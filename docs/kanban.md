# Kanban / Scrum Task Board

Project: Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
Sprint: 29 September – 9 October 2026 · WIP limit in *In Progress*: **1**

Columns and their entry rules:

| Column | A card may enter when… |
|---|---|
| **Backlog** | The item exists in [backlog.md](backlog.md) |
| **Ready** | It has acceptance criteria, an estimate and no unresolved dependency |
| **In Progress** | The previous card reached *Done* (WIP limit 1) and a branch exists for it |
| **Review / Verify** | Code complete, tests green locally, evidence captured |
| **Done** | The [Definition of Done](definition-of-done.md) is fully satisfied, commit tagged `task-NN` |

## Board at end of sprint (9 October 2026)

```
┌──────────────┬──────────────┬──────────────┬────────────────┬──────────────────────────────┐
│   BACKLOG    │    READY     │ IN PROGRESS  │ REVIEW/VERIFY  │            DONE              │
│              │              │  (WIP = 1)   │                │                              │
├──────────────┼──────────────┼──────────────┼────────────────┼──────────────────────────────┤
│ FE-01 notify │              │              │                │ T1  Problem definition       │
│ FE-02 geo    │              │              │                │ T2  Agile plan + DoD         │
│ FE-03 units  │              │              │                │ T3  SRS + architecture       │
│ FE-04 camps  │              │   (empty —   │    (empty)     │ T4  Repo + branch policy     │
│ FE-05 OTP    │              │    sprint    │                │ T5  Donor registry + match   │
│ FE-06 tenant │              │   complete)  │                │ T6  MVP + conflict + v1.0.0  │
│ FE-07 forecast│             │              │                │ T7  Jenkins CI job           │
│ FE-08 mobile │              │              │                │ T8  Jenkinsfile + deploy     │
│ FE-09 FHIR   │              │              │                │ T9  Selenium suite           │
│ FE-10 postgres│             │              │                │ T10 Quality gate + defect fix│
│ FE-11 crypto │              │              │                │ T11 Docker lifecycle         │
│ FE-12 k8s    │              │              │                │ T12 CD to registry + v1.1.0  │
│ FE-13 blue/gn│              │              │                │ T13 Ansible playbook         │
│ FE-14 sonar  │              │              │                │ T14 Idempotency + rollback   │
│ FE-15 export │              │              │                │ T15 Release + docs + viva    │
└──────────────┴──────────────┴──────────────┴────────────────┴──────────────────────────────┘
```

`FE-15` (report export) deliberately remains in the backlog with an open branch
`feature/export-report`, so that an unmerged branch and a live pull request can be demonstrated.

## Daily flow log (stand-up record)

| Date | Done since last | Doing next | Blocker / decision |
|---|---|---|---|
| Tue 29 Sep | Scope frozen (T1); agile plan, backlog, DoD, lifecycle diagram (T2) | SRS and architecture | Decided WIP limit 1 to keep one commit per task clean |
| Wed 30 Sep | SRS, use-case + architecture diagrams, data model, API list (T3); repo scaffolding, README, templates, branch policy, Spring Boot skeleton builds and starts (T4) | Donor registry feature | Host port 8080 occupied → Jenkins moved to 8090, app to 9090, registry to 5001 |
| Thu 1 Oct | `feature/donor-registry`: entity, repository, service, matching engine, UI, 24 unit tests; PR-01 reviewed and merged `--no-ff` (T5) | Requests, workflow, dashboard | BUG-01 found by the eligibility boundary test (`>` vs `>=` at the 90-day cut-off) |
| Fri 2 Oct | `feature/request-workflow`: requests, role-based workflow, dashboard, security; merge conflict in the dashboard template created and resolved; `develop` → `main`; `v1.0.0` (T6) | Jenkins | BUG-02: shortage flag counted unavailable donors; unified on one eligibility predicate |
| Sat 3 Oct | Jenkins 2.568.2 on 8090, Maven CI job, SCM polling, WAR archived (T7) | Pipeline as code | Jenkins needed `JAVA_HOME` pinned to JDK 21; captured in `jenkins/SETUP.md` |
| Sun 4 Oct | `Jenkinsfile` with checkout/build/test/package/deploy, parameterised `DEPLOY_PORT` and `IMAGE_TAG`, deployed to Tomcat (T8) | Selenium | Chose a declarative pipeline over scripted for readable stage view |
| Mon 5 Oct | Test plan, 5 Selenium journeys with assertions, screenshot-on-failure, Maven failsafe wiring (T9) | Jenkins quality gate | Selenium Manager resolves chromedriver automatically — no pinned driver in the repo |
| Tue 6 Oct | Selenium integrated into Jenkins, JUnit reports published, gate blocks deploy; BUG-03 introduced deliberately, pipeline red, fixed, rerun green (T10) | Docker | Gate implemented as a failing stage before deploy, not `catchError` |
| Wed 7 Oct | `Dockerfile` (multi-stage, Tomcat 10.1 + JDK 21), image build/tag, full container lifecycle log (T11); pipeline pushes versioned image to `localhost:5001` and recreates the container; `v1.1.0` (T12) | Ansible | Named volume `bloodbank-data` keeps the H2 database across container replacement |
| Thu 8 Oct | Configuration specification; Ansible inventory, playbook and roles; first run (T13); clean provisioning, `changed=0` on rerun, health check, rollback to previous tag; `v1.2.0` (T14) | Final release | Ansible drives Docker on the local node via `community.docker`; SSH inventory documented as a one-line change |
| Fri 9 Oct | Full end-to-end run `scripts/demo-all.sh`, consolidated report, presentation, viva guide; `v1.3.0` (T15) | — | Sprint closed: 15/15 tasks Done |

## Flow metrics

| Metric | Value |
|---|---|
| Cards committed | 15 tasks (64 story points of user stories + 18 points of documentation work) |
| Cards completed | 15 / 15 |
| Sprint length | 11 calendar days |
| Average throughput | 1.36 tasks per day |
| Average cycle time (Ready → Done) | ≈ 0.7 day |
| Longest cycle time | Task 6 (MVP completion + conflict drill), 1 day, 14 points |
| WIP limit breaches | 0 |
| Defects escaping to a later task | 0 (BUG-01 and BUG-02 were caught in the task that created them; BUG-03 was intentional) |

## Retrospective (end of sprint)

**What worked**
- A WIP limit of 1 made "one commit per task" natural instead of forced; the git history reads as
  the project plan.
- Writing acceptance criteria as Given/When/Then meant that nearly every criterion became either a
  JUnit assertion or a Selenium step with no translation effort.
- Fixing the ports before writing any script avoided a class of late, confusing failures.

**What did not**
- Tasks 5 and 6 carried 27 of the 64 story points. The application work should have been split
  across three tasks and the documentation tasks compressed.
- The first Selenium run depended on a browser window being focused; moving to headless Chrome
  should have been the default from the start.

**Actions carried forward**
- Add a static-analysis stage (FE-14) before growing the application further.
- Replace H2 with PostgreSQL (FE-10) before any multi-user deployment.
- Keep `scripts/preflight.sh` as the first thing run on any new machine.
