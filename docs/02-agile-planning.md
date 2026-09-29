# Task 2 — Agile Planning and DevOps Workflow

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 29 September 2026

This task converts the frozen scope from [Task 1](01-problem-definition.md) into an executable
agile plan: user stories with acceptance criteria, a prioritised product backlog, a 15-task
Kanban/Scrum plan, a Definition of Done, and the DevOps lifecycle workflow this project follows
from development through to operations.

Companion documents: [backlog.md](backlog.md) · [kanban.md](kanban.md) ·
[definition-of-done.md](definition-of-done.md) ·
[DevOps lifecycle diagram](diagrams/devops-lifecycle.md)

---

## 1. Agile approach used

A single-developer **Kanban-flavoured Scrum**: the 15 tasks of the subject task sheet are treated
as the sprint backlog of one 11-day release sprint (29 Sep → 9 Oct 2026), pulled through a
five-column Kanban board with a **work-in-progress limit of 1** (one task finished, committed and
tagged before the next is started). Each task is a vertical slice that ends in working,
committed software or an executable automation asset — never a document alone where code was
possible.

| Ceremony | How it is done here | Evidence |
|---|---|---|
| Backlog refinement | Stories written with acceptance criteria before coding | `docs/backlog.md` |
| Sprint planning | 15 tasks ordered, estimated in story points, dated | §4, §5 of this document |
| Daily stand-up | Daily task-start note (what I did / will do / blockers) | commit log dates + `docs/kanban.md` |
| Review / demo | Running application and pipeline at each tagged task | `task-01 … task-15` tags |
| Retrospective | Lessons recorded per task and consolidated | `docs/REPORT.md`, `docs/TROUBLESHOOTING.md` |

## 2. Personas

| Persona | Name used in stories | Goal |
|---|---|---|
| Blood bank technician | **Staff** (`STAFF`) | Register donors fast, record requests, find a match without phoning around |
| Medical officer | **Admin** (`ADMIN`) | Sign off outcomes, see supply vs demand at a glance, hold staff accountable |
| Voluntary donor | **Donor** (`DONOR`) | Be contacted only when actually eligible; control own availability |
| Platform operator | **Operator** | Deploy, upgrade and roll back the portal with no manual steps |

## 3. User stories with acceptance criteria

Acceptance criteria are written in Given / When / Then so they map one-to-one onto automated tests.
`[U]` = covered by a JUnit unit test, `[S]` = covered by a Selenium journey, `[A]` = covered by an
Ansible/health-check assertion.

---

### Epic A — Donor registry

**US-01 — Register a donor** *(3 pts, MoSCoW: Must)*
> As **Staff**, I want to register a donor with their blood group, contact details and city, so
> that the donor can be found later when a matching request arrives.

- AC-01.1 — Given I am signed in as Staff, when I submit a donor with a valid name, blood group,
  10-digit phone, email and city, then the donor is saved and appears in the donor list. `[S]`
- AC-01.2 — Given a blood group outside the eight valid ABO/Rh values, when I submit, then the
  form is rejected with a field-level validation message and nothing is saved. `[U][S]`
- AC-01.3 — Given a phone number that is not exactly 10 digits, when I submit, then the form is
  rejected. `[U]`
- AC-01.4 — Given a donor with no recorded last-donation date, when the donor is saved, then the
  donor is treated as eligible. `[U]`

**US-02 — Search the donor registry** *(3 pts, Must)*
> As **Staff**, I want to search donors by blood group, city and availability, so that I can
> answer "who can we call right now?" in seconds instead of reading a register.

- AC-02.1 — Given donors in several cities and blood groups, when I filter by blood group `O-`,
  then only `O-` donors are listed. `[U][S]`
- AC-02.2 — Given a city filter, when combined with a blood group filter, then both are applied
  together (logical AND). `[U]`
- AC-02.3 — Given the "available only" filter, when applied, then donors flagged unavailable are
  excluded. `[U]`
- AC-02.4 — Given no donor matches, when I search, then an explicit "no donors found" message is
  shown rather than an empty page. `[S]`

**US-03 — Update a donor / record a donation** *(2 pts, Must)*
> As **Staff**, I want to update a donor's details and record the date they last donated, so that
> the 90-day eligibility rule is enforced automatically from then on.

- AC-03.1 — Given an existing donor, when I change the city and save, then the change is
  persisted and visible in the list. `[S]`
- AC-03.2 — Given I record a last-donation date of today, then the donor is immediately excluded
  from match shortlists. `[U]`
