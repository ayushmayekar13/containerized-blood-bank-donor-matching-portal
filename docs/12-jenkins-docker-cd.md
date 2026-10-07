# Task 12 — Jenkins-Docker Continuous Deployment

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 7 October 2026

| Required deliverable | Delivered as |
|---|---|
| Versioned image | `localhost:5001/bloodbank:15` — tagged with the Jenkins build number; labels carry the git revision `c2b890a` |
| Registry evidence | [`evidence/task-12-registry/registry-evidence.txt`](../evidence/task-12-registry/registry-evidence.txt) — catalogue, tag list, running container, image labels |
| End-to-end commit-to-container pipeline | [`stage-view-15-full-cd.txt`](../evidence/task-12-registry/stage-view-15-full-cd.txt) — 11 stages, **101 seconds**, all green |
| Release tag | `v1.1.0` |

---

## 1. The complete pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI Test ┃ → Promote → Post-deploy
                                                                 ┗━ GATE ━┛      ↓
                                               build versioned image → push → pull back → fresh container
```

```
pipeline build #15  status=SUCCESS  total=101.3s

Checkout                             SUCCESS        1.1s
Build                                SUCCESS        3.2s
Unit Test                            SUCCESS       11.0s     178 tests
Package                              SUCCESS        4.4s
Deploy to Tomcat                     SUCCESS        7.9s
Health Check                         SUCCESS        1.4s
UI Test (quality gate)               SUCCESS       43.7s     16 journeys
Promote: image, registry, deploy     SUCCESS       24.9s
Post-deploy verification             SUCCESS        1.4s
```

**101 seconds from commit to a running, health-checked container**, against the 6-minute budget in
NFR-13 — and every promotion stage sits *after* the quality gate, so none of it is reachable while a
journey is red.

## 2. The one decision in this task worth defending

**The deployment pulls the image back out of the registry before running it.**

```bash
docker push   localhost:5001/bloodbank:15
docker rmi    localhost:5001/bloodbank:15     # drop the local copy
docker pull   localhost:5001/bloodbank:15     # fetch it back
docker run -d ... localhost:5001/bloodbank:15
```

Deleting the local image and pulling it back costs about a second and looks redundant — the image was
just built on this machine. It is the difference between a release that is *verified* and one that is
*assumed*:

- If the push silently failed, or the registry wrote a corrupt layer, or the tag was wrong, deploying
  the **local** image would succeed anyway and nobody would notice.
- The failure would then surface at the **first attempted rollback** — when the previous tag is
  pulled and found to be missing. That is the worst possible moment to discover it.

Pulling before deploying means that if the pipeline is green, the registry provably contains a usable
image. The Task 14 rollback depends entirely on that being true.

## 3. Versioning

| Tag | Mutable? | Purpose |
|---|---|---|
| `bloodbank:<BUILD_NUMBER>` | **No** | The real identity of a release. Monotonic, unique, and what a rollback pins to |
| `bloodbank:latest` | Yes | Convenience for "give me the newest". Never deployed by anything automated |

**Deploying `latest` makes rollback impossible**, because nothing names the version that was running
before. The container is additionally labelled at run time:

```
$ docker inspect bloodbank-app --format '{{.Config.Image}}'
localhost:5001/bloodbank:15
$ docker inspect bloodbank-app --format '{{index .Config.Labels "org.opencontainers.image.version"}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
15 c2b890a
```

Any running container answers two questions without reference to anything else: **which build am I,
and which commit am I?**

## 4. Registry

```
$ curl -s http://localhost:5001/v2/_catalog
{"repositories":["bloodbank"]}

$ curl -s http://localhost:5001/v2/bloodbank/tags/list
{"name":"bloodbank","tags":["latest","1","15"]}

$ docker ps --filter name=bloodbank --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
NAMES                IMAGE                         STATUS
bloodbank-app        localhost:5001/bloodbank:15   Up 21 seconds (healthy)
bloodbank-registry   registry:2                    Up 6 minutes
```

**Why a private registry rather than Docker Hub.** No credentials to manage, no rate limits, and no
internet dependency during the practical examination — the demonstration works on an isolated
machine. Port **5001** because macOS reserves 5000 for AirPlay Receiver. Images persist on the
`bloodbank-registry-data` volume, so previous versions survive a registry restart, which is what
makes rollback possible after a reboot.

### Using Docker Hub instead

Two changes, and nothing else:

```groovy
// 1. the image prefix
REGISTRY_HOST = 'docker.io/ayushmayekar'

// 2. credentials, injected by the plugin - never written into the Jenkinsfile
withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                  usernameVariable: 'REG_USER',
                                  passwordVariable: 'REG_PASS')]) {
    sh 'echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin'
    sh 'bash scripts/release.sh "${BUILD_NUMBER}"'
}
```

The `credentials-binding` plugin is already installed for exactly this. The token is masked in the
console log, and `--password-stdin` keeps it out of the process list — `docker login -p "$TOKEN"` would
expose it to anyone able to run `ps` on the agent.

## 5. Why `scripts/release.sh` and not shell inside the `Jenkinsfile`

The whole release is one script that the pipeline calls with the build number, and that a developer
can run by hand with any version string. One definition of what "release" means — the same reason the
deploy and health-check stages call the same scripts a developer uses. A release procedure that
exists only inside a CI server is a procedure nobody can test, and nobody can run when the CI server
is the thing that is broken.

## 6. Data survives the upgrade

The container is replaced on every release and the named volume is re-attached, so donors and
requests persist across versions — demonstrated directly in Task 11 stage 13 (29 donors before and
after a full container replacement). The container is disposable; the data is not.

## 7. Verification

```bash
# the whole thing
bash scripts/jenkins-job.sh build bloodbank-pipeline --wait

# by hand, without Jenkins
bash scripts/release.sh 99

# what the registry holds
curl -s http://localhost:5001/v2/_catalog | jq
curl -s http://localhost:5001/v2/bloodbank/tags/list | jq

# what is actually running, and which commit it came from
docker ps --filter name=bloodbank-app --format '{{.Names}} {{.Image}} {{.Status}}'
docker inspect bloodbank-app --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
curl -s http://localhost:9090/actuator/health

# prove the registry is really the source: delete every local copy and redeploy
docker rmi -f localhost:5001/bloodbank:15 bloodbank:15
docker run -d --name test-pull -p 9099:8080 localhost:5001/bloodbank:15
```
