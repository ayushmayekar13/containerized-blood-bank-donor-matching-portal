# Task 11 — Docker Image and Container Lifecycle

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 7 October 2026

| Required deliverable | Delivered as |
|---|---|
| `Dockerfile` | [`Dockerfile`](../Dockerfile) — multi-stage, hardened, with traceability labels, plus [`docker/entrypoint.sh`](../docker/entrypoint.sh) and [`.dockerignore`](../.dockerignore) |
| Image details | §2 and §3 — `bloodbank:latest`, 1.05 GB, OCI labels carrying build number, git revision and build date |
| Docker command log | [`evidence/task-11-docker/docker-command-log.txt`](../evidence/task-11-docker/docker-command-log.txt) — a 300-line transcript of all 14 lifecycle stages with real output |
| Running container evidence | §5 — `docker ps`, `docker port`, `docker inspect`, health `healthy`, and PID 1 running as uid 999 |

---

## 1. Why multi-stage

| Stage | Base image | Fate |
|---|---|---|
| 1 — build | `maven:3.9-eclipse-temurin-21` | **Discarded** |
| 2 — runtime | `tomcat:10.1-jdk21-temurin` | Shipped (1.05 GB) |

A single-stage build would ship Maven, the full toolchain, the source tree and every downloaded
dependency. That is a larger image, a larger attack surface, and the project's source code handed to
anyone who can pull it.

**Layer ordering matters as much as the staging.** `pom.xml` is copied and dependencies resolved
*before* `src/` is copied, because the pom changes rarely and the source changes constantly. Docker
reuses the cached dependency layer on every build where the pom has not moved — the difference
between a ~10-second rebuild and a ~5-minute one.

**`-DskipTests` is correct here, and only here.** The pipeline has already run 178 unit tests and 16
Selenium journeys before this image is built. Re-running them inside the image build would double the
pipeline time to prove exactly what was just proved, and would make the image build depend on a
browser being available.

## 2. Hardening, with the reason for each

| Measure | Reason |
|---|---|
| `rm -rf /usr/local/tomcat/webapps/*` | Removes the manager, host-manager, docs and examples applications. The examples webapp in particular has carried its own vulnerabilities and has no business near a deployment |
| Runs as **uid 999**, not root | A container escape from a root process is considerably more useful to an attacker than one from an account that owns only its own data directory |
| JVM is **PID 1** (verified in the log) | `docker stop` delivers `SIGTERM` to Tomcat itself. Measured: **stops in under 1 second**, rather than being `SIGKILL`ed after the 10-second timeout |
| `setpriv`, not `su` | `su` leaves a shell as PID 1 and never forwards signals — which would silently undo the point above |
| `HEALTHCHECK` with a 60s `start-period` | The container reports its own health using the same endpoint Ansible and Jenkins probe, and a slow JVM start is not misreported as a failure |
| OCI labels + `.dockerignore` | Any running image traces back to the commit that built it; the build context excludes `target/`, local databases, `.git/` and secrets |

## 3. Image details

```
$ docker images bloodbank --format "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}"
REPOSITORY   TAG      IMAGE ID       SIZE
bloodbank    latest   e40f29476505   1.05GB
bloodbank    local    e40f29476505   1.05GB      <- same image, two names

$ docker image inspect bloodbank:latest --format '...'
version:  local
revision: 195621b
created:  2026-10-07T05:20:11Z
student:  Ayush Mayekar (23102A0018)
```

**Two tags, two different jobs.** The build-number tag is immutable and is what a rollback pins to;
`latest` moves and is a convenience only. **Deploying `latest` makes rollback impossible**, because
nothing names the version that was running before — which is exactly what Task 14 needs.

## 4. The defect this task produced — BUG-11

Running the hardened image against the existing volume failed:

```
java.nio.file.AccessDeniedException: /opt/bloodbank/data/bloodbank.mv.db
```

