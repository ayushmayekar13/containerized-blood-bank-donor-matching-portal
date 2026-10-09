# Practical Day Guide

**Containerized Blood Bank Donor Matching Portal**
Ayush Mayekar · Roll No. 23102A0018 · BE Computer Engineering, Division A · DevOps

> Read §1 and §2 the night before. Run §3 thirty minutes before. Everything else is
> reference you can look up while demonstrating.

---

## 1. The two-minute pitch

> "Blood cannot be manufactured, a donor is ineligible for ninety days after donating, and red-cell
> compatibility is much wider than 'same group only' — a B+ patient can receive from B+, B−, O+ or
> O−. Yet most blood banks still match requests to donors by phone call and paper register, so
> time-to-match is measured in hours and compatible donors in the same city go uncontacted.
>
> This portal makes the match a query instead of a phone tree. It holds a searchable donor registry,
> records blood requests, and ranks the donors who can *actually* be called — applying the
> compatibility matrix, the ninety-day rule and the availability flag — under a role-based workflow
> where only an administrator may confirm a donation.
>
> The DevOps side is the real subject. Every commit runs 179 unit tests and 16 Selenium journeys in
> Jenkins; the Selenium suite is a **blocking quality gate**, so a broken user journey physically
> cannot reach deployment. A passing build produces a versioned Docker image, publishes it to a
> registry, and deploys a fresh container from it — commit to running container in about a hundred
> seconds. Ansible describes the target node, is provably idempotent, and can roll back to any
> previous version in under a minute without touching the data.
>
> I can show all of that in one command, and I can show it failing on purpose too."

**If you say nothing else, say these three things:**
1. The quality gate **stopped a real deployment** — build #9, where 179 unit tests passed and a
   browser caught the regression.
2. Ansible reports **`changed=0`** on a second run — that is idempotency, demonstrated not claimed.
3. Rollback is a **re-deploy of an immutable tag**, which is only possible because `latest` is never
   deployed.

---

## 2. What makes this project defensible

Every one of these is a question an examiner is likely to ask, with a real answer.

| They ask | You say |
|---|---|
| "Is this just a CRUD app?" | The matching engine is real logic: 64 compatibility pairs, an eligibility boundary, and a four-rule ranking. 47 tests cover the matrix alone. |
| "Do your tests actually catch anything?" | Eleven defects were found and fixed, each recorded with its cause in `docs/TROUBLESHOOTING.md`. Four were found by Selenium *after* the unit suite was green. |
| "Did you just write a pipeline that always passes?" | Build #8, #9 and #10 are red, deliberately kept. #9 is the one that matters: everything passed except the browser. |
| "Is the Jenkins setup reproducible?" | `rm -rf ~/.jenkins-bloodbank && bash scripts/jenkins-setup.sh start` rebuilds the controller from version-controlled Groovy. No wizard, no clicking. |
| "Is Ansible really doing anything?" | It provisions from nothing after the container *and* the local image are destroyed, and the second run reports `changed=0`. |
| "Could you roll back in production?" | Demonstrated: 17 → 15 in under a minute, health-checked, 39 donors intact. |

---

## 3. Thirty minutes before — the startup checklist

Run these in order. Each one either prints `ok` or tells you what is wrong.

```bash
cd ~/code/College/devops_project
source scripts/env.sh
```

| # | Command | Expect |
|---|---|---|
| 1 | *Start Docker Desktop and wait for the whale icon to settle* | `docker ps` responds within a second |
| 2 | `bash scripts/preflight.sh` | `PREFLIGHT PASSED` |
| 3 | `bash scripts/jenkins-setup.sh start` | `Jenkins is up at http://localhost:8090/` |
| 4 | `bash scripts/jenkins-setup.sh status` | lists ~41 plugins |
| 5 | `bash scripts/demo-all.sh` | `COMPLETE DEVOPS WORKFLOW VERIFIED` (takes ~4 min) |

Then open these tabs and **leave them open**:

| Tab | URL |
|---|---|
| Portal | http://localhost:9090/login |
| Jenkins job | http://localhost:8090/job/bloodbank-pipeline/ |
| The red build | http://localhost:8090/job/bloodbank-pipeline/9/ |
| Registry | http://localhost:5001/v2/bloodbank/tags/list |
| Health | http://localhost:9090/actuator/health |

**If step 5 fails, stop and read §8.** Do not start demonstrating with a broken environment.

---

## 4. The demonstration, in the order the tasks are numbered

Total: about 18 minutes if uninterrupted. Each step says **what to run**, **what to point at**, and
**what to say**.

