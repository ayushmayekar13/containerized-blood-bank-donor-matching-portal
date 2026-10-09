# Task 15 — Final End-to-End Release, Documentation and Viva

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 9 October 2026 · **Release:** `v1.3.0`

| Required deliverable | Delivered as |
|---|---|
| Final repository | `main` at `v1.3.0`; 15 tagged task commits; `README.md` has the push instructions |
| Live demonstration | `bash scripts/demo-all.sh` — the entire workflow in one command, every step asserting |
| Complete report | [`REPORT.md`](REPORT.md) — 13 sections, abstract to conclusion |
| Screenshots / video | [`evidence/`](../evidence) holds real captured output; [`screenshots/CHECKLIST.md`](../evidence/screenshots/CHECKLIST.md) lists the 23 images to take |
| Presentation | [`PRESENTATION.md`](PRESENTATION.md) — 10 slides with speaker notes and timings |
| Viva | **[`VIVA_GUIDE.md`](VIVA_GUIDE.md)** — pitch, startup checklist, per-task demonstration script, ~45 Q&A, failure recovery, plan B |
| Troubleshooting guide | [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) — 12 entries, each with the diagnosis |
| Limitations and future plan | [`REPORT.md`](REPORT.md) §11–12, [`backlog.md`](backlog.md) |

---

## 1. The end-to-end run

`scripts/demo-all.sh` runs the complete workflow:

```
STEP 1  Preflight                toolchain, Docker daemon, ports, images
STEP 2  Git                      15 task tags, release tags, the branch graph with both PR diamonds
STEP 3  Build and unit tests     Maven → bloodbank.war, 179 tests
STEP 4  Jenkins                  the full 11-stage pipeline including the Selenium gate
STEP 5  Registry                 versioned images retained
STEP 6  Running container        image provenance; unprivileged PID 1
STEP 7  Health check             nine behavioural assertions
STEP 8  Ansible                  clean provisioning, changed=0, health, rollback, roll-forward
```

**Every step asserts.** A demonstration script that only narrates what it did proves nothing — the
point is that it fails loudly if any part of the claim is untrue. That property was useful
immediately: the first run reported a failure because `task-15` was not yet tagged, which is exactly
what the assertion is for.

```bash
bash scripts/demo-all.sh            # everything, ~4 minutes
bash scripts/demo-all.sh --quick    # skip the Jenkins run, ~90 seconds
```

It exists in this form because a workflow that takes fifteen remembered commands to demonstrate is a
workflow that will be demonstrated badly. Under examination conditions, a forgotten step reads as a
capability that does not exist.

## 2. Documentation delivered

| Document | For |
|---|---|
| [`README.md`](../README.md) | Anyone opening the repository: what it is, how to run it, where everything lives |
| [`REPORT.md`](REPORT.md) | The written submission — abstract, requirements, architecture, pipeline, results, limitations |
| **[`VIVA_GUIDE.md`](VIVA_GUIDE.md)** | **Practical day** — pitch, T-30 checklist, demonstration script, Q&A, recovery, plan B |
| [`PRESENTATION.md`](PRESENTATION.md) | 10 slides with speaker notes and timings |
| [`RUNBOOK.md`](RUNBOOK.md) | Operating it: every routine command, incident procedures, maintenance |
| [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) | 12 problems with their **diagnosis**, not just their remedy |
| `01`–`15` | One document per subject task, each stating what was done, how to reproduce it and what to expect |
| [`SRS.md`](SRS.md) · [`ARCHITECTURE.md`](ARCHITECTURE.md) · [`API.md`](API.md) | Specification and design |
| [`backlog.md`](backlog.md) · [`kanban.md`](kanban.md) · [`definition-of-done.md`](definition-of-done.md) | Agile artefacts |
| [`pr/`](pr) | Pull-request descriptions and review records |

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

## 4. What this project demonstrates, in one paragraph

Pipeline build **#9**: Checkout, Build, Unit Test (179 green), Package, Deploy and the behavioural
Health Check all passed — and the build still went red, because a browser found the request page
offering an ADMIN-only action to a STAFF user. The Promote stage was never entered; no image was
published and no container was replaced. That is the difference between having tests and having a
gate: a suite tells you something is wrong, while a gate makes it impossible for the wrong thing to
reach production.

## 5. Honest statement of what is not done

Recorded in full in [`REPORT.md`](REPORT.md) §11, and worth repeating because a project that knows
what it is not is more credible than one that has not looked:

- H2 is single-node; donor data is unencrypted at rest with no access audit log.
- Only the 90-day interval is modelled as eligibility; real deferral criteria are much wider.
- Ansible manages a Docker node on this machine; the one-line switch to a remote SSH host has not
  been exercised against a real server.
- The registry is local and unauthenticated — correct for an offline demonstration, not for anything
  else.
- No static-analysis stage, and a single environment with no separate staging tier.

## 6. Publishing the repository

The repository is local only, as required. To publish it:

```bash
gh repo create devops-bloodbank-portal --private --source=. --remote=origin
git push -u origin main
git push origin develop
git push origin feature/export-report     # kept unmerged, so a live pull request can be opened
git push origin --tags
```

Then update the **Repository** row at the top of `README.md`, and follow
[`jenkins/SETUP.md`](../jenkins/SETUP.md) §8 to point Jenkins at the remote and replace polling with a
webhook.

## 7. Verification

```bash
bash scripts/demo-all.sh                  # the whole workflow, asserting at every step
bash scripts/verify-reliability.sh        # provisioning, idempotency, health, rollback
mvn clean verify                          # 179 unit tests
bash scripts/run-ui-tests.sh              # 16 Selenium journeys
git tag -l | sort -V                      # 15 task tags + 4 release tags
```
