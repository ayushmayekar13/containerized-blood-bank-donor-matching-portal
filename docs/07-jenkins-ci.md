# Task 7 — Jenkins Installation and Continuous Integration Job

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 3 October 2026

| Required deliverable | Delivered as |
|---|---|
| Configured Jenkins job | `bloodbank-ci`, defined by version-controlled [`jenkins/ci-freestyle-config.xml`](../jenkins/ci-freestyle-config.xml) and created through the REST API by `scripts/jenkins-job.sh` |
| Successful build log | [`evidence/task-07-jenkins/build-02-console.log`](../evidence/task-07-jenkins/build-02-console.log) — SUCCESS in 13.5 s, 178 tests |
| Trigger evidence | [`build-03-console-scm-triggered.log`](../evidence/task-07-jenkins/build-03-console-scm-triggered.log) — first line is `Started by an SCM change` |
| Archived artefact | `bloodbank.war`, fingerprinted, listed in [`build-02-status.txt`](../evidence/task-07-jenkins/build-02-status.txt) |

Full setup reference: [`jenkins/SETUP.md`](../jenkins/SETUP.md).

---

## 1. Installation

Jenkins LTS 2.568.2 was installed with `brew install jenkins-lts` and is started by
`scripts/jenkins-setup.sh` on **port 8090** — not the default 8080, which is already occupied on
this host.

The significant decision is that **the controller is described as code, not clicked together.**
`-Djenkins.install.runSetupWizard=false` skips the first-run wizard, and two version-controlled
Groovy scripts configure everything on each start:

- `jenkins/init.groovy.d/01-security.groovy` — private security realm, the `admin` account,
  authorisation strategy with anonymous read off.
- `jenkins/init.groovy.d/02-tools-and-url.groovy` — the Jenkins URL, and the `jdk21` and `maven3`
  tool installations that the jobs refer to by name.

Both are idempotent, so restarting never resets a password or duplicates a tool. `JENKINS_HOME` is
`~/.jenkins-bloodbank`, project-specific, so nothing on the machine's existing Jenkins is touched
and the whole controller can be deleted and rebuilt in one command.

**Why not the wizard?** It needs a human to paste an unlock token and click through five screens.
That cannot be scripted, reproduced identically elsewhere, or captured as evidence. For a subject
whose argument is that environments should be described as code, the CI server is the last place to
make an exception. The practical payoff is directly demonstrable:

```bash
rm -rf ~/.jenkins-bloodbank          # throw the controller away
bash scripts/jenkins-setup.sh start  # it comes back configured
```

## 2. Plugins

Seven plugins, each with a recorded reason (`jenkins/plugins.txt`): `git`,
`workflow-aggregator`, `junit`, `ws-cleanup`, `timestamper`, `build-timeout`,
`credentials-binding`. The Jenkins CLI resolves each plugin's own dependency tree from the update
centre, bringing the installed total to 40.

The list is short on purpose — every plugin is something that can break on upgrade. The pipeline in
Task 8 shells out to `docker` rather than pulling in `docker-workflow`, and uses plain `sh` steps
wherever a shell command is as clear as a wrapper.

## 3. The CI job

A **freestyle** job, because Task 7 asks for a Maven build job connected to the repository and
triggered by commit, and a freestyle job shows each of those as a separate, inspectable
configuration block. The pipeline-as-code version — same build, with deployment stages — is the
`Jenkinsfile` in Task 8. Keeping both lets the difference between UI-configured CI and
pipeline-as-code be demonstrated side by side instead of described.

| Configuration block | What it does | Why it is there |
|---|---|---|
| **Source** | Git clone of the repository, branch `*/main` | No remote yet, so the URL is the local working copy; `REPO_URL` substitutes a GitHub URL after the push with no edit to the committed XML |
| **Trigger** | SCM polling, `H/2 * * * *` | A webhook is right for a hosted repository and is what this becomes after the push; a local repository has no server able to call back into Jenkins |
| **Build** | `Invoke top-level Maven targets`: `clean package`, tool `maven3`, JDK `jdk21` | The JDK is pinned by name, because the host default is Java 11 and Spring Boot 3 needs 17+ |
| **Workspace cleanup** | Wipe before build | A build that only passes because of leftovers from the previous build is not a reproducible build |
| **Publish JUnit** | `target/surefire-reports/*.xml`, `allowEmptyResults=false` | Jenkins shows pass/fail counts and a trend across builds instead of requiring the console log to be read. Failing on *empty* results matters: a build where the tests silently did not run must not be green |
| **Archive artefact** | `target/bloodbank.war`, fingerprinted, only if successful | The task's explicit deliverable, and the input to every later task: Task 8 deploys it, Task 11 copies it into the image, Task 12 publishes that image |
| **Build timeout** | 20 minutes | A hung build should fail, not hold the executor indefinitely |

## 4. Builds, including the one that failed

| Build | Result | What it shows |
|---|---|---|
| **#1** | **FAILURE** | `Checkout of Git remote ... aborted because it references a local directory, which may be insecure.` The log is kept deliberately |
| **#2** | SUCCESS, 13.5 s | 178 tests published, `bloodbank.war` archived. Triggered manually |
| **#3** | SUCCESS, 12.6 s | `Started by an SCM change` — the commit trigger, working |

### Why build #1 is kept

The Git plugin blocks cloning from a local filesystem path, because on a **shared** controller a job
defined by an untrusted user could otherwise read any directory the Jenkins process can see. This
repository has no remote yet, so the job's URL *is* a local path.

The fix is `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true`, with the reasoning in a comment
in `scripts/jenkins-setup.sh`. It is acceptable here — a single-user controller cloning its own
developer's working copy, with no untrusted job author to defend against — and it stops being needed
the moment the repository is pushed. On a shared controller the correct answer is a real remote,
never this flag. Recorded as T-07 in [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md).

Keeping a red build in the evidence is deliberate: a pipeline that has never failed has never been
shown to be capable of failing, which is the entire point of the quality gate in Task 10.

## 5. Verified output

```
$ bash scripts/jenkins-job.sh status bloodbank-ci
build      #3
result     SUCCESS
duration   12.6s
url        http://localhost:8090/job/bloodbank-ci/3/
artefact   bloodbank.war

$ head -1 evidence/task-07-jenkins/build-03-console-scm-triggered.log
Started by an SCM change

$ curl -s -u admin:bloodbank-admin \
    http://localhost:8090/job/bloodbank-ci/2/testReport/api/json | jq '.passCount, .failCount'
178
0
```

## 6. Verification

```bash
bash scripts/jenkins-setup.sh status                       # up, and the plugin list
bash scripts/jenkins-job.sh build bloodbank-ci --wait      # streams a full build
bash scripts/jenkins-job.sh status bloodbank-ci            # result, duration, artefact
open http://localhost:8090/job/bloodbank-ci/                # test trend graph, build history

# the reproducibility claim, verified rather than asserted
rm -rf ~/.jenkins-bloodbank
bash scripts/jenkins-setup.sh start && bash scripts/jenkins-setup.sh plugins
bash scripts/jenkins-job.sh create bloodbank-ci jenkins/ci-freestyle-config.xml
```