### Tasks 1–3 · Problem, plan, architecture — 2 minutes

**Show:** `docs/01-problem-definition.md` §2 (the problem statement) and
`docs/ARCHITECTURE.md` §5 (the matching algorithm).

> "The problem statement is specific: no searchable registry, no automatic enforcement of
> compatibility or the ninety-day rule, no tracked request lifecycle, no role-based accountability.
> The architecture puts every one of those rules in the service layer or on a domain enum, which is
> why 64 compatibility pairs can be proved in milliseconds without a browser."

### Task 4 · Repository — 1 minute

```bash
git log --oneline | head -20
git tag -l | sort -V
```

> "One tagged commit per task, so you can check out the exact state of any task. `task-07` is Jenkins,
> `task-11` is Docker. The release tags are `v1.0.0` through `v1.3.0`."

### Tasks 5–6 · Branching, pull requests, merge conflict — 3 minutes

```bash
git log --graph --oneline --decorate -16
```

**Point at the two diamonds.**

> "Both features came in through pull requests merged with `--no-ff`, so the feature boundary is
> permanent in history and the whole feature can be reverted as one commit. The records are in
> `docs/pr/` — descriptions, design decisions, and review comments; two of them caused code changes
> before the merge."

```bash
cat evidence/task-06-conflict/README.md | head -40
```

> "The conflict was real: `develop` added a Health link to the navigation while my feature branch
> added a role-conditional profile link, both as the last item of the same list. I resolved it on the
> feature branch, not on `develop` — if the resolution is wrong, I throw away the feature branch and
> the integration branch is untouched. And I ran the full suite on the resolved tree before
> committing, because a resolution exists in neither parent, so neither side's tests ever ran
> against it."

### Tasks 7–8 · Jenkins and pipeline as code — 3 minutes

**Show the job page**, then:

> "The controller is described as code — two Groovy scripts create the admin user and register the
> JDK and Maven tools. The setup wizard is disabled because it needs a human to paste a token and
> click through five screens, which cannot be scripted or reproduced. I can delete `JENKINS_HOME`
> entirely and `scripts/jenkins-setup.sh start` brings it back configured."

**Show the two job types side by side:**

> "`jenkins/ci-freestyle-config.xml` is 130 lines describing every build step in Jenkins' own XML.
> `jenkins/pipeline-config.xml` is 33 lines and only says where the `Jenkinsfile` is. That difference
> *is* pipeline-as-code: the pipeline is reviewed in the same pull request as the code it builds."

**Point at a parameterised build** (`DEPLOY_PORT`, `SPRING_PROFILE`), and
`evidence/task-08-pipeline/parameter-override-proof.txt`.

### Tasks 9–10 · Selenium and the quality gate — 4 minutes ⭐ **the centrepiece**

**Open build #9's stage view.**

> "Look at the order. Checkout, Build, Unit Test — 179 green. Package. Deploy. Health Check — all
> passed. Then the UI Test stage goes red, and Promote was never entered.
>
> The defect was removing one `sec:authorize` attribute from the fulfil form, so the page offered a
> STAFF user an action only an ADMIN may take. The service layer still refused it with 403, so no
> data was ever at risk — and **every unit test still passed**. Only a browser could see it.
>
> The gate is the *stage order*, not a check inside a stage. Everything that promotes a build sits
> after the UI Test stage, so a failing journey cannot physically reach it. No `catchError`, no
> `|| true`, no `unstable`, and no `retry` — a journey that passes on the second attempt is telling
> you the suite is flaky, and hiding that is how a suite stops being believed."

**Show the archived screenshot** in `evidence/task-10-quality-gate/gate-failure-screenshots/`.

> "Failures capture a screenshot, the page source and the URL. In Jenkins the browser is headless and
> nobody is watching, so this is the only record of what the page looked like."

**Then show the recovery:** build #11, all green, Promote reached.

**If asked to run them live:**
```bash
bash scripts/run-ui-tests.sh --headed     # you can watch the browser
```

### Tasks 11–12 · Docker and continuous deployment — 3 minutes

```bash
docker ps --filter name=bloodbank --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
docker exec bloodbank-app sh -c 'ps -o uid,pid,comm -p 1'
curl -s http://localhost:5001/v2/bloodbank/tags/list
```

