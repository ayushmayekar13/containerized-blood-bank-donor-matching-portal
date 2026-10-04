# Task 8 — Pipeline as Code and Server Deployment

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 4 October 2026

| Required deliverable | Delivered as |
|---|---|
| `Jenkinsfile` | [`Jenkinsfile`](../Jenkinsfile) in the repository root — declarative, 7 stages, 4 parameters |
| Successful pipeline run | [`evidence/task-08-pipeline/build-03-console.log`](../evidence/task-08-pipeline/build-03-console.log) — SUCCESS in **34 s**, all stages green |
| Deployed application URL / screenshot | http://localhost:9090 — Tomcat 10.1 container, `/actuator/health` returning `UP`; stage view in [`stage-view.txt`](../evidence/task-08-pipeline/stage-view.txt) |
| Configuration evidence | [`parameter-override-proof.txt`](../evidence/task-08-pipeline/parameter-override-proof.txt) — a run with `DEPLOY_PORT=9095 SPRING_PROFILE=local`, with `docker ps` and `docker inspect` showing both took effect |

---

## 1. What "pipeline as code" actually changes

Task 7's freestyle job works, and its configuration lives in Jenkins' own database. That has three
consequences that only become visible when something goes wrong:

| | Freestyle job (Task 7) | `Jenkinsfile` (Task 8) |
|---|---|---|
| Where the definition lives | Jenkins' `config.xml`, inside `JENKINS_HOME` | The repository, beside the code it builds |
| Reviewed | Not at all, unless someone exports the XML | In the same pull request as the change |
| History of *why* a stage exists | None | `git log` on the `Jenkinsfile` |
| Restoring it on a fresh controller | Re-create it, or restore a backup | It is already in the clone |
| Changing it per branch | Impossible without copying the job | A branch's own `Jenkinsfile` governs its own build |

The practical demonstration of the last point: `jenkins/pipeline-config.xml` is **33 lines** and
only tells Jenkins where to find the `Jenkinsfile`, while `jenkins/ci-freestyle-config.xml` is
**130 lines** describing every step, publisher and wrapper in Jenkins' own XML dialect. Both jobs
are kept so the comparison can be shown rather than described.

## 2. Why declarative and not scripted

- The **stage view** renders each stage separately. That is what will make the Task 10 quality gate
  legible at a glance — a red *UI Test* stage with the stages after it visibly never started — rather
  than a fact to be dug out of a console log.
- **`post` blocks run whether the stage passed or failed.** `junit` is therefore published in an
  `always` block: a red build is exactly when the test report is needed, and a scripted pipeline
  would need explicit `try/finally` to achieve the same.
- **`parameters` are declared in the file**, so the parameterised environment this task requires is
  version-controlled with everything else rather than being UI state.

## 3. The stages

```
Checkout → Build → Unit Test → Package → Deploy to Tomcat → Health Check
```

| Stage | What it does | Why it is a separate stage |
|---|---|---|
| **Checkout** | `checkout scm`, then print the commit and the `java`/`mvn` versions | A build log that does not say which commit and which JDK it used is not evidence of anything |
| **Build** | `mvn clean compile` | A compile failure should be reported *as* a compile failure, not buried inside a test run |
| **Unit Test** | `mvn test`, publishing Surefire XML in an `always` block | Fast feedback, and the report survives a red build |
| **Package** | `mvn package -DskipTests`, archive `bloodbank.war` fingerprinted | `-DskipTests` because the tests already ran; re-running them would double the build time to prove nothing |
| **Deploy to Tomcat** | `scripts/deploy-tomcat.sh` with the parameters passed as environment variables | The deployment target the task sheet names |
| **Health Check** | `scripts/health-check.sh` | Deployment is not success; *behaving correctly* is |

### The same script, by hand and in CI

The deploy and health-check stages call `scripts/deploy-tomcat.sh` and `scripts/health-check.sh` —
the same scripts a developer runs by hand. There is therefore exactly one deployment procedure and
one definition of "healthy", and no possibility of "but CI does it differently". The parameters are
passed as environment variables because `scripts/env.sh` already reads every one of them from the
environment with a default, so no second code path was needed to make the pipeline parameterisable.

### Why the health check is behavioural

A Tomcat that returns `200` on `/actuator/health` while the matching engine is broken has failed in
the only way that matters. `scripts/health-check.sh` therefore asserts nine things through the API,
including the three that encode the project's real rules:

