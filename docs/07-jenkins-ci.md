# Task 7 — Jenkins Installation and Continuous Integration Job

**Deliverable:** configured Jenkins job, successful build log, trigger evidence and archived
artefact.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 3 October 2026 · **Tag:** `task-07`

---

## 1. Installation

| Property | Value |
|---|---|
| Product | Jenkins LTS |
| Version | **2.568.2** |
| Installation method | `brew install jenkins-lts` |
| JDK | 21 (`/opt/homebrew/opt/openjdk@21`), pinned explicitly |
| Controller URL | <http://localhost:8090/> |
| `JENKINS_HOME` | `~/.jenkins-bloodbank` — project-specific |
| Administrator | `admin`, created by a bootstrap script |

Port **8090**, not the default 8080, because 8080 is already occupied on this host.

`JENKINS_HOME` is project-specific so that an existing Jenkins on the machine is never touched, and
so the whole controller can be deleted and rebuilt in one command.

```bash
bash scripts/jenkins-setup.sh start      # starts and bootstraps on first run
bash scripts/jenkins-setup.sh plugins    # installs the plugin set
bash scripts/jenkins-setup.sh status     # up? which plugins?
```

---

## 2. Configuration as code

**Nothing about this controller was configured by clicking.** `-Djenkins.install.runSetupWizard=false`
skips the first-run wizard and two version-controlled Groovy scripts do the work on every start:

| File | What it configures | Idempotency |
|---|---|---|
| [`jenkins/init.groovy.d/01-security.groovy`](../jenkins/init.groovy.d/01-security.groovy) | Private security realm, the `admin` account, authorisation strategy with anonymous read off | Creates the account only when the realm does not already hold it, so a restart never resets a changed password |
| [`jenkins/init.groovy.d/02-tools-and-url.groovy`](../jenkins/init.groovy.d/02-tools-and-url.groovy) | Jenkins URL and admin address; the `jdk21` and `maven3` tool installations jobs refer to by name | Registers an installation only when one with that name is absent |

`scripts/jenkins-setup.sh` copies these from the repository into `$JENKINS_HOME` on every start, so
**editing them in the repository is what changes the controller** — never the other way round.

### 2.1 Why the wizard is disabled

The wizard requires a human to read an unlock token out of
`$JENKINS_HOME/secrets/initialAdminPassword` and click through five screens. That cannot be
scripted, cannot be reproduced identically on another machine, and cannot be captured as evidence.
For a subject whose argument is that environments should be described as code, the CI server is the
last place to make an exception.

The payoff is directly demonstrable:

```bash
rm -rf ~/.jenkins-bloodbank          # throw the controller away
bash scripts/jenkins-setup.sh start  # it comes back configured
```

### 2.2 Why the JDK is pinned in Jenkins as well as in `scripts/env.sh`

The host's default `java` is 11 and Spring Boot 3 needs 17+. Pinning JDK 21 in **both** places means
the build uses the same JVM whether run by hand or by the pipeline, which removes a whole class of
"works on my machine, fails in CI".

### 2.3 Plugins

[`jenkins/plugins.txt`](../jenkins/plugins.txt) lists **seven** plugins, each with a recorded reason:

| Plugin | Why |
|---|---|
| `git` | Clone the repository in both jobs |
| `workflow-aggregator` | The Pipeline suite — required for the `Jenkinsfile` (Task 8) |
| `junit` | Publish Surefire and Failsafe XML and show the test trend |
| `ws-cleanup` | Wipe the workspace before a build, so "passes because of leftovers" cannot happen |
| `timestamper` | Timestamps in the console log, which makes the captured evidence readable |
| `build-timeout` | Stop a hung build instead of occupying the executor forever |
| `credentials-binding` | Inject registry credentials as environment variables, never into the `Jenkinsfile` |

The list is short on purpose — every plugin is something that can break on upgrade. The pipeline
shells out to `docker` rather than pulling in `docker-workflow`. The Jenkins CLI resolves each
plugin's own dependency tree, bringing the installed total to **40**.

---

## 3. The CI job

A **freestyle** job, defined by version-controlled XML
([`jenkins/ci-freestyle-config.xml`](../jenkins/ci-freestyle-config.xml)) posted to the REST API by
`scripts/jenkins-job.sh`.

| Configuration block | What it does | Why it is there |
|---|---|---|
| **Source** | Git clone, branch `*/main` | No remote yet, so the URL is the local working copy; `REPO_URL` substitutes a GitHub URL after the push with no edit to the committed XML |
| **Trigger** | SCM polling, `H/2 * * * *` | A webhook is right for a hosted repository; a local repository has no server able to call back into Jenkins |
| **Build** | `Invoke top-level Maven targets`: `clean package`, tool `maven3`, JDK `jdk21` | The JDK is pinned by name |
| **Workspace cleanup** | Wipe before build | A build that passes only because of leftovers is not reproducible |
| **Publish JUnit** | `target/surefire-reports/*.xml`, `allowEmptyResults=false` | Jenkins shows counts and a trend. Failing on *empty* results matters: a build whose tests silently did not run must not be green |
| **Archive artefact** | `target/bloodbank.war`, fingerprinted | The task's deliverable, and the input to every later task |
| **Build timeout** | 20 minutes | A hung build should fail, not hold the executor |

### 3.1 Why a freestyle job here and a `Jenkinsfile` in Task 8

Task 7 asks for a Maven build job connected to the repository and triggered by commit, and a
freestyle job shows each of those as a separate, inspectable configuration block. Keeping both lets
the difference from pipeline-as-code be **demonstrated** side by side rather than described —
`ci-freestyle-config.xml` is 130 lines describing every step in Jenkins' own XML, while
`pipeline-config.xml` is 33 lines and only says where the `Jenkinsfile` is.