> "Multi-stage: Maven builds the WAR in a stage that is discarded, so the runtime image ships no
> toolchain and no source. Tomcat runs as uid 999 **and** as PID 1 — unprivileged, and `docker stop`
> reaches the JVM directly, so it stops in under a second instead of being killed after ten.
>
> The pipeline tags the image with the build number, pushes it, then **deletes the local copy and
> pulls it back** before deploying. That costs a second and it is the difference between a release
> that is verified and one that is assumed: if the push had silently failed, deploying the local
> image would hide it until the first rollback — the worst possible moment to find out."

**The data-survives demonstration** (`docs/11-docker-lifecycle.md` §5, stage 13):

> "The container is destroyed and recreated from the image, and the donor count is unchanged. The
> container is disposable; the data lives on a named volume. That is what makes recreate-deployment
> and rollback safe."

### Tasks 13–14 · Ansible, idempotency, rollback — 3 minutes

```bash
bash scripts/verify-reliability.sh
```

> "Four things in one command. It destroys the container *and* the local image, then provisions from
> nothing — the registry is what makes that possible. It runs again and reports **`changed=0`**: that
> is idempotency, which is what makes a playbook safe to run when you are not sure whether it already
> ran. It health-checks *behaviour* — the compatibility matrix, the role guard — not just a ping.
> Then it rolls back to the previous image tag and confirms the donor count is unchanged."

> "Rollback uses the **same role** as deployment with the tag overridden. A recovery path that differs
> from the normal path is one that has never really been tested, because it only runs during
> incidents."

### Task 15 · Everything — 2 minutes

```bash
bash scripts/demo-all.sh
```

> "Commit, build, unit tests, quality gate, image, registry, container, provisioned node, rollback.
> Every step asserts — it fails loudly if any part of the claim is untrue."

---

## 5. Likely viva questions, with answers

### On the application

**Why is eligibility computed and not stored?**
A stored flag would need a nightly job and would be wrong the moment the clock passed midnight. It is
derived from `lastDonationDate` on every read.

**Why does an exact blood-group match rank above a merely compatible one?**
Clinical stewardship. O− is the universal donor and the scarcest stock; spending an O− donor on an
O+ patient when an O+ donor is available wastes the rarest resource.

**Why does urgency not affect matching?**
Relaxing compatibility or eligibility under time pressure is exactly the shortcut the portal exists
to prevent. A CRITICAL request draws the same shortlist as a LOW one; urgency affects display order
only.

**What is the difference between 403 and 409 here?**
403 means *you* may not do this — authorisation. 409 means *this* cannot be done from the current
state — the state machine. Staff attempting `NEW → FULFILLED` violates both; the role is checked
first, because telling someone a transition is impossible when they were never allowed to attempt it
is misleading.

**Why two security filter chains?**
`/api/**` and `/actuator/**` use HTTP Basic and are stateless, so an unauthenticated call returns
**401** — something a script can act on. Everything else uses form login with a session and CSRF
intact, so a browser is **redirected** to the login page. One chain with Basic enabled made every
screen answer 401 with a browser pop-up.

**Is it safe to disable CSRF on the API chain?**
Yes, and only there. CSRF protection defends against a third-party page causing a request that rides
on the victim's **ambient session**. The API chain is stateless and authenticates per request, so
there is no ambient session to ride on. The browser chain keeps CSRF enabled.

### On CI/CD

**CI vs CD vs CD?**
*Continuous Integration* — every commit is built and tested automatically. *Continuous Delivery* —
every passing build is **releasable**. *Continuous Deployment* — every passing build **is** released,
with no human gate. This project does continuous deployment: a green pipeline publishes an image and
replaces the running container.

**What exactly is your quality gate?**
Stage order. Everything that promotes a build is placed after the Selenium stage, so a failing
journey cannot reach it. No `catchError`, no `unstable`, no `retry`.

**Why not `retry` on flaky UI tests?**
A journey that passes on the second attempt is evidence the suite is flaky. Retrying hides that, and
a gate nobody believes is not a gate. I fixed three separate flakes in one journey instead — each in
its own commit.

**Freestyle job vs pipeline?**
The freestyle job's configuration lives in Jenkins' database: not reviewed, no history of *why* a
step exists, and lost if the controller is lost. A `Jenkinsfile` lives in the repository, is reviewed
in the same pull request as the code, and comes back with the clone.

**Why does the image build skip tests?**
They already ran — 179 unit tests and 16 journeys, before the image is built. Re-running them inside
the build would double the pipeline time to prove what was just proved, and would make the image
build depend on a browser.

**What is `allowEmptyResults: false` for?**
So a build whose tests silently did not run cannot be green. "0 tests, 0 failures" otherwise looks
identical to success.

### On Docker

