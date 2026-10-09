# Task 12 — Jenkins-Docker Continuous Deployment

**Deliverable:** versioned image, registry evidence and end-to-end commit-to-container pipeline.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 7 October 2026 · **Tags:** `task-12`, `v1.1.0`

---

## 1. The complete pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI TEST ┃ → Promote → Post-deploy
                                                                 ┗━ GATE ━┛      ↓
                                               build versioned image → push → pull back → fresh container
```

```
pipeline build #15  status=SUCCESS  total=101.3s

Checkout                             SUCCESS        1.1s
Build                                SUCCESS        3.2s
Unit Test                            SUCCESS       11.0s     179 tests
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

---

## 2. The decision worth defending

**The deployment pulls the image back out of the registry before running it.**

```bash
docker push   localhost:5001/bloodbank:15
docker rmi    localhost:5001/bloodbank:15     # drop the local copy
docker pull   localhost:5001/bloodbank:15     # fetch it back
docker run -d ... localhost:5001/bloodbank:15
```

Deleting the local image and pulling it back costs about a second and looks redundant — the image
was just built on this machine. It is the difference between a release that is **verified** and one
that is **assumed**:

- If the push silently failed, or the registry wrote a corrupt layer, or the tag was wrong,
  deploying the **local** image would succeed anyway and nobody would notice.
- The failure would then surface at the **first attempted rollback** — when the previous tag is
  pulled and found to be missing. That is the worst possible moment to discover it.

Pulling before deploying means that if the pipeline is green, the registry **provably** contains a
usable image. The Task 14 rollback depends entirely on that being true.

---

## 3. Versioned images

| Tag | Mutable? | Purpose |
|---|---|---|
| `bloodbank:<BUILD_NUMBER>` | **No** | The real identity of a release. Monotonic, unique, and what a rollback pins to |
| `bloodbank:latest` | Yes | Convenience for "give me the newest". Never deployed by anything automated |

**Deploying `latest` makes rollback impossible**, because nothing names the version that was running
before. The container is additionally labelled at run time:

```
$ docker inspect bloodbank-app --format '{{.Config.Image}}'
localhost:5001/bloodbank:15

$ docker inspect bloodbank-app \
    --format '{{index .Config.Labels "org.opencontainers.image.version"}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
15 c2b890a
```

Any running container answers two questions without reference to anything else: **which build am I,
and which commit am I?**

---

## 4. The registry

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

### 4.1 Using Docker Hub instead

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
console log, and `--password-stdin` keeps it out of the process list — `docker login -p "$TOKEN"`
would expose it to anyone able to run `ps` on the agent.

---

## 5. Why `scripts/release.sh` and not shell inside the `Jenkinsfile`

The whole release is one script that the pipeline calls with the build number, and that a developer
can run by hand with any version string:

```bash
bash scripts/release.sh 99
```

One definition of what "release" means — the same reason the deploy and health-check stages call the
same scripts a developer uses. **A release procedure that exists only inside a CI server is a
procedure nobody can test, and nobody can run when the CI server is the thing that is broken.**

---

## 6. Data survives the upgrade

The container is replaced on every release and the named volume is re-attached, so donors and
requests persist across versions — demonstrated directly in Task 11 stage 13 (29 donors before and
after a full container replacement). The container is disposable; the data is not.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The deployment pulls the image back from the registry before running it | A silently failed push would otherwise surface at the first attempted rollback — the worst possible moment. Pulling first means a green pipeline proves the registry holds a usable image |
| The image is tagged with the Jenkins build number | Monotonic, unique and immutable. That tag is what the Task 14 rollback pins to |
| `latest` is never deployed by anything automated | Deploying it makes rollback impossible, because nothing names the version that was running before |
| The release is one script, called by the pipeline and runnable by hand | A release procedure that exists only inside a CI server is one nobody can test, and nobody can run when the CI server is broken |
| A private registry rather than Docker Hub | No credentials, no rate limits, no internet dependency during the practical examination |
| A second health check after the release | The first verified the build; this one verifies the thing actually serving traffic |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-12-registry/stage-view-15-full-cd.txt`](../evidence/task-12-registry/stage-view-15-full-cd.txt) | All 11 stages green in 101 seconds |
| [`evidence/task-12-registry/build-15-console-full-cd.log`](../evidence/task-12-registry/build-15-console-full-cd.log) | Full console of the end-to-end run |
| [`evidence/task-12-registry/registry-evidence.txt`](../evidence/task-12-registry/registry-evidence.txt) | Catalogue, tag list, running container and image labels |
| [`scripts/release.sh`](../scripts/release.sh) | Build, publish, pull back, deploy |

---

## 9. Task 12 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | `mvn clean verify` green in the pipeline | Unit Test stage, 179 tests |
| D5 | The Jenkins pipeline is green for the commit | Build #15 SUCCESS |
| D6 | The Selenium quality gate passed before promotion | UI Test stage green, 16 journeys |
| D7 | No credential in the `Jenkinsfile` | Local registry; Docker Hub path uses `credentials-binding` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-12-registry/` |
| D10 | The deployed container answers its health check | Post-deploy verification stage |
| D11 | Committed on `main` and tagged | `task-12`, `v1.1.0` |
| D12 | Backlog updated | US-14 Done |

**Outcome:** the full commit-to-container path in 101 seconds — 179 unit tests, 16 Selenium journeys
as a blocking gate, a versioned image published to a private registry, pulled back to prove the push
worked, and deployed as a fresh container that answers its health check. Immutable build-number tags
are what make the Task 14 rollback possible.
