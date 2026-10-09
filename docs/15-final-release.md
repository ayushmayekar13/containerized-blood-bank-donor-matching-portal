# Task 15 — Final End-to-End Release, Documentation and Viva

**Deliverable:** final repository, live demonstration, complete report, screenshots/video,
presentation and viva.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 9 October 2026 · **Tags:** `task-15`, `v1.3.0`

---

## 1. The end-to-end run

`bash scripts/demo-all.sh` runs the complete workflow — Git commit through Jenkins build, Selenium
quality gate, Docker deployment and Ansible provisioning — in nine steps.

| Step | What it does |
|---|---|
| 1 | **Preflight** — toolchain, Docker daemon, ports, base images |
| 2 | **Git** — 15 task tags, release tags, the branch graph with both pull-request diamonds |
| 3 | **Build and unit tests** — Maven → `bloodbank.war`, 179 tests |
| 4 | **Jenkins** — the full 11-stage pipeline including the Selenium gate |
| 5 | **Registry** — versioned images retained |
| 6 | **Running container** — image provenance; unprivileged PID 1 |
| 7 | **Health check** — nine behavioural assertions |
| 8 | **Ansible** — clean provisioning, `changed=0`, health, rollback, roll-forward |
| 9 | **Result** — pass or fail, with the elapsed time |

### 1.1 Verified run

```
STEP 1 Preflight            ok (toolchain, daemon, ports, images)
STEP 2 Git                  ok every subject task is tagged => 15
STEP 3 Build and unit tests ok artefact: bloodbank.war (54M)
STEP 4 Jenkins              ok pipeline result => SUCCESS
STEP 5 Registry             ok 6 image versions retained
STEP 6 Running container        999  1  java
STEP 7 Health check         HEALTH CHECK PASSED
STEP 8 Ansible              ok IDEMPOTENT / ok ROLLED BACK, data intact
STEP 9 Result               elapsed 151s

████  COMPLETE DEVOPS WORKFLOW VERIFIED  ████
```

### 1.2 Every step asserts

A demonstration script that only narrates what it did proves nothing. Each step compares an expected
value against an observed one and fails loudly otherwise. That property was useful immediately: the
**first run failed** because `task-15` was not yet tagged —

```
FAIL every subject task is tagged => expected '15', got '14'
```

— which is exactly what the assertion exists for.

### 1.3 Why it is one command

A workflow that takes fifteen remembered commands to demonstrate is a workflow that will be
demonstrated badly. Under examination conditions, a forgotten step reads as a capability that does
not exist.

```bash
bash scripts/demo-all.sh            # everything, about 2.5 minutes
bash scripts/demo-all.sh --quick    # skip the Jenkins run, about 90 seconds
```

---

## 2. Documentation delivered

| Document | For |
|---|---|
| [`README.md`](../README.md) | Anyone opening the repository: what it is, how to run it, where everything lives |
| [`REPORT.md`](REPORT.md) | The written submission — abstract, requirements, architecture, pipeline, results, limitations |
| **[`VIVA_GUIDE.md`](VIVA_GUIDE.md)** | **Practical day** — pitch, T-30 checklist, demonstration script, Q&A, recovery, plan B |
| [`PRESENTATION.md`](PRESENTATION.md) | Ten slides with speaker notes and timings |
| [`RUNBOOK.md`](RUNBOOK.md) | Operating it: routine commands, incident procedures, maintenance |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | Twelve problems with their **diagnosis**, not just their remedy |
| `01`–`15` | One document per subject task |
| [`SRS.md`](SRS.md) · [`ARCHITECTURE.md`](ARCHITECTURE.md) · [`API.md`](API.md) | Specification and design |
| [`backlog.md`](backlog.md) · [`kanban.md`](kanban.md) · [`definition-of-done.md`](definition-of-done.md) | Agile artefacts |
| [`pr/`](pr) | Pull-request descriptions and review records |

### 2.1 Troubleshooting guide

Twelve entries, each recording **how the cause was worked out**, not only the remedy — because the
examiner's likely question is "how did you find that?". Two entries generalise beyond this project:

> **T-11 — Waiting for a condition that is already true is not waiting at all.** One Selenium journey
> failed three times for three different reasons; the third was a wait satisfied instantly by the
> *old* page, because the unfiltered list had the same results table as the filtered one.

> **T-12 — A click that returns without error has not necessarily done anything.** Where the result
> of a click is a navigation, assert the navigation rather than trusting the click.

---

## 3. Release history

| Tag | Marks |
|---|---|
| `task-01` … `task-15` | The exact state at which each subject task was completed |
| `v1.0.0` | Functional MVP — registry, matching engine, role-based workflow, dashboard |
| `v1.1.0` | Containerized continuous deployment through a registry |
| `v1.2.0` | Automated provisioning with proven idempotency and tested rollback |
| **`v1.3.0`** | **Final documented release** |

```bash
git checkout task-07      # exactly the state at which Jenkins CI was completed
git checkout v1.0.0       # the functional MVP
git checkout main         # the final release
```

---

## 4. Results against the frozen success criteria

All ten criteria fixed in Task 1 were met.