- AC-03.3 — Given a last-donation date more than 90 days in the past, then the donor is eligible
  again. `[U]`

**US-04 — Donor views and controls their own record** *(2 pts, Should)*
> As a **Donor**, I want to see my own profile and turn my availability off, so that I am not
> called while I am travelling or unwell.

- AC-04.1 — Given I am signed in as Donor, when I open the portal, then I see my own record and
  not the full registry. `[S]`
- AC-04.2 — Given I am signed in as Donor, when I attempt to edit another donor, then the request
  is refused with HTTP 403. `[U]`

---

### Epic B — Blood requests and matching

**US-05 — Record a blood request** *(3 pts, Must)*
> As **Staff**, I want to record an incoming blood request with the patient, blood group, units,
> hospital, city and urgency, so that the request has an owner and a tracked state.

- AC-05.1 — Given valid input, when I create a request, then it is saved with status `NEW` and a
  creation timestamp. `[U][S]`
- AC-05.2 — Given units needed ≤ 0 or > 10, when I submit, then the form is rejected. `[U]`
- AC-05.3 — Given a created request, then it appears on the dashboard under "open requests". `[S]`

**US-06 — Get a ranked list of compatible, eligible donors** *(8 pts, Must)* — *core of the project*
> As **Staff**, I want the portal to show me the donors who can actually give blood for this
> request, ranked best-first, so that I stop guessing compatibility from memory.

- AC-06.1 — Given a request for `B+`, then the shortlist contains only donors of groups `B+`,
  `B-`, `O+`, `O-`, applying the standard red-cell compatibility matrix. `[U]`
- AC-06.2 — Given the full 8 × 8 set of recipient/donor group pairs, then the engine's verdict is
  correct for all 64 pairs. `[U]`
- AC-06.3 — Given a compatible donor who donated 30 days ago, then that donor is **excluded** as
  ineligible. `[U]`
- AC-06.4 — Given a compatible donor flagged unavailable, then that donor is excluded. `[U]`
- AC-06.5 — Given two equally compatible donors, one in the request's city and one elsewhere, then
  the same-city donor is ranked higher. `[U]`
- AC-06.6 — Given an exact blood-group match and a merely compatible match, then the exact match
  ranks higher (preserves rare universal-donor stock). `[U]`
- AC-06.7 — Given 500 donors, then the shortlist is returned in under 2 seconds. `[A]`
- AC-06.8 — Given I open a request, when I click "Find matches", then the ranked shortlist is
  displayed with each donor's group, city and eligibility date. `[S]`

**US-07 — Role-based request status workflow** *(5 pts, Must)*
> As **Admin**, I want only the right role to be able to advance a request, so that there is
> accountability for every outcome.

- AC-07.1 — Given a `NEW` request, when Staff assigns a matched donor, then status becomes
  `MATCHED` and the donor is recorded against the request. `[U][S]`
- AC-07.2 — Given a `MATCHED` request, when Admin marks it fulfilled, then status becomes
  `FULFILLED` and the matched donor's last-donation date is set to today. `[U][S]`
- AC-07.3 — Given a `MATCHED` or `NEW` request, when Admin rejects it with a reason, then status
  becomes `REJECTED`. `[U]`
- AC-07.4 — Given a `NEW` request, when **Staff** attempts to fulfil it directly, then the
  transition is refused (illegal transition **and** insufficient role). `[U][S]`
- AC-07.5 — Given a `FULFILLED` or `REJECTED` request, then no further transition is accepted —
  these are terminal states. `[U]`
- AC-07.6 — Given a Donor account, when any transition is attempted, then it is refused with
  HTTP 403. `[U]`

---

### Epic C — Visibility

**US-08 — Summary dashboard** *(3 pts, Must)*
> As **Admin**, I want a single screen showing donors per blood group, requests per status and
> shortage flags, so that I can see supply against demand without running queries.

- AC-08.1 — Given donors and requests exist, then the dashboard shows a count of available
  eligible donors per blood group. `[U][S]`
- AC-08.2 — Given requests in each state, then the dashboard shows counts for `NEW`, `MATCHED`,
  `FULFILLED`, `REJECTED`. `[U]`
- AC-08.3 — Given a blood group with open requests but zero eligible donors, then that group is
  flagged as a shortage. `[U][S]`

**US-09 — Authentication and roles** *(3 pts, Must)*
> As **Operator**, I want the portal to require sign-in and separate three roles, so that the
> workflow permissions in US-07 are enforceable.

- AC-09.1 — Given I am not signed in, when I request any page except the login page and the
  health endpoint, then I am redirected to login. `[S]`
