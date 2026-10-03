# Jenkins Setup — reproducing this controller from nothing

Project: Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018

Everything below is scripted. The whole controller can be deleted and rebuilt in two commands,
which is how the reproducibility claim is verified rather than asserted:

```bash
rm -rf ~/.jenkins-bloodbank                       # delete the controller entirely
bash scripts/jenkins-setup.sh start               # rebuild it, bootstrapped from code
bash scripts/jenkins-setup.sh plugins             # install the plugin set
bash scripts/jenkins-job.sh create bloodbank-ci       jenkins/ci-freestyle-config.xml
bash scripts/jenkins-job.sh create bloodbank-pipeline jenkins/pipeline-config.xml
```

| | |
|---|---|
| **URL** | http://localhost:8090 |
| **Administrator** | `admin` / `bloodbank-admin` (overridable with `JENKINS_ADMIN_PASSWORD`) |
| **Version** | Jenkins LTS 2.568.2 (`brew install jenkins-lts`) |
| **`JENKINS_HOME`** | `~/.jenkins-bloodbank` — project-specific, so an existing Jenkins on the machine is never touched |
| **JDK** | JDK 21 at `/opt/homebrew/opt/openjdk@21`, registered as tool `jdk21` |
| **Maven** | `/opt/homebrew/opt/maven/libexec`, registered as tool `maven3` |

Port **8090**, not the default 8080, because 8080 is already occupied on this host.

---

## 1. Why the setup wizard is disabled

Jenkins' first-run wizard requires a human to read an unlock token out of
`$JENKINS_HOME/secrets/initialAdminPassword` and click through five screens. That cannot be
scripted, cannot be reproduced identically on another machine, and cannot be captured as evidence.
For a subject whose whole argument is that environments should be described as code, the CI server
is the last place to make an exception.

Instead, `-Djenkins.install.runSetupWizard=false` skips the wizard and two version-controlled
Groovy scripts in `jenkins/init.groovy.d/` do the configuration on every start:

| Script | What it configures | Idempotency |
|---|---|---|
| `01-security.groovy` | A private security realm, the `admin` account, and `FullControlOnceLoggedInAuthorizationStrategy` with anonymous read **off** | Only creates the account when the realm does not already hold it, so a restart never resets a password changed by hand |
| `02-tools-and-url.groovy` | Jenkins URL and admin address; the `jdk21` and `maven3` tool installations the jobs refer to by name | Registers an installation only when one with that name is absent |

`scripts/jenkins-setup.sh` copies these from the repository into `$JENKINS_HOME` on every start, so
editing them in the repository is what changes the controller — never the other way round.

### Why the JDK is pinned in Jenkins as well as in `scripts/env.sh`

The host's default `java` is 11 and Spring Boot 3 needs 17+. Pinning JDK 21 in *both* places means
the build uses the same JVM whether it is run by hand or by the pipeline, which removes a whole
class of "works on my machine, fails in CI".

## 2. Plugins

`jenkins/plugins.txt` lists **seven** plugins, each with a reason:

| Plugin | Why |
|---|---|
| `git` | Clone the repository in both the freestyle job and the pipeline |
| `workflow-aggregator` | The Pipeline suite — required for the declarative `Jenkinsfile` (Task 8) |
| `junit` | Publish Surefire and Failsafe XML and show the test trend over builds (Tasks 7, 10) |
| `ws-cleanup` | Wipe the workspace before a build, so "passes because of leftovers" cannot happen |
| `timestamper` | Timestamps in the console log, which makes the captured evidence readable |
| `build-timeout` | Stop a hung build instead of occupying the executor forever |
| `credentials-binding` | Inject registry credentials as environment variables, never into the `Jenkinsfile` (Task 12) |

The list is deliberately short: every plugin is something that can break on upgrade. The pipeline
shells out to `docker` rather than pulling in `docker-workflow`, and uses plain `sh` steps wherever a
shell command is as clear as a wrapper. The Jenkins CLI resolves each plugin's own dependency tree
from the update centre, which brings the installed total to **40** including transitive dependencies.

```bash
bash scripts/jenkins-setup.sh status      # lists every installed plugin and version
```

## 3. The two jobs, and why both exist