| ID | Criterion | Target | Achieved |
|---|---|---|---|
| S1 | Time to produce a ranked shortlist | < 2 s for 500 donors | Met |
| S2 | Compatibility correctness | 64/64 pairs | **64/64** |
| S3 | Eligibility correctness | 0 ineligible shortlisted | **0** |
| S4 | Workflow safety | 0 illegal transitions accepted | **0** |
| S5 | Build reproducibility | Green from a clean clone | Met |
| S6 | Commit to deployed container | < 6 min | **101 s** |
| S7 | Quality gate effectiveness | A failing test stops deployment | **Demonstrated** |
| S8 | Provisioning idempotency | Second run `changed=0` | **`changed=0`** |
| S9 | Recovery | Rollback healthy in < 2 min | **< 1 min** |
| S10 | Manual deployment steps | 0 | **0** |

---

## 5. What the project demonstrates

Pipeline build **#9**: Checkout, Build, Unit Test (179 green), Package, Deploy and the behavioural
Health Check all passed — and the build still went red, because a browser found the request page
offering an ADMIN-only action to a STAFF user. The Promote stage was never entered; no image was
published and no container was replaced.

**That is the difference between having tests and having a gate.** A suite tells you something is
wrong; a gate makes it impossible for the wrong thing to reach production.

The same principle recurs throughout. Images are tagged immutably so a rollback has something to
name. The deployment pulls from the registry so a broken push cannot hide. Ansible is idempotent so
running it during an incident is safe. The rollback uses the same code path as a deployment, so the
recovery path is exercised every day rather than only during incidents. In each case the design
choice **removes a way for a human to be wrong**, rather than adding a step for a human to remember.

---

## 6. Honest statement of what is not done

Recorded in full in [`REPORT.md`](REPORT.md) §11, and repeated here because a project that knows
what it is not is more credible than one that has not looked.

| # | Limitation |
|---|---|
| 1 | H2 is single-node; PostgreSQL would be the first change for a multi-user deployment |
| 2 | Donor personal data is unencrypted at rest, with no access audit log |
| 3 | Only the ninety-day interval is modelled as eligibility; real deferral criteria are much wider |
| 4 | "Same city" is a string comparison, not a distance calculation |
| 5 | Ansible manages a Docker node on this machine; the one-line switch to a remote SSH host has not been exercised against a real server |
| 6 | The registry is local and unauthenticated — correct for an offline demonstration, not for anything else |
| 7 | No static-analysis stage, omitted to keep the pipeline inside the six-minute budget |
| 8 | A single environment, with no separate staging tier |

---

## 7. Publishing the repository

The repository is local only, as required. To publish it:

```bash
gh repo create devops-bloodbank-portal --private --source=. --remote=origin
git push -u origin main
git push origin develop
git push origin feature/export-report     # kept unmerged, so a live pull request can be opened
git push origin --tags
```

Then update the **Repository** row at the top of `README.md`, and follow
[`jenkins/SETUP.md`](../jenkins/SETUP.md) §8 to point Jenkins at the remote and replace polling with
a webhook.

### 7.1 Why `feature/export-report` is left unmerged

Every other feature reached `develop` through a reviewed pull request — and a merged pull request
cannot be reopened, so after the push there would be no **live** PR to show an examiner. That branch
is a genuine incomplete feature from the backlog (FE-15) with real acceptance criteria and an honest
list of what remains, recorded in [`pr/PR-03-export-report.md`](pr/PR-03-export-report.md).

---

## 8. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Every step of the demonstration script asserts rather than narrates | A script that only prints what it did proves nothing. The first run failed because `task-15` was not yet tagged — exactly what the assertion exists for |
| The whole workflow is one command | A workflow needing fifteen remembered commands is one that will be demonstrated badly; under examination a forgotten step reads as a missing capability |
| `feature/export-report` is left unmerged on purpose | A merged pull request cannot be reopened, so there would otherwise be no live PR to show |
| The report ends on the limitations | A project that knows what it is not is more credible than one that has not looked |
| The troubleshooting guide records the diagnosis, not just the remedy | The examiner's likely question is "how did you work that out?" |

---

## 9. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-15-release/demo-all-full-run.log`](../evidence/task-15-release/demo-all-full-run.log) | The verified end-to-end run: nine steps green in 151 s |
| [`scripts/demo-all.sh`](../scripts/demo-all.sh) | The complete workflow in one asserting command |
| [`docs/REPORT.md`](REPORT.md) | The consolidated written report |
| [`docs/VIVA_GUIDE.md`](VIVA_GUIDE.md) | Pitch, checklist, demonstration script, Q&A, plan B |
| [`docs/PRESENTATION.md`](PRESENTATION.md) | Ten slides with speaker notes |
| [`evidence/screenshots/CHECKLIST.md`](../evidence/screenshots/CHECKLIST.md) | The 23 captures to take, with the three that carry the project named first |

---

## 10. Task 15 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | The whole workflow is demonstrable | `scripts/demo-all.sh`, nine asserting steps |
| D3 | `mvn clean verify` green | 179 tests |
| D5 | The Jenkins pipeline is green | Build #15 SUCCESS |
| D6 | The Selenium quality gate passed | 16 journeys green |
| D8 | Documentation complete | Report, viva guide, runbook, presentation, 15 task documents |
| D9 | Evidence captured | `evidence/task-15-release/` |
| D10 | The deployed container answers its health check | Nine behavioural assertions |
| D11 | Committed on `main` and tagged | `task-15`, `v1.3.0` |
| D12 | Backlog closed for this release | 15 of 15 stories Done |

**Outcome:** the complete workflow — commit, build, unit tests, quality gate, image, registry,
container, provisioned node, rollback — runs in one asserting command and completed in 151 seconds
with every step green. The written report, viva guide, runbook, presentation outline and
troubleshooting guide are complete, all ten success criteria frozen in Task 1 are met, and the
limitations are stated rather than left to be discovered.