**Cause.** A named volume keeps the ownership it was created with. This volume had first been
created by the earlier, root-running Tomcat deployment from Task 8, so its contents were owned by
`root`. Mounting it shadows the image's own directory — and Docker only copies the image
directory's ownership into a **fresh** volume. **Nothing in a Dockerfile can fix a volume that
already exists.**

**Fix.** `docker/entrypoint.sh` starts as root for exactly one purpose — correcting the ownership,
and only when it is actually wrong — then drops privileges with `setpriv` and `exec`s Tomcat. This is
the pattern the official `postgres` and `mysql` images use. It also makes the image self-healing on
any host, instead of depending on whoever created the volume having got it right.

```
entrypoint: data directory is owned by uid 0, expected 999 - correcting
entrypoint: dropping privileges to bloodbank (999:999)
```

## 5. The lifecycle, as recorded

`bash scripts/docker-lifecycle.sh` writes a **transcript** — each command echoed before it runs, with
its real output — rather than a summary written afterwards. All 14 stages are in
[`evidence/task-11-docker/docker-command-log.txt`](../evidence/task-11-docker/docker-command-log.txt).

| # | Stage | What it demonstrates |
|---|---|---|
| 1 | **Build** | Multi-stage build with build-args for traceability |
| 2 | **Tag** | Immutable build-number tag alongside moving `latest` |
| 3 | **Image details** | Size, layer history, OCI labels |
| 4 | **Run** | `-d`, `--name`, port map, named volume, restart policy |
| 5 | **Port mapping** | `docker port` → `8080/tcp -> 0.0.0.0:9090` |
| 6 | **Health** | Application answers; `docker inspect` reports `healthy` |
| 7 | **Logs** | `docker logs --tail 15` |
| 8 | **Inspect** | Image, state, restart policy, ports, mounts |
| 9 | **Process** | `uid 999, pid 1, java` — unprivileged **and** PID 1 |
| 10 | **Stats** | CPU, memory and network of the running container |
| 11 | **Stop** | `stopped in 0s` — a graceful SIGTERM shutdown |
| 12 | **Restart** | Same container started again, healthy |
| 13 | **Replacement** | Container destroyed and recreated — **29 donors before, 29 after** |
| 14 | **Summary** | Image, container, URL, volume, commit |

### Stage 13 is the one that matters

```
donors before destroying the container: 29
$ docker rm -f bloodbank-app
$ docker run -d --name bloodbank-app ... -v bloodbank-data:/opt/bloodbank/data bloodbank:latest
donors after a brand-new container:     29
  ok   data survived container replacement (29 donors) - NFR-04 satisfied
```

The container is **disposable** and the state lives on the volume. That is precisely what makes the
recreate deployment strategy safe, and what makes the Task 14 rollback a re-deploy of a previous
image tag rather than a restore from backup.

## 6. `docker-compose.yml`

Runs the application plus the private registry for local use. It is deliberately **not** what the
pipeline uses — Jenkins builds a versioned image and deploys a specific tag (Task 12), and Ansible
provisions the node (Tasks 13–14). Letting Compose drive deployment as well would create two
definitions of "how this is deployed", which is the drift this subject is about.

## 7. Verification

```bash
# the whole lifecycle, re-recorded
bash scripts/docker-lifecycle.sh all
cat evidence/task-11-docker/docker-command-log.txt

# individual facts
docker ps --filter name=bloodbank-app --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}'
docker inspect bloodbank-app --format '{{.State.Health.Status}}'       # healthy
docker exec bloodbank-app sh -c 'ps -o uid,pid,comm -p 1'              # 999  1  java
docker image inspect bloodbank:latest --format '{{json .Config.Labels}}'

# prove the volume claim yourself
curl -s -u staff:Staff@123 http://localhost:9090/api/donors | jq length
docker rm -f bloodbank-app && docker run -d --name bloodbank-app -p 9090:8080 \
  -v bloodbank-data:/opt/bloodbank/data bloodbank:latest
curl -s -u staff:Staff@123 http://localhost:9090/api/donors | jq length   # same number

# the local stack
docker compose up -d && docker compose ps
```