| Job | Defined by | Purpose |
|---|---|---|
| `bloodbank-ci` | `jenkins/ci-freestyle-config.xml` | **Task 7** — a freestyle Maven job: clone, `clean package`, publish unit tests, archive the WAR |
| `bloodbank-pipeline` | `jenkins/pipeline-config.xml` + `Jenkinsfile` | **Tasks 8, 10, 12** — pipeline as code: build, test, quality gate, image, registry, deploy |

The freestyle job is not redundant. Task 7 asks for a Maven build job connected to the repository
and triggered by commit, and a freestyle job shows each of those as a separate, inspectable
configuration block. Keeping both lets the difference between UI-configured CI and
pipeline-as-code be *demonstrated* side by side instead of described — which is itself a likely viva
question.

Both job configurations are version-controlled XML, posted to the Jenkins REST API by
`scripts/jenkins-job.sh`. Changing CI therefore means editing a file and re-running a script:
reviewable in a diff, and restorable on a fresh controller.

## 4. Build trigger

The freestyle job polls the repository on `H/2 * * * *` (every two minutes, hashed to spread load).

A webhook is the right answer for a hosted repository, and is what this becomes after the first
push — the local repository has no server able to call back into Jenkins, so polling is what works
today. The task sheet asks for "triggered by commit **or** scheduled polling", and polling is
genuinely triggered by a commit: Jenkins compares the remote's tip against the last built revision
and starts a build only when it has moved.

**Evidence that it works:** build #3 in `evidence/task-07-jenkins/` begins with
`Started by an SCM change` — it was not triggered by hand.

## 5. Verified build results

| Build | Result | Notes |
|---|---|---|
| **#1** | FAILURE | The Git plugin refused to clone a local directory. Log kept deliberately — see `docs/TROUBLESHOOTING.md` T-07 |
| **#2** | SUCCESS, 13.5 s | 178 tests published, `bloodbank.war` archived and fingerprinted. Triggered manually |
| **#3** | SUCCESS, 12.6 s | Same, but **`Started by an SCM change`** — the trigger evidence |

```
$ bash scripts/jenkins-job.sh status bloodbank-ci
build      #3
result     SUCCESS
duration   12.6s
url        http://localhost:8090/job/bloodbank-ci/3/
artefact   bloodbank.war

$ curl -s -u admin:bloodbank-admin \
    http://localhost:8090/job/bloodbank-ci/3/testReport/api/json | jq '.passCount, .failCount'
178
0
```

## 6. The one flag that needs explaining

```
-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true
```

The Git plugin blocks cloning from a local filesystem path, because on a **shared** controller a job
defined by an untrusted user could otherwise read any directory the Jenkins process can see. This
repository has no remote yet, so the job's URL *is* a local path.

This is a single-user controller cloning its own developer's working copy, so there is no untrusted
job author to defend against. On a shared controller the correct answer is a real remote, never this
flag — and the flag stops being needed the moment the repository is pushed and `REPO_URL` becomes a
GitHub URL. The reasoning is recorded in a comment in `scripts/jenkins-setup.sh` so it is not
silently inherited later.

## 7. Everyday commands

```bash
bash scripts/jenkins-setup.sh start | stop | status | plugins
bash scripts/jenkins-setup.sh cli <any jenkins-cli command>

bash scripts/jenkins-job.sh create <job> <config.xml>
bash scripts/jenkins-job.sh build  <job> --wait        # streams the console log
bash scripts/jenkins-job.sh log    <job> [build]
bash scripts/jenkins-job.sh status <job>

# force a poll instead of waiting up to two minutes
bash scripts/jenkins-setup.sh cli build bloodbank-ci
```

## 8. After the repository is pushed to GitHub

1. Set `REPO_URL` to the HTTPS clone URL and re-run
   `bash scripts/jenkins-job.sh create bloodbank-ci jenkins/ci-freestyle-config.xml`.
2. Remove `ALLOW_LOCAL_CHECKOUT` from `scripts/jenkins-setup.sh`.
3. Replace polling with a webhook: add
   `http://<jenkins-host>:8090/github-webhook/` in the repository's settings and install the
   `github` plugin. Polling remains as the fallback, which is the usual production arrangement.