**Image vs container?**
An image is an immutable, layered filesystem plus metadata. A container is a running instance with a
writable layer on top. One image, many containers.

**Why multi-stage?**
The build stage carries Maven, the JDK toolchain, the source and every dependency. Shipping that
means a larger image, a larger attack surface, and handing anyone who can pull the image the source
code.

**Why copy `pom.xml` before `src/`?**
Layer caching. The pom changes rarely and the source constantly, so dependency resolution is reused
on every build where the pom has not moved — a ~10-second rebuild instead of ~5 minutes.

**Why does PID 1 matter?**
`docker stop` sends `SIGTERM` to PID 1. With `setpriv`, Tomcat *is* PID 1 and shuts down in under a
second. Using `su` would leave a shell as PID 1 that never forwards signals, so every stop would hit
the ten-second timeout and `SIGKILL` the JVM.

**Why does the container not run as root?**
A container escape from a root process is considerably more useful to an attacker than one from an
account that owns only its own data directory.

**Why an entrypoint that starts as root?**
A named volume keeps the ownership it was created with, and Docker only copies the image directory's
ownership into a **fresh** volume — so nothing in a Dockerfile can fix an existing one. The
entrypoint corrects it when wrong, then drops privileges. The official `postgres` and `mysql` images
do the same.

**Volume vs bind mount?**
A volume is managed by Docker and portable; a bind mount maps a host path. The database uses a volume
so it is not tied to a host layout — and bind-mounting the WAR out of `target/` caused a real defect,
because Docker held the file open and `mvn clean` failed.

**Why not deploy `latest`?**
Because then nothing names the version that was running before, and rollback becomes impossible.

### On Ansible

**What is idempotency, and why does it matter?**
Applying the same description twice produces no further change. It is what makes a playbook safe to
run on a schedule, safe to run when nobody is sure whether it already ran, and safe to run during an
incident. A playbook that must only be run once is a script with extra syntax.

**How did you achieve it?**
`changed_when: false` on every task that only checks; `comparisons: '*': strict` so the Docker module
compares *declared* state against *actual* state rather than against Docker's defaults; and
`force_source: false` so an immutable tag already present is not re-pulled.

**Ansible vs Puppet?**
Ansible is agentless and push-based; Puppet needs an agent and is pull-based. For a single node and
no infrastructure to host a Puppet master, agentless wins — and open-source Puppet on Apple Silicon
would have had to run inside a container, adding a moving part between the thing managing the
deployment and the thing being deployed.

**Your inventory is `localhost`. Is that real configuration management?**
The connection is the only local thing. Nothing in `site.yml` or in any role refers to `localhost` —
every task uses the inventory hostname and the group variables, so pointing it at
`prod-01 ansible_host=10.0.0.21 ansible_user=deploy` is a one-line change.

**Why does `prereqs` check rather than install?**
Installing Docker on macOS means driving a GUI installer, which cannot be done honestly. More
generally, a run that silently installs a container runtime on a machine that did not have one is a
surprise, not provisioning. On a Linux fleet those `assert` tasks become `package:` tasks and nothing
else changes.

**How does rollback work, and why is it possible?**
It re-deploys an earlier immutable tag. Three things make it possible: images are tagged with the
build number and never overwritten; the registry retains them on a volume; and all state is on a
separate volume, so replacing the container does not touch data.

### On testing

**Why are the Selenium tests in a separate Maven profile?**
`mvn clean verify` must be green on a clean checkout with nothing running — that is what the Jenkins
*Unit Test* stage depends on. The journeys need a deployed application and a browser; making them
part of the default build would mean the default build fails for reasons unrelated to the commit, and
people stop trusting it.

**Why `data-testid` and not CSS selectors?**
So restyling a page or rewording a label cannot break a test. A suite that raises false alarms gets
ignored.

**Why no `Thread.sleep`?**
A fixed sleep is too short on a loaded machine — a flaky failure — or too long on a fast one. Every
wait is on an explicit condition.

**You still had flaky tests. What did you learn?**
One journey failed three times for three different reasons, and the third is the lesson: I waited for
the results table to be visible, but the *unfiltered* page already had that table, so the wait was
satisfied instantly against the old page. **Waiting for a condition that is already true is not
waiting at all.** After an action that replaces the page, wait for something about the old page to
become false — staleness — first.

### On the process

**Which defect are you proudest of finding?**
BUG-05. The worklist showed MEDIUM above HIGH above CRITICAL, and every unit test was green, because
`urgency` is `@Enumerated(STRING)` so `order by urgency desc` sorted the **text**. The tests asserted
*which* requests came back and never in what order — and since the ordering lived in SQL, the H2 test
agreed with production, both wrong in the same way.