- AC-09.2 — Given valid credentials, when I sign in, then I land on the dashboard with my role
  displayed. `[S]`
- AC-09.3 — Given `/actuator/health`, then it is reachable without authentication so that
  automated health checks and container probes work. `[A]`

---

### Epic D — DevOps capability (the assessed epic)

**US-10 — Reproducible build** *(3 pts, Must)*
> As **Operator**, I want `mvn clean verify` on a clean checkout to produce a deployable WAR, so
> that any machine or CI agent builds the same artefact.
- AC-10.1 — A clean clone builds with no manual steps beyond `JAVA_HOME`. `[A]`
- AC-10.2 — The build produces `target/bloodbank.war`, archived by CI. `[A]`

**US-11 — Continuous integration on every commit** *(5 pts, Must)*
> As **Operator**, I want Jenkins to build and unit-test automatically when I push, so that a
> broken commit is visible within minutes.
- AC-11.1 — A commit triggers the job (SCM polling / hook) without manual start. `[A]`
- AC-11.2 — Unit test results are published and trend over builds. `[A]`

**US-12 — Pipeline as code with deployment** *(5 pts, Must)*
> As **Operator**, I want the pipeline defined in a `Jenkinsfile` in the repository with a
> parameterised target environment, so that the pipeline is versioned with the code.
- AC-12.1 — Stages: Checkout → Build → Unit Test → Package → Docker Build → Deploy → UI Test. `[A]`
- AC-12.2 — At least one environment setting (deploy port / image tag / target host) is a build
  parameter with a default. `[A]`

**US-13 — Selenium quality gate** *(5 pts, Must)*
> As **Operator**, I want failing UI tests to stop the deployment, so that a regression can never
> reach the running container.
- AC-13.1 — A deliberately introduced defect turns the pipeline red. `[A]`
- AC-13.2 — When UI tests fail, the publish/promote stage does not execute. `[A]`
- AC-13.3 — Failures attach a screenshot of the browser at the point of failure. `[S]`

**US-14 — Containerized delivery** *(5 pts, Must)*
> As **Operator**, I want a versioned Docker image published to a registry and a fresh container
> deployed automatically after tests pass, so that releases are immutable and repeatable.
- AC-14.1 — Image tagged with the build number and `latest`, pushed to the registry. `[A]`
- AC-14.2 — The previous container is replaced by one created from the new image. `[A]`
- AC-14.3 — Data survives container replacement (named volume). `[A]`

**US-15 — Infrastructure and recovery as code** *(5 pts, Must)*
> As **Operator**, I want the target environment described in Ansible, proved idempotent, with a
> health check and a tested rollback, so that recovery is a routine operation.
- AC-15.1 — A single playbook provisions prerequisites and deploys the application. `[A]`
- AC-15.2 — A second identical run reports `changed=0`. `[A]`
- AC-15.3 — A health check asserts the application answers correctly after provisioning. `[A]`
- AC-15.4 — `rollback.yml` restores the previous image tag and the health check passes. `[A]`

**Total: 64 story points across 15 stories.**

## 4. 15-task Scrum/Kanban plan

Board columns: **Backlog → Ready → In Progress → Review/Verify → Done**. WIP limit 1 in
*In Progress*. A task leaves *Review/Verify* only when it satisfies the
[Definition of Done](definition-of-done.md), and leaving *Done* means committed and tagged.

| Task | Deliverable (sprint item) | Stories | Pts | Date | Exit criterion (= tag) |
|---|---|---|---|---|---|
| 1 | Problem definition and frozen scope | — | 2 | 29 Sep | `task-01` |
| 2 | Agile plan, backlog, board, DoD, lifecycle diagram | — | 3 | 29 Sep | `task-02` |
| 3 | SRS, architecture, data model, API list, local setup | US-10 | 5 | 30 Sep | `task-03` |
| 4 | Repository, README, structure, templates, branch policy | US-10 | 3 | 30 Sep | `task-04` |
| 5 | Feature 1: donor registry + matching engine (feature branch + PR) | US-01→04, US-06 | 13 | 1 Oct | `task-05` |
| 6 | MVP completion: requests, workflow, dashboard; conflict drill | US-05, 07, 08, 09 | 14 | 2 Oct | `task-06`, `v1.0.0` |
| 7 | Jenkins install + Maven CI job + artefact archiving | US-11 | 5 | 3 Oct | `task-07` |
| 8 | `Jenkinsfile` + Tomcat deploy + parameterised environment | US-12 | 5 | 4 Oct | `task-08` |
| 9 | Selenium test plan + 5 journeys run through Maven | US-13 | 5 | 5 Oct | `task-09` |
| 10 | Selenium in Jenkins as a quality gate; defect → fix → rerun | US-13 | 5 | 6 Oct | `task-10` |
| 11 | `Dockerfile`, image build/tag, full container lifecycle | US-14 | 4 | 7 Oct | `task-11` |
| 12 | Jenkins → versioned image → registry → fresh container | US-14 | 5 | 7 Oct | `task-12`, `v1.1.0` |
| 13 | Configuration specification + Ansible inventory/playbook | US-15 | 4 | 8 Oct | `task-13` |
| 14 | Clean provisioning, idempotency, health check, rollback | US-15 | 5 | 8 Oct | `task-14`, `v1.2.0` |
| 15 | End-to-end release, report, presentation, viva prep | all | 4 | 9 Oct | `task-15`, `v1.3.0` |