---

## 4. Build results

| Build | Result | Duration | Notes |
|---|---|---|---|
| **#1** | **FAILURE** | — | The Git plugin refused to clone a local directory. Kept deliberately |
| **#2** | SUCCESS | 13.5 s | 178 tests published, `bloodbank.war` archived and fingerprinted. Triggered manually |
| **#3** | SUCCESS | 12.6 s | Same, but **`Started by an SCM change`** — the trigger evidence |

### 4.1 Build #3 console, abridged — the trigger evidence

```
Started by an SCM change
Running as SYSTEM
Building in workspace /Users/ayushmayekar/.jenkins-bloodbank/workspace/bloodbank-ci
[WS-CLEANUP] Deleting project workspace...
The recommended git tool is: NONE
...
[INFO] Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
Recording test results
Archiving artifacts
Recording fingerprints
Finished: SUCCESS
```

### 4.2 Job status and archived artefact

```
$ bash scripts/jenkins-job.sh status bloodbank-ci
build      #3
result     SUCCESS
duration   12.6s
url        http://localhost:8090/job/bloodbank-ci/3/
artefact   bloodbank.war

$ curl -s -u admin:*** http://localhost:8090/job/bloodbank-ci/2/testReport/api/json | jq '.passCount, .failCount'
178
0
```

### 4.3 Why build #1 is kept

A pipeline that has never failed has never been shown to be **capable** of failing — which is the
entire point of the quality gate added in Task 10.

---

## 5. Problems met and solved

### 5.1 The Git plugin refused to clone the repository (build #1)

```
ERROR: Checkout of Git remote '/Users/ayushmayekar/code/College/devops_project' aborted because it
references a local directory, which may be insecure. You can allow local checkouts anyway by setting
the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.
```

**Cause.** The Git plugin blocks cloning from a local filesystem path by default, because on a
**shared** controller a job defined by an untrusted user could otherwise read any directory the
Jenkins process can see. This repository has no remote yet, so the job's URL *is* a local path.

**Fix.** `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` on the Jenkins JVM, with the
reasoning in a comment in `scripts/jenkins-setup.sh`.

**Why that is acceptable here, and when it would not be.** This is a single-user controller cloning
its own developer's working copy; there is no untrusted job author to defend against. On a shared
controller the right answer is a real remote, never this flag — and it stops being needed the moment
the repository is pushed.

### 5.2 `mapfile: command not found` in the setup script

**Cause.** macOS ships **bash 3.2** (2007), which has no `mapfile` / `readarray`.

**Fix.** Read the plugin list into a plain space-separated word list with `sed`/`awk`/`tr`.

**Lesson.** A script that has to run on the machine the project is demonstrated on is constrained by
*that* machine's shell.

### 5.3 The evidence logs were silently excluded from commits

**Cause.** `.gitignore` excluded `*.log` so a developer's local application log never reaches a
commit. That also swallowed the Jenkins console logs under `evidence/`.

**Fix.** Negation patterns re-include `evidence/**/*.log`, `*.txt` and `*.xml`. The broad rule stays,
because its purpose is still right everywhere else.

---

## 6. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The setup wizard is disabled; the controller is configured by code | The wizard cannot be scripted, reproduced or captured as evidence. For a subject about environments as code, the CI server is the last place to make an exception |
| A freestyle job here, a `Jenkinsfile` in Task 8 — both kept | Lets the difference between UI-configured CI and pipeline-as-code be demonstrated side by side |
| Job configuration is version-controlled XML posted through the REST API | Changing CI means editing a file and re-running a script — reviewable in a diff, restorable on a fresh controller |
| `allowEmptyResults` is false on the JUnit publisher | "0 tests, 0 failures" otherwise looks identical to success |
| Build #1, which failed, is kept as evidence | A pipeline that has never failed has never been shown to be capable of failing |

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-07-jenkins/build-01-console-failed.log`](../evidence/task-07-jenkins/build-01-console-failed.log) | The local-checkout refusal, kept deliberately |
| [`evidence/task-07-jenkins/build-02-console.log`](../evidence/task-07-jenkins/build-02-console.log) | Full console of the green build |
| [`evidence/task-07-jenkins/build-03-console-scm-triggered.log`](../evidence/task-07-jenkins/build-03-console-scm-triggered.log) | First line `Started by an SCM change` — the trigger evidence |
| [`evidence/task-07-jenkins/build-02-status.txt`](../evidence/task-07-jenkins/build-02-status.txt) | Result, duration and the archived artefact |
| [`evidence/task-07-jenkins/test-report-summary.txt`](../evidence/task-07-jenkins/test-report-summary.txt) | 178 passed, 0 failed |
| [`jenkins/SETUP.md`](../jenkins/SETUP.md) | Two-command controller rebuild, plugin reasoning, what changes after the push |

---

## 8. Task 7 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | `mvn clean package` green in CI from a wiped workspace | Build #2, #3 |
| D5 | The Jenkins job is green for the commit | Build #3 SUCCESS |
| D7 | No hard-coded credential in job configuration | Admin password from an environment variable |
| D8 | Task documentation written and committed | This document and `jenkins/SETUP.md` |
| D9 | Evidence captured | `evidence/task-07-jenkins/` |
| D11 | Committed on `main` per the branching model | `task-07` |
| D12 | Backlog updated | US-11 Done |

**Outcome:** Jenkins 2.568.2 installed and fully configured from version-controlled Groovy with no
wizard and no clicking, a Maven job connected to the repository and triggered by SCM polling, a
genuine red build kept as evidence alongside two green ones, 178 tests published and a fingerprinted
WAR archived. The controller can be deleted and rebuilt in two commands.