**What would you do differently?**
Split the application work across three tasks instead of two — Tasks 5 and 6 carried 27 of 64 story
points — and add a static-analysis stage. And I would write the Selenium suite earlier: four real
defects were sitting behind a green unit suite.

**What is not production-ready?**
H2 is single-node; donor personal data is unencrypted at rest with no access audit log; only the
ninety-day interval is modelled as eligibility; "same city" is a string comparison, not a distance.
All recorded in `docs/SRS.md` §5 and tracked in `docs/backlog.md`.

---

## 6. Numbers worth knowing

| | |
|---|---|
| Tests | 179 unit + 16 Selenium journeys |
| Compatibility pairs asserted | 64 (27 compatible) |
| Pipeline, commit to deployed container | ~101 s |
| Jenkins stages | 11 |
| Defects found and fixed | 11, each with its cause recorded |
| Subject tasks | 15, each tagged |
| Ports | app 9090 · Jenkins 8090 · registry 5001 |

---

## 7. If they ask you to break something live

**Best choice — remove the role guard from the UI** (this is the Task 10 defect):

```bash
# in src/main/resources/templates/requests/detail.html, delete this line from the fulfil form:
#     sec:authorize="hasRole('ADMIN')"
git commit -am "demo: remove the role guard"
bash scripts/jenkins-job.sh build bloodbank-pipeline --wait
```
Unit tests pass, deployment happens, the UI Test stage goes red, Promote is skipped. ~2 minutes.

**Fastest choice — break a unit test:** change `DONATION_INTERVAL_DAYS` from 90 to 60 in `Donor.java`.
The Unit Test stage fails in ~40 seconds and nothing is deployed.

**Afterwards:** `git revert HEAD` (or `git checkout -- .`) and rerun.

---

## 8. If something goes wrong

| Symptom | Fix |
|---|---|
| `docker ps` hangs | Docker Desktop is wedged. Restart it from the menu bar, wait for the whale to settle, then `bash scripts/preflight.sh`. **This happened during development — do not panic, it is a known state.** |
| Port 9090 already in use | `docker rm -f bloodbank-app` then `bash scripts/release.sh 1` |
| Jenkins not responding | `bash scripts/jenkins-setup.sh stop && bash scripts/jenkins-setup.sh start` |
| Jenkins job missing | `bash scripts/jenkins-job.sh create bloodbank-pipeline jenkins/pipeline-config.xml` |
| Registry empty | `bash scripts/release.sh 1 && bash scripts/release.sh 2` — you need two versions for rollback |
| Application unhealthy | `docker logs --tail 50 bloodbank-app` |
| Selenium fails everywhere | Chrome updated. Selenium Manager fixes itself on the next run; just rerun |
| Data looks wrong | `docker rm -f bloodbank-app && docker volume rm bloodbank-data && bash scripts/release.sh 1` — reseeds in ~15 s |
| **Everything is broken** | `bash scripts/demo-all.sh` rebuilds the whole stack. If even that fails, fall back to §9 |

## 9. Plan B — demonstrate from the evidence

Every claim in this project has a captured artefact, committed to the repository. If the live
environment cannot be recovered, open these instead — they are real output, not screenshots of
something that might have happened.

| Claim | File |
|---|---|
| Jenkins CI job, with a red build and a commit-triggered one | `evidence/task-07-jenkins/` |
| Pipeline run + parameter override proof | `evidence/task-08-pipeline/` |
| Selenium results and a failure capture | `evidence/task-09-selenium/` |
| **The quality gate stopping a deployment** | `evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt` |
| Full container lifecycle transcript | `evidence/task-11-docker/docker-command-log.txt` |
| Registry, versioned images, deployed container | `evidence/task-12-registry/` |
| Ansible first run | `evidence/task-13-ansible/first-run.log` |
| **Idempotency, clean provisioning, rollback** | `evidence/task-14-reliability/` |
| The merge conflict, with markers preserved | `evidence/task-06-conflict/` |

> "Everything I am claiming is captured in the repository as real output. Here is the stage view
> where the quality gate stopped the deployment, and here is the Ansible recap showing `changed=0`."

---

## 10. Closing line

> "The thing I would point at is build #9. One hundred and seventy-nine unit tests passed, the
> package built, the container deployed, the health check passed — and the pipeline still went red,
> because a browser found an action offered to a user who was not allowed to take it. Nothing was
> promoted. That is the difference between having tests and having a gate."