```
ok   no incompatible donor in a B+ shortlist => 0      (FR-14, the compatibility matrix)
ok   STAFF fulfilling a request is refused => 403      (NFR-07, server-side authorisation)
ok   ADMIN fulfilling a NEW request is refused => 409  (FR-18, the state machine)
```

## 4. Deployment to Tomcat

`scripts/deploy-tomcat.sh` runs the official `tomcat:10.1-jdk21-temurin` image with the freshly
built WAR mounted into `webapps/` as **`ROOT.war`**, so the context path is `/` in every
environment — local, container, CI — and no URL in the Selenium suite, the health check or the
documentation changes between them.

Three deliberate choices:

- **Recreate, not restart.** The old container is removed and a new one created. An immutable
  replacement is what makes the rollback in Task 14 a re-deploy rather than a rebuild.
- **Named volume `bloodbank-data`.** The H2 database outlives the container, which is what makes
  recreate safe and is demonstrated directly in Task 12.
- **`--restart unless-stopped`.** The reliability requirement NFR-03.

Task 11 replaces the volume mount with a proper image that bakes the WAR in. This script stays,
because it is the fastest way to put a freshly built WAR in front of a browser without an image
rebuild — and the difference between the two is itself worth demonstrating.

## 5. The parameterised environment (the task's explicit requirement)

Four parameters, each a value that genuinely differs between environments, each with a default that
makes a plain "Build Now" behave exactly like the committed configuration:

| Parameter | Default | Why it is a parameter |
|---|---|---|
| `DEPLOY_PORT` | `9090` | 8080 is occupied on this host. Changing it deploys a second instance alongside the first without touching it |
| `TOMCAT_IMAGE` | `tomcat:10.1-jdk21-temurin` | Pinned to a minor version, not `:latest`, so a rebuild in six months produces the same runtime |
| `SPRING_PROFILE` | *(empty)* | `local` enables the H2 console and debug logging; empty is the production-like default |
| `RUN_HEALTH_CHECK` | `true` | Lets a deployment be inspected manually in a broken state without the pipeline tearing it down |

A parameter with no sensible default is a trap rather than a feature, which is why all four have one.

### Verified: the parameters really do change the deployment

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

Both the published port and the active Spring profile changed. Build #5 restored the defaults.

## 6. Pipeline duration, and the one real problem found

| Build | Result | Total | Package stage | Note |
|---|---|---|---|---|
| #1 | SUCCESS | 10 m 25 s | — | First run, cold dependency cache |
| #2 | SUCCESS | 10 m 25 s | **597.6 s** | Still slow — this is the problem |
| **#3** | SUCCESS | **34.0 s** | **4.1 s** | After the fix |
| #4 | SUCCESS | 32 s | 4 s | Parameterised run on port 9095 |
| #5 | SUCCESS | 33 s | 4 s | Defaults restored |

**Cause.** `MAVEN_OPTS` pointed `maven.repo.local` at `.m2/repository` *inside the workspace*. A
cache that lives in the workspace is wiped with the workspace, so it is not a cache — it is a
guarantee of a cold download on every single build.

**Fix.** Removed, so builds use the shared `~/.m2`. That is also the correct answer for a second
reason: it is the repository a developer running the same command by hand uses, which is what makes
"the pipeline builds what I build" true rather than approximately true.

34 seconds against the 6-minute budget in NFR-13 — and the margin is what makes the deliberately
red build in Task 10 cheap to demonstrate live.

## 7. Verification

```bash
bash scripts/jenkins-job.sh create bloodbank-pipeline jenkins/pipeline-config.xml
bash scripts/jenkins-job.sh build  bloodbank-pipeline --wait     # streams all 7 stages
open http://localhost:8090/job/bloodbank-pipeline/               # the visual stage view
open http://localhost:9090/login                                 # the deployed application

# by hand, without Jenkins - the same two scripts the pipeline calls
source scripts/env.sh
mvn clean package && bash scripts/deploy-tomcat.sh && bash scripts/health-check.sh

# the parameters
bash scripts/jenkins-setup.sh cli build bloodbank-pipeline -p DEPLOY_PORT=9095 -s
docker ps -f name=bloodbank-app --format '{{.Ports}}'
```
