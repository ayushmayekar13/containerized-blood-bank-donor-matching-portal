# Task 8 — Pipeline as Code and Server Deployment

**Deliverable:** Jenkinsfile, successful pipeline run, deployed application URL/screenshot and
configuration evidence.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 4 October 2026 · **Tag:** `task-08`

---

## 1. What pipeline-as-code actually changes

Task 7's freestyle job works, and its configuration lives in Jenkins' own database. That has three
consequences that only become visible when something goes wrong.

| | Freestyle job (Task 7) | `Jenkinsfile` (Task 8) |
|---|---|---|
| Where the definition lives | Jenkins' `config.xml`, inside `JENKINS_HOME` | The repository, beside the code it builds |
| Reviewed | Not at all, unless someone exports the XML | In the same pull request as the change |
| History of *why* a stage exists | None | `git log` on the `Jenkinsfile` |
| Restoring it on a fresh controller | Re-create it, or restore a backup | It is already in the clone |
| Changing it per branch | Impossible without copying the job | A branch's own `Jenkinsfile` governs its own build |

The practical demonstration of the last point: [`jenkins/pipeline-config.xml`](../jenkins/pipeline-config.xml)
is **33 lines** and only tells Jenkins where to find the `Jenkinsfile`, while
[`jenkins/ci-freestyle-config.xml`](../jenkins/ci-freestyle-config.xml) is **130 lines** describing
every step, publisher and wrapper in Jenkins' own XML dialect.

### 1.1 Why declarative and not scripted

- The **stage view** renders each stage separately — what makes the Task 10 quality gate legible at
  a glance rather than buried in a log.
- **`post` blocks run whether the stage passed or failed**, so `junit` is published in an `always`
  block: a red build is exactly when the test report is needed.
- **`parameters` are declared in the file**, so the parameterised environment this task requires is
  version-controlled rather than being UI state.

---

## 2. The stages

```
Checkout → Build → Unit Test → Package → Deploy to Tomcat → Health Check
```

| Stage | What it does | Why it is a separate stage |
|---|---|---|
| **Checkout** | `checkout scm`, then print the commit and the `java`/`mvn` versions | A build log that does not say which commit and which JDK it used is not evidence of anything |
| **Build** | `mvn clean compile` | A compile failure should be reported *as* a compile failure, not buried inside a test run |
| **Unit Test** | `mvn test`, publishing Surefire XML in an `always` block | Fast feedback, and the report survives a red build |
| **Package** | `mvn package -DskipTests`, archive `bloodbank.war` fingerprinted | `-DskipTests` because the tests already ran; re-running them would double the build time to prove nothing |
| **Deploy to Tomcat** | `scripts/deploy-tomcat.sh` with the parameters as environment variables | The deployment target the task sheet names |
| **Health Check** | `scripts/health-check.sh` | Deployment is not success; *behaving correctly* is |

### 2.1 The same script, by hand and in CI

The deploy and health-check stages call `scripts/deploy-tomcat.sh` and `scripts/health-check.sh` —
the same scripts a developer runs by hand. There is therefore exactly one deployment procedure and
one definition of "healthy", and no possibility of "but CI does it differently".

The parameters are passed as environment variables because `scripts/env.sh` already reads every one
of them from the environment with a default, so **no second code path was needed** to make the
pipeline parameterisable.

### 2.2 Why the health check is behavioural

A Tomcat that returns `200` on `/actuator/health` while the matching engine is broken has failed in
the only way that matters. `scripts/health-check.sh` asserts nine things through the API, including
the three that encode the project's real rules:

```
ok   no incompatible donor in a B+ shortlist => 0      (FR-14, the compatibility matrix)
ok   STAFF fulfilling a request is refused => 403      (NFR-07, server-side authorisation)
ok   ADMIN fulfilling a NEW request is refused => 409  (FR-18, the state machine)
```

---

## 3. Deployment to Tomcat

`scripts/deploy-tomcat.sh` runs the official `tomcat:10.1-jdk21-temurin` image with the freshly
built WAR mounted into `webapps/` as **`ROOT.war`**, so the context path is `/` in every environment
and no URL in the Selenium suite, the health check or the documentation changes between them.

| Choice | Reason |
|---|---|
| **Recreate, not restart** | The old container is removed and a new one created. An immutable replacement is what makes the Task 14 rollback a re-deploy rather than a rebuild |
| **Named volume `bloodbank-data`** | The H2 database outlives the container, which is what makes recreate safe |
| **`--restart unless-stopped`** | The reliability requirement NFR-03 |

Task 11 replaces the volume mount with a proper image that bakes the WAR in. This script stays,
because it is the fastest way to put a freshly built WAR in front of a browser without an image
rebuild.

---

## 4. The parameterised setting, and why it is verified rather than assumed

Four parameters, each a value that genuinely differs between environments, each with a default that
makes a plain "Build Now" behave exactly like the committed configuration.

| Parameter | Default | Why it is a parameter |
|---|---|---|
| `DEPLOY_PORT` | `9090` | 8080 is occupied on this host. Changing it deploys a second instance alongside the first |
| `TOMCAT_IMAGE` | `tomcat:10.1-jdk21-temurin` | Pinned to a minor version, not `:latest`, so a rebuild in six months produces the same runtime |
| `SPRING_PROFILE` | *(empty)* | `local` enables the H2 console and debug logging |
| `RUN_HEALTH_CHECK` | `true` | Lets a deployment be inspected by hand in a broken state without the pipeline tearing it down |

