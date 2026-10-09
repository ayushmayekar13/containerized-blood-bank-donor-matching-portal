#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# build-final-report.sh - assemble docs/FINAL-REPORT.md from the 15 task
# documents plus a front matter.
#
# The consolidated report is GENERATED, not maintained. Each task document is
# the single source for its own content, so correcting a task means editing one
# file and re-running this script - the report cannot drift from the documents
# it summarises.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

OUT="${PROJECT_ROOT}/docs/FINAL-REPORT.md"
cd "$PROJECT_ROOT"

{
cat <<'HEADER'
# Containerized Blood Bank Donor Matching Portal
## DevOps Project — Consolidated Report

**Student:** Ayush Mayekar · **Roll No.** 23102A0018
**Class:** BE Computer Engineering, Division A
**Subject:** DevOps · **Academic Year:** 2026-27, ODD Semester
**Department of Computer Engineering**

---

## About this document

This is the consolidated report for all fifteen tasks of the DevOps subject. It is **generated**
from the individual task documents in `docs/`, so it cannot drift from them: correcting a task means
editing that task's own document and re-running `scripts/build-final-report.sh`.

Every result quoted — build logs, test counts, pipeline stage timings, Ansible play recaps and
container output — is captured output from a real run, retained under `evidence/`, and reproducible
with the commands at the end of each task section.

| | |
|---|---|
| **Repository** | *(add the GitHub URL here after the first push)* |
| **Final release** | `v1.3.0` |
| **One-command demonstration** | `bash scripts/demo-all.sh` |
| **Practical-day guide** | `docs/VIVA_GUIDE.md` |

---

## Project at a glance

| Measure | Result |
|---|---|
| Subject tasks completed | **15 of 15**, each a tagged commit |
| Automated tests | 179 unit tests + 16 Selenium user journeys |
| Blood-group compatibility pairs asserted | 64 of 64 (27 compatible) |
| Commit to running, health-checked container | **101 seconds** |
| Pipeline stages | 11 |
| Ansible second run | **`changed = 0`** (idempotent) |
| Rollback to a previous release | Under 1 minute, data intact |
| Defects found and fixed | 12, each with its cause recorded |
| Release tags | `v1.0.0`, `v1.1.0`, `v1.2.0`, `v1.3.0` |

### The single result worth pointing at

Pipeline build **#9**: Checkout, Build, Unit Test (179 green), Package, Deploy and the behavioural
Health Check all passed — and the build still went red, because a browser found the request page
offering an administrator-only action to a staff user. The Promote stage was never entered; no image
was published and no container was replaced.

**That is the difference between having tests and having a gate.**

---

## Technology stack

| Layer | Selection |
|---|---|
| Language and framework | Java 21 · Spring Boot 3.3 · Spring MVC + Thymeleaf · Spring Data JPA · Spring Security |
| Build | Maven 3.10 → `target/bloodbank.war` |
| Database | H2 2.x in file mode, on the Docker volume `bloodbank-data` |
| Deployment target | Tomcat 10.1, deployed as `ROOT.war` |
| Containerisation | Docker (multi-stage) · private registry `registry:2` on port 5001 |
| CI/CD | Jenkins LTS 2.568.2 — freestyle job + declarative `Jenkinsfile` |
| Testing | JUnit 5 (Surefire) · Selenium 4 WebDriver, headless Chrome (Failsafe) |
| Configuration management | Ansible with the `community.docker` collection |

**Ports.** Application 9090 → container 8080 · Jenkins 8090 · registry 5001. Host 8080 is occupied
on the development machine and macOS reserves 5000 for AirPlay Receiver.

---

## Contents

HEADER

# contents list
for f in docs/[0-9][0-9]-*.md; do
  title="$(head -1 "$f" | sed 's/^# //')"
  echo "- ${title}"
done

cat <<'MID'

Supporting documents: `SRS.md` · `ARCHITECTURE.md` · `API.md` · `backlog.md` · `kanban.md` ·
`definition-of-done.md` · `RUNBOOK.md` · `TROUBLESHOOTING.md` · `VIVA_GUIDE.md` · `PRESENTATION.md`

---

MID

# each task document, verbatim, with links rewritten for the merged context
for f in docs/[0-9][0-9]-*.md; do
  # Links in the task documents are relative to docs/. In the merged report the
  # file still lives in docs/, so ../ links remain correct and sibling links
  # remain correct. Only the per-document front matter is trimmed, because the
  # merged report states the student details once.
  sed -e '/^\*\*Project:\*\* Containerized Blood Bank/d' \
      -e '/^\*\*Completed:\*\*/d' "$f"
  echo
  echo "---"
  echo
done

cat <<'FOOTER'
## End of report

**Ayush Mayekar** · 23102A0018 · BE Computer Engineering, Division A · DevOps · AY 2026-27

Generated from the fifteen task documents by `scripts/build-final-report.sh`.
FOOTER
} > "$OUT"

ok "wrote ${OUT} ($(wc -l < "$OUT" | tr -d ' ') lines, $(du -h "$OUT" | cut -f1))"