**Capacity check.** 82 points of planned work across 11 calendar days ⇒ a planned velocity of
~7.5 points/day for one developer. Tasks 5 and 6 are the two largest items and are deliberately
placed early, while the remaining tasks are automation work that builds on a stable application.

### Risks on the plan

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Jenkins/Tomcat port clash with existing services on the host | High | Medium | Ports fixed up front (Jenkins 8090, app 9090, registry 5001) and asserted by `scripts/preflight.sh` |
| Selenium/chromedriver version drift | Medium | High | Selenium 4 Selenium Manager resolves the driver for the installed Chrome automatically |
| Network unavailable during the demonstration | Medium | High | Local registry instead of Docker Hub; CSS/JS vendored into the WAR; all evidence pre-captured |
| Docker image rebuild slowness blocking the pipeline | Medium | Low | Multi-stage build with a cached Maven dependency layer |
| Scope creep in the application diluting DevOps work | Medium | High | Scope frozen in Task 1; new ideas go to the backlog's "Future" section |

## 5. DevOps workflow (development → operations)

See [diagrams/devops-lifecycle.md](diagrams/devops-lifecycle.md) for the diagrams. The workflow
implemented by this project is:

```
 PLAN      Backlog item / issue, branch named feature/<slug>
   ↓
 CODE      Local edit, unit tests red → green, conventional commit messages
   ↓
 BUILD     Maven clean package → bloodbank.war   (Jenkins stage: Build/Package)
   ↓
 TEST      Surefire unit tests (fast) → Failsafe Selenium journeys (quality gate)
   ↓
 RELEASE   Versioned Docker image, tagged with build number, pushed to registry
   ↓
 DEPLOY    Fresh container created from the new image, named volume re-attached
   ↓
 OPERATE   Ansible provisions/configures the node; container restart policy
   ↓
 MONITOR   /actuator/health probe, container health check, Jenkins build trend
   ↓
 FEEDBACK  Defect or idea → new backlog item → PLAN
```

Each arrow is automated in this project except `PLAN → CODE` (human) and `MONITOR → FEEDBACK`
(human judgement). The gate between *TEST* and *RELEASE* is the Selenium quality gate from Task 10:
if it is red, nothing downstream of it executes.

### Branching and promotion model

```
feature/<slug>  ──commits──►  PR + review  ──--no-ff merge──►  develop  ──merge──►  main ──tag──► release
```

- `main` — always releasable; tagged `vX.Y.Z` at each release; protected by the pipeline.
- `develop` — integration branch where features are combined and the MVP is assembled.
- `feature/<slug>` — one branch per user story; short-lived; merged with `--no-ff` so that the
  merge commit permanently records the feature boundary.
- Conventions, naming rules and the review checklist are in `CONTRIBUTING.md` (Task 4).

## 6. Definition of Done

Summarised here, authoritative version in [definition-of-done.md](definition-of-done.md):

1. Acceptance criteria of the story demonstrably met.
2. Unit tests written and green; UI journey added or updated where the story touches the UI.
3. `mvn clean verify` green from a clean target directory.
4. No new compiler or dependency warnings introduced.
5. Documentation updated in the same commit as the change (docs live with the code).
6. Change committed on the correct branch with a descriptive message and merged via a reviewed PR.
7. Pipeline green end to end, including the quality gate, for the commit being called done.
8. Deployed container reachable and `/actuator/health` returning `UP`.
9. Evidence (log, report or screenshot) captured under `evidence/` for the task.
10. Task tagged `task-NN` so that the examiner can check out exactly that state.