A parameter with no sensible default is a trap rather than a feature, which is why all four have one.

### 4.1 Verified: the parameters really do change the deployment

```
$ bash scripts/jenkins-setup.sh cli build bloodbank-pipeline \
       -p DEPLOY_PORT=9095 -p SPRING_PROFILE=local -s
Started bloodbank-pipeline #4
Completed bloodbank-pipeline #4 : SUCCESS

$ docker ps --format '{{.Names}} {{.Image}} {{.Ports}}' -f name=bloodbank-app
bloodbank-app tomcat:10.1-jdk21-temurin 0.0.0.0:9095->8080/tcp

$ curl -s http://localhost:9095/actuator/health
{"status":"UP","groups":["liveness","readiness"]}

$ docker inspect bloodbank-app --format '{{range .Config.Env}}{{println .}}{{end}}' | grep SPRING
SPRING_PROFILES_ACTIVE=local
```

Both the published port and the active Spring profile changed. Build #5 restored the defaults. The
point is that the parameter was **observed reaching the running container**, not assumed from the
build log.

---

## 5. Pipeline runs

| Build | Result | Total | Package stage | Note |
|---|---|---|---|---|
| #1 | SUCCESS | 10 m 25 s | — | First run, cold dependency cache |
| #2 | SUCCESS | 10 m 25 s | **597.6 s** | Still slow — this is the problem |
| **#3** | SUCCESS | **34.0 s** | **4.1 s** | After the fix |
| #4 | SUCCESS | 32 s | 4 s | Parameterised run on port 9095 |
| #5 | SUCCESS | 33 s | 4 s | Defaults restored |

### 5.1 Build #3 stage view

```
pipeline build #3  status=SUCCESS  total=34.0s

STAGE                        STATUS         TIME
Checkout                     SUCCESS        1.1s
Build                        SUCCESS        3.3s
Unit Test                    SUCCESS       10.6s
Package                      SUCCESS        4.1s
Deploy to Tomcat             SUCCESS        8.4s
Health Check                 SUCCESS        1.4s
```

34 seconds against the 6-minute budget in NFR-13 — and the margin is what makes the deliberately red
build in Task 10 cheap to demonstrate live.

---

## 6. Problems met and solved

### 6.1 A 625-second pipeline

**Symptom.** Build #2 spent **597 of its 625 seconds** in the Package stage, downloading every
dependency from scratch.

**Cause.** `MAVEN_OPTS` pointed `maven.repo.local` at `.m2/repository` **inside the workspace**. A
cache that lives in the workspace is wiped with the workspace, so it is not a cache — it is a
guarantee of a cold download on every single build.

**Fix.** Removed, so builds use the shared `~/.m2`. That is also correct for a second reason: it is
the repository a developer running the same command by hand uses, which is what makes "the pipeline
builds what I build" true rather than approximately true.

**Result.** The Package stage went from 597 s to 4 s, and the pipeline from 625 s to 34 s.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Declarative rather than scripted pipeline | The stage view renders stages separately; `post` blocks publish reports on red builds; parameters in the file are version-controlled rather than UI state |
| The deploy and health-check stages call the same scripts a developer runs | Exactly one deployment procedure and one definition of "healthy" |
| The health check asserts behaviour, not a 200 response | A Tomcat answering while the matching engine is broken has failed in the only way that matters |
| Deployment recreates the container rather than restarting it | An immutable replacement is what makes the Task 14 rollback a re-deploy and not a rebuild |
| `-DskipTests` in the Package stage | The tests already ran in the previous stage; re-running them would double the build time to prove nothing |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`Jenkinsfile`](../Jenkinsfile) | Seven stages, four parameters, `post` blocks |
| [`evidence/task-08-pipeline/build-03-console.log`](../evidence/task-08-pipeline/build-03-console.log) | Full console of the 34-second green run |
| [`evidence/task-08-pipeline/stage-view.txt`](../evidence/task-08-pipeline/stage-view.txt) | Per-stage status and timings |
| [`evidence/task-08-pipeline/build-04-console-parameterised.log`](../evidence/task-08-pipeline/build-04-console-parameterised.log) | The run with `DEPLOY_PORT=9095` |
| [`evidence/task-08-pipeline/parameter-override-proof.txt`](../evidence/task-08-pipeline/parameter-override-proof.txt) | `docker ps` and `docker inspect` showing both parameters reached the container |

---

## 9. Task 8 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | `mvn clean verify` green in the pipeline | Unit Test stage, 178 tests |
| D5 | The Jenkins pipeline is green for the commit | Build #3 SUCCESS |
| D7 | No hard-coded host, port, path or credential | Four build parameters with defaults |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-08-pipeline/` |
| D10 | The deployed container answers its health check | Health Check stage, nine assertions |
| D11 | Committed on `main` per the branching model | `task-08` |
| D12 | Backlog updated | US-12 Done |

**Outcome:** the build moved out of Jenkins' database and into the repository as a declarative
seven-stage `Jenkinsfile` with four parameters, deploying to Tomcat and verifying behaviour rather
than liveness. Both job types are kept so the difference can be demonstrated: 33 lines of job XML
against 130. The pipeline runs in 34 seconds after a workspace-local Maven cache was removed, and a
parameterised run was observed reaching the container on port 9095 with the `local` profile active.
