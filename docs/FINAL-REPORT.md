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

- Task 1 — Problem Definition and Scope
- Task 2 — Agile Planning and DevOps Workflow
- Task 3 — Requirements, Architecture and Technology Setup
- Task 4 — Git and GitHub Repository Initialization
- Task 5 — Feature Development with Branching
- Task 6 — MVP Completion and Git Collaboration
- Task 7 — Jenkins Installation and Continuous Integration Job
- Task 8 — Pipeline as Code and Server Deployment
- Task 9 — Selenium Test Design and Local Execution
- Task 10 — Continuous Testing in Jenkins
- Task 11 — Docker Image and Container Lifecycle
- Task 12 — Jenkins-Docker Continuous Deployment
- Task 13 — Configuration Management Script
- Task 14 — Automated Provisioning and Reliability Validation
- Task 15 — Final End-to-End Release, Documentation and Viva

Supporting documents: `SRS.md` · `ARCHITECTURE.md` · `API.md` · `backlog.md` · `kanban.md` ·
`definition-of-done.md` · `RUNBOOK.md` · `TROUBLESHOOTING.md` · `VIVA_GUIDE.md` · `PRESENTATION.md`

---

# Task 1 — Problem Definition and Scope

**Deliverable:** problem statement, stakeholder list, objectives, constraints and approved MVP
scope.


---

## 1. Real-time need

Blood is a perishable, uncertain-supply resource. In most Indian hospitals the gap between a patient
needing blood and a matching donor being contacted is still bridged by phone calls, WhatsApp
forwards and paper registers.

### 1.1 The three facts that make this a real problem

| # | Fact | Consequence |
|---|---|---|
| 1 | Blood cannot be manufactured, and whole blood lasts roughly 35–42 days | Stock cannot be built up in advance; supply depends on reaching donors quickly |
| 2 | A donor must wait about 90 days after a whole-blood donation | A register that does not track the last donation date repeatedly calls back ineligible people |
| 3 | Compatibility is not "same group only" — a B+ patient can receive from B+, B−, O+ or O− | A phone-based process almost never exploits this, so compatible donors in the same city go unused |

### 1.2 The measurable failure

**Time-to-match** — from a request being raised to a compatible, eligible, reachable donor being
identified — is routinely measured in hours when it should be measured in seconds. Nobody can
answer *"how many eligible O− donors do we have in this city right now?"* without reading a
register.

### 1.3 Pain points in the existing manual process

| # | Pain point | Consequence |
|---|---|---|
| P1 | Donor records in registers and scattered spreadsheets | No search; duplicates; records lost |
| P2 | Eligibility tracked mentally or not at all | Ineligible donors called; eligible ones missed |
| P3 | Compatibility judged from memory | Compatible donors never contacted |
| P4 | A request is a phone call, not a record | Nobody knows whether a request is still open |
| P5 | No separation of duties | Any staff member can mark anything done; no accountability |
| P6 | No aggregate view of supply | Shortages discovered only when a request fails |
| P7 | No audit trail | Disputes cannot be resolved; no data for planning |

---

## 2. Problem statement

> Blood banks and hospital transfusion desks currently match blood requests to donors through
> manual, memory-driven, register-based processes. There is no single searchable donor registry, no
> automatic enforcement of blood-group compatibility or the 90-day donation-eligibility rule, no
> tracked lifecycle for a blood request, and no role-based accountability for who may advance a
> request. Consequently time-to-match is high, eligible compatible donors are under-used, and
> management has no real-time visibility of supply against demand.
>
> **This project delivers a containerized web portal that maintains a searchable donor registry,
> records blood requests, automatically ranks compatible and eligible donors for each request,
> enforces a role-based request status workflow, and presents a live summary dashboard — the whole
> system being built, tested, packaged and deployed through an automated DevOps pipeline.**

---

## 3. Target users and stakeholders

### 3.1 Primary users

| User | Role in the system | What they do |
|---|---|---|
| Blood bank technician | `STAFF` | Register and update donors, record incoming requests, run the matching engine, assign a matched donor |
| Medical officer | `ADMIN` | Everything STAFF can do, plus confirm or reject the outcome of a request, and view the dashboard |
| Registered donor | `DONOR` | View own profile and donation history, control own availability |
| Hospital transfusion desk | via `STAFF` | Raises a request that STAFF records in the portal |

### 3.2 Stakeholder register

| Stakeholder | Interest | Influence |
|---|---|---|
| Patients and families | Fast, correct match — the ultimate beneficiary | Low (indirect) |
| Voluntary donors | Not being contacted when ineligible; privacy of contact details | Medium |
| Blood bank staff | A tool faster than the phone, with low training overhead | **High** (daily users) |
| Medical officer | Accountability, audit trail, supply visibility | **High** (approves the system) |
| Hospital transfusion desk | Reliable turnaround on raised requests | Medium |
| IT / operations | Deployability, reproducibility, ease of rollback | **High** (owns the pipeline) |
| Regulatory bodies (NBTC / SBTC) | Correct eligibility enforcement, traceability | Medium (constraints) |
| Course faculty | Demonstrated DevOps coverage across all 15 tasks | **High** (acceptance) |

### 3.3 Out of scope for the MVP

Donor self-registration without staff involvement; hospital information systems as direct API
consumers; and regulators as report recipients. Each is recorded as a future enhancement rather
than silently dropped.

---

## 4. Pain point to objective traceability

| Pain point | Objective that addresses it |
|---|---|
| P1 no searchable registry | O1 — searchable donor registry |
| P2 eligibility untracked | O3 — automatic 90-day enforcement |
| P3 compatibility from memory | O3 — ABO/Rh matrix applied automatically |
| P4 requests untracked | O2, O4 — recorded requests with a status workflow |
| P5 no separation of duties | O4 — role-based transitions |
| P6 no view of supply | O5 — summary dashboard with shortage flags |
| P7 no audit trail | O4 — every transition stamps the acting user and time |

---

## 5. Objectives

### 5.1 Product objectives

| ID | Objective |
|---|---|
| O1 | Maintain a single, searchable donor registry (create, view, update, search) |
| O2 | Record blood requests with patient, blood group, units, hospital, city and urgency |
| O3 | Produce a ranked list of **compatible, eligible and available** donors for any request |
| O4 | Enforce `NEW → MATCHED → FULFILLED / REJECTED` with per-role permissions |
| O5 | Present a dashboard of donors per blood group, requests per status and shortage flags |

### 5.2 Engineering (DevOps) objectives

| ID | Objective |
|---|---|
| O6 | Version-control with a documented branching policy, pull-request review, a resolved merge conflict and semantic tags |
| O7 | Build and package reproducibly with Maven, producing an archivable artefact |
| O8 | Automate build → test → package → deploy in Jenkins, defined as code |
| O9 | Automate functional verification with Selenium as a **quality gate** that stops deployment |
| O10 | Containerize, publish versioned images to a registry, deploy automatically after a green run |
| O11 | Describe the environment as code with Ansible, proved **idempotent**, with health check and rollback |

---

## 6. Constraints

### 6.1 Technical constraints

| ID | Constraint |
|---|---|
| C1 | Single developer, single laptop (Apple Silicon macOS); no cloud budget, so every environment is local |
| C2 | Host port 8080 is already occupied, so Jenkins and the application must be re-mapped (8090 / 9090) |
| C3 | The build tool must be Maven and the deployment target Tomcat or Nginx (course requirement) |
| C4 | Java 21 is required by Spring Boot 3; the system default `java` is 11, so every script pins `JAVA_HOME` |
| C5 | No external database server — an embedded H2 file database on a Docker volume |
| C6 | The demonstration must work **offline**: no CDN assets, no Docker Hub dependency at demo time |

### 6.2 Process constraints

| ID | Constraint |
|---|---|
| C7 | Delivered as exactly 15 tasks, each with its own commit, in the sequence fixed by the task sheet |
| C8 | Calendar: 29 September 2026 to 9 October 2026 |

### 6.3 Domain and regulatory constraints

| ID | Constraint |
|---|---|
| C9 | Donor phone numbers and eligibility are personal data. In production this needs consent capture, encryption at rest and access logging. The MVP stores them in plain form locally and restricts access by role only — stated as a known limitation |
| C10 | The portal **matches donors; it does not certify blood units.** Serological screening, cross-matching and unit labelling are outside the system boundary |

---

## 7. Measurable success criteria

Every criterion names the measurement that settles it. "Fast matching" cannot be verified; "a
shortlist for 500 donors in under two seconds" can.

| ID | Criterion | Target | How it is measured |
|---|---|---|---|
| S1 | Time to produce a ranked shortlist | < 2 s for 500 donors | Response time of `GET /api/requests/{id}/matches` |
| S2 | Compatibility correctness | 64/64 pairs correct | Unit test over the full 8 × 8 matrix |
| S3 | Eligibility correctness | 0 ineligible donors shortlisted | Unit test: donation < 90 days ⇒ excluded |
| S4 | Workflow safety | 0 illegal transitions accepted | Unit test per invalid transition and role |
| S5 | Build reproducibility | Green from a clean clone | Jenkins build on a fresh workspace |
| S6 | Commit to deployed container | < 6 min | Jenkins pipeline stage view |
| S7 | Quality gate effectiveness | A failing journey ⇒ deploy never runs | Deliberate-defect pipeline run (Task 10) |
| S8 | Provisioning idempotency | 2nd playbook run reports `changed=0` | Ansible play recap |
| S9 | Recovery | Rollback healthy in < 2 min | `rollback.yml` plus health check |
| S10 | Manual deployment steps | 0 | `scripts/demo-all.sh` |

---

## 8. Approved MVP scope — the 15-task freeze

### 8.1 In scope

| Ref | Capability |
|---|---|
| F1 | Donor registration with validation on blood group, phone and city |
| F2 | Donor listing and detail view |
| F3 | Donor update, including availability and last-donation date |
| F4 | Donor **search** by blood group, city and availability |
| F5 | Blood request creation (patient, group, units, hospital, city, urgency) |
| F6 | Request listing, detail view and search by status and group |
| F7 | **Donor matching engine** — compatibility, eligibility, availability, same-city first |
| F8 | **Role-based status workflow** with per-role permissions |
| F9 | **Summary dashboard** with shortage flags |
| F10 | Authentication and three roles |
| F11 | REST API mirroring F1–F9, plus `/actuator/health` |

### 8.2 Explicitly out of scope

SMS, email and WhatsApp notification · geolocation and distance ranking · blood **unit** inventory
with expiry · donation camp scheduling · donor self-registration with OTP · multi-blood-bank
tenancy · forecasting · a mobile application · HL7/FHIR integration · an external database cluster.

### 8.3 Scope approval

This scope is **approved and frozen** as of 29 September 2026. Anything discovered after this point
becomes a new backlog item for a future release rather than being absorbed into the MVP. That is
what makes the 15-task plan in Task 2 estimable.

---

## 9. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The system matches donors; it does not certify blood units | Serological screening, cross-matching and unit labelling are a different domain with different regulatory weight. Drawing the boundary at the start stopped the scope drifting into it |
| Success criteria were made measurable before any code was written | "Fast matching" cannot be verified. Every one of the ten criteria names the test or measurement that settles it |
| Ports were fixed in Task 1, not when they first caused trouble | Host 8080 was already occupied and macOS reserves 5000. Deciding on 9090 / 8090 / 5001 up front meant no later task had to be revised |

---

## 10. Evidence index

| File | Shows |
|---|---|
| [`docs/01-problem-definition.md`](01-problem-definition.md) | This document — the frozen scope of record |
| [`docs/backlog.md`](backlog.md) | The out-of-scope items carried as future enhancements FE-01 to FE-15 |

---

## 11. Task 1 Definition of Done

Scored against the twelve criteria in [`definition-of-done.md`](definition-of-done.md). This task
produced no code, so D2 to D7 and D10 do not apply.

| # | Criterion | Status |
|---|---|---|
| D8 | Task documentation written and committed | This document |
| D11 | Committed on `main` per the branching model | `task-01` |
| D12 | Backlog seeded with the out-of-scope items | `docs/backlog.md` |

**Outcome:** the real-time need is established from three verifiable facts about blood supply, the
problem statement is specific enough to be falsified, four user classes and eight stakeholders are
identified, eleven objectives and ten measurable success criteria are fixed, and the MVP is frozen
as F1–F11 with an explicit out-of-scope list. Every later task is estimable because this one
refused to leave the scope open.

---

# Task 2 — Agile Planning and DevOps Workflow

**Deliverable:** backlog, task board, sprint plan, Definition of Done and DevOps workflow diagram.


---

## 1. Agile approach used

A single-developer **Kanban-flavoured Scrum**. The 15 tasks of the subject task sheet are treated as
the sprint backlog of one 11-day release sprint (29 September – 9 October 2026), pulled through a
five-column board with a **work-in-progress limit of 1**.

| Ceremony | How it is done here | Evidence |
|---|---|---|
| Backlog refinement | Stories written with acceptance criteria before coding | [`backlog.md`](backlog.md) |
| Sprint planning | 15 tasks ordered, estimated and dated | §4 of this document |
| Daily stand-up | Daily task-start note | [`kanban.md`](kanban.md) flow log |
| Review / demo | Running application and pipeline at each tagged task | `task-01` … `task-15` |
| Retrospective | Lessons recorded per task and consolidated | [`REPORT.md`](REPORT.md), [`TROUBLESHOOTING.md`](TROUBLESHOOTING.md) |

---

## 2. Personas

| Persona | Name in stories | Goal |
|---|---|---|
| Blood bank technician | **Staff** (`STAFF`) | Register donors fast, find a match without phoning around |
| Medical officer | **Admin** (`ADMIN`) | Sign off outcomes, see supply against demand, hold staff accountable |
| Voluntary donor | **Donor** (`DONOR`) | Be contacted only when actually eligible |
| Platform operator | **Operator** | Deploy, upgrade and roll back with no manual steps |

---

## 3. User stories with acceptance criteria

Acceptance criteria are written Given / When / Then so they map one-to-one onto automated tests.
`[U]` unit test · `[S]` Selenium journey · `[A]` automation or health-check assertion.

### 3.1 Epic A — Donor registry

| Story | Points | Summary |
|---|---|---|
| US-01 | 3 | As Staff I want to register a donor with group, contact and city, so the donor can be found later |
| US-02 | 3 | As Staff I want to search donors by group, city and availability, so I know who to call now |
| US-03 | 2 | As Staff I want to update a donor and record their last donation, so eligibility is automatic |
| US-04 | 2 | As a Donor I want to see my profile and control my availability |

Representative criteria for US-02:

- AC-02.1 — Given donors in several groups, when I filter by `O-`, then only `O-` donors are listed. `[U][S]`
- AC-02.2 — Given a city filter combined with a group filter, then both apply together (logical AND). `[U]`
- AC-02.3 — Given the "available only" filter, then donors flagged unavailable are excluded. `[U]`
- AC-02.4 — Given no donor matches, then an explicit "no donors found" message is shown. `[S]`

### 3.2 Epic B — Requests and matching

| Story | Points | Summary |
|---|---|---|
| US-05 | 3 | As Staff I want to record a blood request, so it has an owner and a tracked state |
| US-06 | 8 | As Staff I want a ranked list of compatible, eligible donors — **the core of the project** |
| US-07 | 5 | As Admin I want only the right role to advance a request, so outcomes are accountable |

Representative criteria for US-06 and US-07:

- AC-06.1 — Given a `B+` request, then the shortlist contains only `B+`, `B−`, `O+`, `O−`. `[U]`
- AC-06.2 — Given all 64 recipient/donor pairs, then the verdict is correct for every one. `[U]`
- AC-06.3 — Given a compatible donor who donated 30 days ago, then that donor is **excluded**. `[U]`
- AC-06.7 — Given 500 donors, then the shortlist returns in under 2 seconds. `[A]`
- **AC-07.4** — Given a `NEW` request, when **Staff** attempts to fulfil it, then it is refused. `[U][S]`
- AC-07.5 — Given a `FULFILLED` or `REJECTED` request, then no further transition is accepted. `[U]`

### 3.3 Epic C — Visibility

| Story | Points | Summary |
|---|---|---|
| US-08 | 3 | As Admin I want a dashboard of supply against demand with shortage flags |
| US-09 | 3 | As Operator I want authentication with three roles, so US-07 is enforceable |

### 3.4 Epic D — DevOps capability (the assessed epic)

| Story | Points | Summary |
|---|---|---|
| US-10 | 3 | A reproducible Maven build producing a deployable WAR |
| US-11 | 5 | Jenkins builds and tests automatically on commit |
| US-12 | 5 | The pipeline is code, in a `Jenkinsfile`, with a parameterised environment |
| US-13 | 5 | Failing UI tests stop the deployment |
| US-14 | 5 | Versioned images in a registry, a fresh container deployed on success |
| US-15 | 5 | The environment as code, idempotent, health-checked and roll-back-able |

**Total: 64 story points across 15 stories.**

---

## 4. The 15-task Kanban / Scrum plan

Columns: **Backlog → Ready → In Progress → Review/Verify → Done**, WIP limit 1 in *In Progress*.

| Task | Deliverable | Stories | Pts | Date | Exit |
|---|---|---|---|---|---|
| 1 | Problem definition and frozen scope | — | 2 | 29 Sep | `task-01` |
| 2 | Agile plan, backlog, board, DoD, diagrams | — | 3 | 29 Sep | `task-02` |
| 3 | SRS, architecture, data model, local setup | US-10 | 5 | 30 Sep | `task-03` |
| 4 | Repository, README, templates, branch policy | US-10 | 3 | 30 Sep | `task-04` |
| 5 | Donor registry and matching engine | US-01…04, 06 | 13 | 1 Oct | `task-05` |
| 6 | MVP completion, conflict drill | US-05, 07, 08, 09 | 14 | 2 Oct | `task-06`, `v1.0.0` |
| 7 | Jenkins install and CI job | US-11 | 5 | 3 Oct | `task-07` |
| 8 | `Jenkinsfile`, Tomcat deploy, parameters | US-12 | 5 | 4 Oct | `task-08` |
| 9 | Selenium journeys through Maven | US-13 | 5 | 5 Oct | `task-09` |
| 10 | Quality gate, defect, correction, rerun | US-13 | 5 | 6 Oct | `task-10` |
| 11 | Dockerfile and container lifecycle | US-14 | 4 | 7 Oct | `task-11` |
| 12 | Versioned image → registry → container | US-14 | 5 | 7 Oct | `task-12`, `v1.1.0` |
| 13 | Ansible specification and playbook | US-15 | 4 | 8 Oct | `task-13` |
| 14 | Provisioning, idempotency, rollback | US-15 | 5 | 8 Oct | `task-14`, `v1.2.0` |
| 15 | End-to-end release, report, viva prep | all | 4 | 9 Oct | `task-15`, `v1.3.0` |

**Capacity.** 82 points across 11 calendar days, a planned velocity of about 7.5 points a day. Tasks
5 and 6 are the two largest items and are placed early, so the remaining tasks build on a stable
application.

### 4.1 Risk register

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Port clash with services already on the host | High | Medium | Ports fixed up front and asserted by `scripts/preflight.sh` |
| Selenium / chromedriver version drift | Medium | High | Selenium Manager resolves the driver for the installed Chrome |
| No network during the demonstration | Medium | High | Local registry, vendored CSS, all evidence pre-captured |
| Slow image rebuilds blocking the pipeline | Medium | Low | Multi-stage build with a cached dependency layer |
| Scope creep diluting the DevOps work | Medium | High | Scope frozen in Task 1; new ideas go to the backlog |

---

## 5. Definition of Done

Twelve numbered criteria, D1 to D12, so that every task closes with the same bar applied and a
reader can see which criteria a given task was accountable for. The authoritative version, with the
detailed eight-area checklist, is [`definition-of-done.md`](definition-of-done.md).

Three of them are worth drawing out:

- **D6 — the Selenium quality gate passed.** Not "the tests ran", but that a red gate would have
  stopped the deployment. Task 10 demonstrates this with a real red build.
- **D9 — evidence is captured.** A task whose result exists only as a memory of a terminal is not
  demonstrable in a viva.
- **D11 — merged through the branching model.** Including the `--no-ff` requirement, so the history
  records the shape of the work and not just its result.

---

## 6. DevOps workflow, development to operations

```
 PLAN      Backlog item, branch named feature/<slug>
   ↓
 CODE      Local edit, unit tests red → green, conventional commit
   ↓
 BUILD     mvn clean package → bloodbank.war
   ↓
 TEST      Surefire unit tests → Failsafe Selenium journeys  ← THE GATE
   ↓
 RELEASE   Versioned Docker image, tagged with the build number, pushed
   ↓
 DEPLOY    Fresh container from the new image, named volume re-attached
   ↓
 OPERATE   Ansible provisions and configures the node
   ↓
 MONITOR   /actuator/health, container health check, Jenkins trend
   ↓
 FEEDBACK  Defect or idea → new backlog item → PLAN
```

Every arrow is automated except `PLAN → CODE` and `MONITOR → FEEDBACK`. The gate between **TEST**
and **RELEASE** is the single most important edge in the diagram: when it is red, nothing
downstream executes.

### 6.1 Branching and promotion model

```
feature/<slug> ──commits──► PR + review ──merge --no-ff──► develop ──merge──► main ──tag──► vX.Y.Z
```

`main` is always releasable · `develop` integrates features · one short-lived `feature/*` branch per
story. Rules are in [`CONTRIBUTING.md`](../CONTRIBUTING.md).

### 6.2 Diagrams

Six Mermaid diagrams are in [`diagrams/devops-lifecycle.md`](diagrams/devops-lifecycle.md): the
implemented lifecycle with the quality gate marked, tool-chain ownership, the branching model, the
end-to-end pipeline sequence, the request state machine, and the environment topology.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| A work-in-progress limit of 1 | One task finished, committed and tagged before the next began. That is what made "one commit per task" a natural outcome rather than something reconstructed afterwards — the git history reads as the project plan |
| Acceptance criteria written Given/When/Then and tagged by test type | Nearly every criterion became a JUnit assertion or a Selenium step with no rewriting. The ones that could not be automated were visible as such from the start |
| The Definition of Done includes "demonstrable live in under three minutes, with a fallback" | The subject is assessed by demonstration. A task that works but cannot be shown is not done |
| The two largest tasks were scheduled first | Tasks 5 and 6 carry 27 of the 64 story points. Putting them early means the nine automation tasks build on a stable application rather than a moving one |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`docs/02-agile-planning.md`](02-agile-planning.md) | This document — stories, criteria, sprint plan, risks |
| [`docs/backlog.md`](backlog.md) | MoSCoW backlog, defect register, 15 future enhancements |
| [`docs/kanban.md`](kanban.md) | Board, daily flow log, flow metrics, retrospective |
| [`docs/definition-of-done.md`](definition-of-done.md) | D1–D12 and the detailed eight-area checklist |
| [`docs/diagrams/devops-lifecycle.md`](diagrams/devops-lifecycle.md) | Six Mermaid diagrams |

---

## 9. Task 2 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D8 | Task documentation written and committed | This document and four companions |
| D11 | Committed on `main` per the branching model | `task-02` |
| D12 | Backlog created and populated | `docs/backlog.md` |

**Outcome:** 15 user stories worth 64 points, each with Given/When/Then criteria tagged by the kind
of test that would prove it; a prioritised backlog with 15 deferred enhancements; a 15-task board
with a WIP limit of 1, per-task exit criteria and a five-entry risk register; a twelve-point
Definition of Done used by every later task; and six diagrams covering the lifecycle, tool chain,
branching, pipeline sequence, state machine and environment.

---

# Task 3 — Requirements, Architecture and Technology Setup

**Deliverable:** SRS summary, use-case diagram, architecture diagram, data model / API list and
working local setup.


---

## 1. Requirements

A full SRS in IEEE 830 structure is in [`SRS.md`](SRS.md): **26 functional** and **15
non-functional** requirements, each traced to the test that proves it.

### 1.1 The three requirements the project is built around

| ID | Requirement | Proved by |
|---|---|---|
| **FR-14** | Red-cell compatibility must be correct for all 64 recipient/donor pairs | `CompatibilityMatrixTest` (64 cases) |
| **FR-08** | A donor is eligible only when the last donation is **at least** 90 days ago | `DonorEligibilityTest` (89/90/91-day boundary) |
| **NFR-07** | Authorisation is enforced server-side, not by hiding a control in the UI | `SecurityRoleTest` over the real filter chain |

### 1.2 The compatibility matrix behind FR-14

A recipient may receive red cells from the donor groups marked ✓.

| Recipient ↓ / Donor → | O− | O+ | B− | B+ | A− | A+ | AB− | AB+ |
|---|---|---|---|---|---|---|---|---|
| **O−**  | ✓ | | | | | | | |
| **O+**  | ✓ | ✓ | | | | | | |
| **B−**  | ✓ | | ✓ | | | | | |
| **B+**  | ✓ | ✓ | ✓ | ✓ | | | | |
| **A−**  | ✓ | | | | ✓ | | | |
| **A+**  | ✓ | ✓ | | | ✓ | ✓ | | |
| **AB−** | ✓ | | ✓ | | ✓ | | ✓ | |
| **AB+** | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

The matrix is **derived from two rules**, not typed out as 64 cells: a recipient accepts a donor
when the donor's ABO antigens are a subset of the recipient's, and an Rh-negative recipient accepts
only Rh-negative donors. O− is therefore the universal donor and AB+ the universal recipient, and
exactly **27 of the 64** ordered pairs are compatible — a figure the test also asserts, because a
matrix that is self-consistently wrong (Rh ignored) would give 36.

### 1.3 Non-functional requirements that shape the design

| ID | Requirement |
|---|---|
| NFR-01 | A shortlist over 500 donors completes in under 2 s |
| NFR-03 | The container restarts automatically on failure |
| NFR-04 | Data survives container replacement and image upgrade |
| NFR-05 | Rollback completes and passes a health check in under 2 minutes |
| NFR-07 | Authorisation is enforced in the service layer |
| NFR-10 | The same WAR runs via `java -jar`, in Tomcat, and in the image with no rebuild |
| NFR-13 | Commit to deployed container takes under 6 minutes with zero manual steps |

---

## 2. Architecture

Full views are in [`ARCHITECTURE.md`](ARCHITECTURE.md).

### 2.1 Layers

```
web      DonorController · RequestController · DashboardController · AuthController
         web/api: DonorApi · RequestApi · DashboardApi · GlobalExceptionHandler
   ↓
service  DonorService · MatchingService · RequestService · DashboardService
   ↓
domain   Donor · BloodRequest · BloodGroup · RequestStatus · Urgency
   ↓
repo     DonorRepository · BloodRequestRepository   (Spring Data JPA)
   ↓
infra    H2 on volume bloodbank-data · SecurityConfig · DataSeeder · Actuator
```

**The rule that makes this testable:** every business rule — compatibility, eligibility, ranking,
transition legality, role permission — lives in the **service layer** or on a **domain enum**.
Controllers bind input, call one service method and choose a view. That is why 90 % of the
requirements are provable by fast JUnit tests, and Selenium only has to cover five journeys.

### 2.2 The matching algorithm

```
input : request R (group g, city c), today's date T
output: donors ranked best-first

1  candidates ← all donors
2  keep d where  g.canReceiveFrom(d.bloodGroup)           -- compatibility  (FR-14)
3  keep d where  d.available = true                       -- availability   (FR-13)
4  keep d where  d.lastDonationDate is null
                 or daysBetween(d.lastDonationDate, T) >= 90    -- eligibility (FR-08)
5  sort by
     (a) d.bloodGroup = g        descending   -- exact group first
     (b) d.city = c              descending   -- same city first
     (c) days since last donation descending  -- fairest rotation
     (d) d.fullName              ascending    -- deterministic for tests
6  return
```

| Rule | Why it is there |
|---|---|
| 5(a) exact group first | A universal O− donor should not be spent on an O+ patient while an O+ donor is available — O− is the scarcest and most widely demanded stock |
| 5(b) same city | Travel time dominates time-to-match |
| 5(c) longest rested | Rotates the load across the register instead of repeatedly calling the same willing people |
| 5(d) name | Purely so the ordering is deterministic and the unit tests cannot be flaky |

Complexity is O(n log n) in the number of donors, so NFR-01's two-second budget is met with a wide
margin — measured by `MatchingPerformanceTest`.

### 2.3 Use-case model

Twelve use cases across four actors, with `include` and `extend` relations, in
[`ARCHITECTURE.md`](ARCHITECTURE.md) §6. The relation worth noting: **UC7 Fulfil request *extends*
UC3 Update donor**, because confirming a donation also stamps the donor's donation date, which is
what starts that donor's 90-day ineligibility window (FR-20).

---

## 3. Data model and API

Full specification in [`API.md`](API.md): ER model, three behaviour-carrying enumerations, 20 UI
endpoints, 17 REST endpoints, JSON representations and a status-code contract.

### 3.1 Entities

| Entity | Key fields |
|---|---|
| `Donor` | name, blood group, 10-digit phone, email, city, last donation date, available |
| `BloodRequest` | patient, blood group, units (1–10), hospital, city, urgency, status, matched donor, rejection reason, audit fields |

**Eligibility is deliberately not a stored column.** Storing it would require a nightly job and
would be wrong the moment the clock passed midnight, so it is computed on read.

### 3.2 Enumerations that carry behaviour

| Enum | Behaviour |
|---|---|
| `BloodGroup` | `canReceiveFrom(BloodGroup)` implements the 8 × 8 matrix |
| `RequestStatus` | `allowedNext()` returns the legal successors; `isTerminal()` for the two closed states |
| `Urgency` | `getWeight()` for display ordering — it deliberately does not affect matching |

### 3.3 Status-code contract

| Code | Meaning here |
|---|---|
| 400 | Request body or parameters failed validation |
| 401 | Not authenticated (API chain) |
| **403** | Authenticated, but the role is not permitted — proves authorisation is server-side |
| 404 | Unknown identifier |
| **409** | Legal request, illegal state — proves the state machine is enforced in the service layer |
| 422 | Well-formed input that breaks a business rule (an unusable donor) |

---

## 4. Technology selections

Each answers a requirement in the task sheet. Full comparison, including rejected alternatives, in
[`ARCHITECTURE.md`](ARCHITECTURE.md) §1.

| Decision point | Selection | Why |
|---|---|---|
| Programming stack | Java 21 + Spring Boot 3.3 + Thymeleaf | One artefact serves UI and API; Actuator gives the health endpoint the pipeline, container and Ansible all consume |
| Build tool | **Maven 3.10** | Named in the task sheet; Surefire/Failsafe split keeps fast tests separate from browser tests |
| Database | **H2 in file mode**, on a Docker volume | Real SQL with no external service, so the pipeline runs on one laptop and offline |
| Deployment target | **Tomcat 10.1** | Nginx cannot host a WAR; Tomcat 10.1 is the Jakarta EE 10 container matching Spring Boot 3 |
| Packaging | **WAR**, `bloodbank.war` | Archivable CI artefact *and* a Tomcat webapp, while remaining executable via `java -jar` |
| Local setup | `scripts/env.sh` + `scripts/preflight.sh` | Every host-specific value in one file that all later scripts, the Jenkinsfile and the playbooks read |

### 4.1 Rejected alternatives

| Rejected | Why |
|---|---|
| Spring Boot fat JAR only | Would not satisfy "Tomcat deployment target" |
| PostgreSQL container | A second container, health check and network, teaching nothing the pipeline does not already demonstrate. Recorded as FE-10 |
| GitHub Actions | The subject specifies Jenkins, and Actions would not run offline |
| Kubernetes | Out of syllabus; Docker and Ansible are what Tasks 11–14 ask for |

---

## 5. Working local setup

### 5.1 Prerequisites verified on this host

| Tool | Version | Installed with |
|---|---|---|
| JDK 21 | 21.0.12 | `brew install openjdk@21` |
| Maven | 3.10.0 | `brew install maven` |
| Docker Engine / Compose | 28.1.1 / v2.35.1 | Docker Desktop |
| Ansible | core 2.21.5 | `pipx install --include-deps ansible` |
| Jenkins LTS | 2.568.2 | `brew install jenkins-lts` |
| Google Chrome | installed | Driven headless by Selenium Manager |

### 5.2 Port map, fixed now because every later task depends on it

| Service | Host port | Note |
|---|---|---|
| Jenkins | **8090** | 8080 is already occupied on this host |
| Application container | **9090** → 8080 | Tomcat listens on 8080 inside the container |
| Private registry | **5001** | macOS reserves 5000 for AirPlay Receiver |

### 5.3 One-time setup and verification

```bash
git clone <repository-url> devops_project && cd devops_project
source scripts/env.sh        # pins JAVA_HOME to JDK 21, exports every port and name
bash scripts/preflight.sh    # verifies toolchain, daemon, ports, images and layout
```

Verified output on this host:

```
==> 1. Toolchain
  ok   Java 21 — JAVA_HOME=/opt/homebrew/opt/openjdk@21
  ok   Maven — Apache Maven 3.10.0
  ok   Git — git version 2.46.0
  ok   Docker — Docker version 28.1.1
  ok   Ansible — ansible [core 2.21.5]
  ok   Docker daemon is running
  ok   Google Chrome present (Selenium Manager will resolve chromedriver)
  ok   Ansible collection community.docker installed
==> 2. Ports
  ok   port 8090 free      ok   port 9090 free      ok   port 5001 free
==> 3. Docker base images
  ok   image present: tomcat:10.1-jdk21-temurin
  ok   image present: registry:2
PREFLIGHT PASSED
```

`preflight.sh` is written to be run repeatedly: it is the first thing to run on a new machine and
the first thing to run before the practical demonstration.

### 5.4 The development loop

```bash
mvn clean verify                      # unit tests + Selenium journeys
mvn spring-boot:run                   # fast loop, embedded Tomcat
java -jar target/bloodbank.war        # the same artefact, executable
bash scripts/docker-lifecycle.sh      # the same artefact, in Tomcat in a container
```

That the **same** `bloodbank.war` runs three ways is the practical proof of NFR-10.

---

## 6. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Java 21 + Spring Boot 3.3 packaged as a WAR, deployed to Tomcat 10.1 | The task sheet names Maven and Tomcat. A Spring Boot WAR satisfies both and is still executable, so one artefact runs three ways with no second build profile |
| H2 in file mode on a Docker volume, not an external database | Real SQL persistence with no external service, so the pipeline is reproducible on one laptop and offline. The volume is what later proves data survives container replacement |
| Every environment-specific value lives in `scripts/env.sh` | Because they are read from the environment with defaults, making the Jenkins pipeline parameterisable in Task 8 needed no second code path |
| Deployed as `ROOT.war` so the context path is `/` everywhere | No URL in the Selenium suite, the health check or the documentation changes between local, container and CI |
| Eligibility computed on read, never stored | A stored flag would need a nightly job and would be wrong the moment the clock passed midnight |

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`docs/SRS.md`](SRS.md) | 26 FR + 15 NFR, the compatibility matrix, traceability matrix |
| [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) | Layered and deployment views, matching algorithm, 12 use cases |
| [`docs/API.md`](API.md) | ER model, 37 endpoints, payloads, status-code contract |
| [`scripts/env.sh`](../scripts/env.sh) | Every host-specific value, defined once |
| [`scripts/preflight.sh`](../scripts/preflight.sh) | Toolchain, daemon, port and image verification |

---

## 8. Task 3 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D7 | No hard-coded host, port, path or credential | All centralised in `scripts/env.sh` |
| D8 | Task documentation written and committed | This document, SRS, architecture, API |
| D11 | Committed on `main` per the branching model | `task-03` |
| D12 | Backlog updated with the deferred alternatives | FE-10, FE-12 recorded |

**Outcome:** the system is specified before it is built — 26 functional and 15 non-functional
requirements each traced to a test, a compatibility matrix derived from two rules rather than typed
out, an architecture that puts every business rule where a fast test can reach it, a complete API
and status-code contract, and a local setup that verifies itself with one command.

---

# Task 4 — Git and GitHub Repository Initialization

**Deliverable:** repository URL, README, initial commits, issues and branch policy.


---

## 1. Repository initialisation

```bash
git init -b main
git config user.name  "AYUSH MAYEKAR"
git config user.email "ayushmayekar1333@gmail.com"
```

The default branch is `main`, and identity is set **per repository** rather than globally, so the
authorship of this submission is unambiguous.

The repository is local at this stage by design — it is published to GitHub in one push once the
history is complete, which keeps it clean of "fix remote" noise. The push commands are in the
[README](../README.md).

### 1.1 Two commits in this task, not one

The task sheet asks for the skeleton to be committed "using meaningful commit messages" — plural.
The work splits along its natural seam:

| Commit | Contains | Rationale |
|---|---|---|
| 1 | README, `.gitignore`, `.gitattributes`, `.editorconfig`, `CONTRIBUTING.md`, `.github/` | Repository **policy and documentation**: how the project is to be worked on |
| 2 (tagged `task-04`) | `pom.xml`, `src/`, `application.yml`, security skeleton, landing page, smoke test | The **buildable skeleton**: the first code that produces an artefact |

Mixing the two would have produced a commit that could not be described in one sentence, which is
the practical test in `CONTRIBUTING.md` §2.

---

## 2. Ignore rules, and what each group protects against

| Group | Patterns | Why it matters *here* |
|---|---|---|
| Build output | `target/`, `*.war`, `*.class` | `bloodbank.war` is 56 MB; committing it would make the repository unusable and defeat the point of a reproducible build |
| **Database files** | `*.mv.db`, `*.trace.db`, `data/` | The H2 file is created the first time the application runs locally. Committing it would version-control donor data and conflict on every run |
| Secrets | `*.pem`, `*.jks`, `.env`, `ansible/vault_pass*` | Registry credentials come from Jenkins credential bindings, never from a file in the repository |
| Test output | `surefire-reports/`, `screenshots/` | Regenerated by every build; only the curated copies under `evidence/` are committed, because those are the submission's proof |
| IDE / OS | `.idea/`, `.vscode/`, `.DS_Store` | Machine-specific noise |

`.gitattributes` normalises line endings to LF for all text files, so the repository builds
identically on macOS, a Linux CI agent and Windows. `.editorconfig` fixes indentation per file type.

A later correction is worth recording: the broad `*.log` exclusion silently swallowed the Jenkins
console logs under `evidence/`, which are the submission's proof that the pipeline ran. Negation
patterns now re-include `evidence/**/*.log`, `*.txt` and `*.xml`, while the broad rule stays,
because its purpose — never committing a transient local log — is still right everywhere else.

---

## 3. Folder structure

```
devops_project/
├── pom.xml                      packaging = war, finalName = bloodbank
├── README.md  CONTRIBUTING.md  .gitignore  .gitattributes  .editorconfig
├── .github/ISSUE_TEMPLATE/ + PULL_REQUEST_TEMPLATE.md
├── src/main/java/com/bloodbank/
│   ├── BloodBankApplication.java     @SpringBootApplication + SpringBootServletInitializer
│   ├── config/  domain/  repo/  service/  web/
├── src/main/resources/              application.yml, templates/, static/
├── src/test/java/                   unit tests (Surefire)
├── src/it/java/                     Selenium journeys (Failsafe) — created now, filled in Task 9
├── scripts/                         env.sh, preflight.sh
└── docs/                            task documents, SRS, architecture, API
```

The package layout mirrors the layered architecture from Task 3, so "business rules live in the
service layer" has a structural home from the first commit rather than being retrofitted.

---

## 4. Branch naming rules

```
<type>/<short-kebab-case-description>        types: feature | fix | chore | docs | hotfix
```

| Accepted | Rejected | Why rejected |
|---|---|---|
| `feature/donor-registry` | `Ayush-work` | No type; says nothing about the change |
| `feature/request-workflow` | `feature/Donor_Registry` | Upper case and underscore |
| `fix/bug-01-eligibility-boundary` | `new-stuff`, `temp`, `test2` | Not traceable to a story or defect |
| `chore/jenkins-pipeline` | `feature/12` | An issue number alone carries no meaning in `git log` |

Full rules, the commit-message convention and the nine-point review checklist are in
[`CONTRIBUTING.md`](../CONTRIBUTING.md).

---

## 5. Issue and pull-request templates

| Template | Purpose | Notable requirement |
|---|---|---|
| `task.md` | One of the 15 subject tasks | Copies the task sheet's *Deliverable* line verbatim, plus an evidence list |
| `feature_request.md` | A user story | Acceptance criteria must be Given/When/Then and tagged `[U]`/`[S]`/`[A]` |
| `bug_report.md` | A defect | Requires naming the **requirement violated**, and will not accept a fix without a test that would have caught it |
| `config.yml` | — | Disables blank issues, so every issue goes through a template |
| `PULL_REQUEST_TEMPLATE.md` | Every PR | Requires a table mapping each acceptance criterion to the test that proves it, and ends with the `--no-ff` merge command so the merge style cannot drift |

---

## 6. The application skeleton, and why it is this small

The skeleton contains **no domain logic at all**. Its only job is to prove, before any feature work
begins, that the artefact builds and serves traffic in all three run modes.

| Element | Established now because |
|---|---|
| `packaging = war`, `finalName = bloodbank` | The Jenkins job, the `Dockerfile` and the Ansible playbook all refer to `target/bloodbank.war`; fixing the name now means no later task has to change |
| `SpringBootServletInitializer` | Makes the WAR runnable three ways with no profile switching (NFR-10) |
| `application.yml` with environment-variable overrides | Satisfies Task 8's parameterised-environment requirement structurally, rather than bolting it on later |
| `/actuator/health` unauthenticated | The container `HEALTHCHECK`, the Ansible check and the Jenkins post-deploy check all need it without credentials (FR-26) |
| `context-path: /` | Deployed as `ROOT.war`, so every URL is identical in every environment |
| A three-assertion smoke test | Context loads, landing page identifies the project, health reports `UP`. If this fails, nothing downstream is worth debugging |
| `src/it/java` present but empty | The Selenium source root exists from the start, so Task 9 adds tests rather than restructuring the build |

### 6.1 Verified build and run

```
$ source scripts/env.sh && mvn clean package
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ ls -l target/bloodbank.war
-rw-r--r--  1 ayushmayekar  staff  56491811  target/bloodbank.war

$ java -jar target/bloodbank.war &
$ curl -s http://localhost:9090/actuator/health
{"status":"UP","groups":["liveness","readiness"]}

$ curl -s http://localhost:9090/ | grep 23102A0018
      <div><dt>Roll number</dt><dd>23102A0018</dd></div>
```

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Two commits for this task | The task sheet asks for "meaningful commit messages" — plural. Policy and skeleton are two things, and a commit covering both could not be described in one sentence |
| The skeleton contains no domain logic | Debugging a build problem and a business-logic problem at the same time is what makes later tasks slow |
| `/actuator/health` left unauthenticated from the first commit | Establishing it early avoided retrofitting it under time pressure when the container probe needed it |
| `finalName` fixed as `bloodbank` in the first `pom.xml` | Three later artefacts refer to that path; naming it once means none of them has to change |
| Identity set per repository, not globally | The authorship of this submission is then unambiguous and does not depend on a machine-wide setting |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`README.md`](../README.md) | Purpose, stack, quick start, task index, port map, push instructions |
| [`CONTRIBUTING.md`](../CONTRIBUTING.md) | Branch model, naming rules, commit convention, review checklist |
| [`.github/`](../.github) | Four issue templates and the pull-request template |
| [`.gitignore`](../.gitignore) | Ignore rules with the reasoning per group |
| `git show --stat task-04` | The skeleton commit |

---

## 9. Task 4 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D2 | Smoke test covering context, landing page and health | 3 tests |
| D3 | `mvn clean package` green from a clean target | `BUILD SUCCESS` |
| D7 | No hard-coded host, port, path or credential | `application.yml` with env overrides |
| D8 | Task documentation written and committed | This document |
| D11 | Two commits on `main` per the branching model | `task-04` |
| D12 | Backlog updated | US-10 In Progress |

**Outcome:** a repository with its working agreement written down before any feature work — branch
naming with accepted and rejected examples, a commit convention, a review checklist and templates
that refuse an incomplete issue — and a buildable WAR skeleton that produces a 56 MB artefact and
serves `{"status":"UP"}` in all three run modes.

---

# Task 5 — Feature Development with Branching

**Deliverable:** working feature 1, feature branch, pull request, review comments and merge
evidence.


---

## 1. The feature delivered

The first core workflow: register a donor, search the registry, and produce the ranked list of
donors who can actually be called for a patient of a given blood group.

| Story | Delivered |
|---|---|
| US-01 | Donor registration with validated name, blood group, 10-digit phone, email and city |
| US-02 | Search by blood group, city and availability, filters combined with logical **AND** |
| US-03 | Donor update, including availability and last-donation date |
| US-06 | **The matching engine** — the project's actual domain logic |

### 1.1 The matching engine

A donor matches when three independent predicates all hold:

| Predicate | Rule | Where it lives |
|---|---|---|
| Compatible | The recipient's group can receive the donor's red cells | `BloodGroup.canReceiveFrom` |
| Available | The donor's availability flag is set | `Donor.isCallable` |
| Eligible | At least 90 days since the last donation | `Donor.isEligible` |

Matches are then ranked: **exact group → same city → longest rested → name**.

---

## 2. Git operations, in the order they were used

```bash
# branch from the integration branch, never from main
git checkout develop
git checkout -b feature/donor-registry

# ---- commit 1: domain model -------------------------------------------------
git add src/main/java/com/bloodbank/domain src/main/java/com/bloodbank/repo \
        src/test/java/com/bloodbank/domain
git status                          # confirm nothing unintended is staged
git commit                          # 'feat: add donor domain model with blood group compatibility'

# ---- commit 2: matching engine ---------------------------------------------
git add src/main/java/com/bloodbank/service src/main/java/com/bloodbank/config/ClockConfig.java
git commit                          # 'feat: add donor matching engine with ranked shortlist'

# ---- commit 3: screens, API, seed data -------------------------------------
git add -A
git commit                          # 'feat: add donor registry screens, search and match preview'
git diff --stat HEAD~1              # review own change before asking for review

# ---- commit 4: the pull-request record -------------------------------------
git commit                          # 'docs: record PR-01 description and review'

# ---- review, then merge preserving the feature boundary --------------------
git log --oneline develop..feature/donor-registry     # exactly what the PR contains
git checkout develop
git merge --no-ff feature/donor-registry -m "Merge PR-01: donor registry and matching engine"
git tag -a task-05 -m "Task 5 - Feature Development with Branching"
git branch -d feature/donor-registry
```

### 2.1 Why `--no-ff`

Without it, Git would fast-forward `develop` to the tip of the feature branch and **the branch would
vanish from history**: `git log` would show four unrelated-looking commits with no record that they
formed one reviewed unit. `--no-ff` forces a merge commit, so:

- `git log --graph` permanently shows where the feature started and ended, and
- `git revert -m 1 <merge>` undoes the whole feature as one operation.

Both properties are why the pull-request template mandates it.

---

## 3. Merge evidence

```
$ git log --graph --oneline -12
*   7f3c1a9 (tag: task-05, develop) Merge PR-01: donor registry and matching engine
|\
| * e3d713c docs: record PR-01 description and review for the donor registry
| * 8021da1 feat: add donor registry screens, search and match preview
| * 6b8c055 feat: add donor matching engine with ranked shortlist
| * 39e120c feat: add donor domain model with blood group compatibility
|/
* 61f45b7 (tag: task-04) Task 4: add buildable Spring Boot WAR skeleton
* d4a9003 chore: initialise repository with README, ignore rules and policies
* 3dcaf48 (tag: task-03) Task 3: SRS, architecture, data model, API list and local setup
```

The diamond is the permanent record of the feature boundary.

---

## 4. Pull request and review

The full record — description, acceptance-criteria-to-test table, eight design decisions and six
review comments with responses — is in [`pr/PR-01-donor-registry.md`](pr/PR-01-donor-registry.md).

### 4.1 Review comments that produced code changes

| # | Problem | Fix |
|---|---|---|
| **R2** | A duplicate phone number returned HTTP **500** from the API | `DuplicatePhoneException` mapped to **409**, `DonorNotFoundException` to **404**. A 500 for a foreseeable conflict is a contract violation, not just an untidy message |
| **R3** | `/match` returned 500: `Property or field 'rhPositive' cannot be found on null` | Thymeleaf attribute precedence — `th:replace` outranks `th:each`, so both on one element ran the fragment before the loop variable existed. `th:each` moved to an outer element |

### 4.2 Review comments accepted as deferred

| # | Comment | Response |
|---|---|---|
| R1 | Eligibility is filtered in Java, not SQL — will it scale? | Intentional. Eligibility depends on today's date, so a SQL predicate would be wrong at midnight. The repository narrows to compatible groups in SQL — the selective filter — and the service applies the date rule. Measured well inside NFR-01's budget |
| R4 | Anyone can open `/donors`; there is no authentication | Accepted and out of scope here. Authentication is US-09 and arrives with the workflow in Task 6, because roles are only meaningful once there are transitions to restrict |
| R6 | `findMatches` is called twice when the dashboard renders | Acknowledged. The correctness benefit — a dashboard that cannot disagree with the shortlist — is worth more than the saving at MVP data volume |

---

## 5. Test results

```
Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Suite | Tests | Covers |
|---|---|---|
| `CompatibilityMatrixTest` | 47 | All 64 pairs against an independently written reference matrix, the 27-pair total, universal donor and recipient, both encoded rules, input parsing |
| `DonorEligibilityTest` | 15 | The 90-day boundary at 0, 1, 45, **89, 90**, 91, 120 and 400 days; never-donated; availability interaction |
| `MatchingServiceTest` | 21 | Each predicate separately, each ranking rule separately, the full priority order, empty and all-ineligible registries, 500-donor performance |
| `DonorServiceTest` | 22 | Search filter combinations over a real in-memory database, create, duplicate phone, update, unknown id |
| `BloodBankApplicationTest` | 3 | Context loads, landing page, open health endpoint |

### 5.1 Verified shortlist

Expected result for a **B+** patient in **Thane** against the seeded registry:

| Rank | Donor | Group | City | Why |
|---|---|---|---|---|
| 1 | Sneha Patil | B+ | Thane | exact group **and** same city |
| 2 | Vikram Shinde | B+ | Pune | exact group |
| 3 | Rohit Deshmukh | O− | Thane | compatible, same city |
| 4 | Tanmay Pawar | O+ | Thane | compatible, same city, rested less than Rohit |
| 5 | Imran Shaikh | O+ | Pune | compatible, never donated |

Arjun Mehta (A+) and Zoya Qureshi (AB+, donated 30 days ago) are correctly absent: the first is
incompatible, the second ineligible.

---

## 6. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Compatibility lives on the `BloodGroup` enum, not in a service | Compatibility is a property of blood groups, not of a workflow. Groups are modelled by the antigens they carry, so the 27 compatible pairs fall out of two rules instead of a hand-typed 64-cell table that could be mistyped |
| Eligibility is computed on read, never stored | A stored flag would need a nightly job and would be wrong the moment the clock passed midnight |
| `Clock` injected as a Spring bean rather than calling `LocalDate.now()` | The only way to assert the eligibility boundary at exactly 89 and 90 days without waiting for real time to pass |
| An exact group match ranks above a merely compatible one | A clinical-stewardship decision: a universal O− donor should not be spent on an O+ patient while an O+ donor is available |
| `countMatches` delegates to `findMatches` | Two independent supply calculations is exactly how defect BUG-02 happened; one code path makes a disagreement impossible |
| Views carry `data-testid` attributes | Selenium then binds to stable hooks instead of CSS classes, so restyling cannot break the suite |

---

## 7. Evidence index

| File | Shows |
|---|---|
| [`docs/pr/PR-01-donor-registry.md`](pr/PR-01-donor-registry.md) | PR description, criteria-to-test table, eight decisions, six review comments |
| `git log --graph --oneline -12` | The `--no-ff` merge diamond |
| `target/surefire-reports/` | 106 tests, 0 failures |

---

## 8. Task 5 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Every acceptance criterion met and demonstrable | AC table in PR-01 |
| D2 | Every business rule has a passing test, including boundaries | 106 tests; 89/90/91-day boundary |
| D3 | `mvn clean verify` green from a clean target | `BUILD SUCCESS` |
| D4 | Business rules in the service layer or a domain enum | `BloodGroup`, `MatchingService` |
| D7 | No hard-coded host, port, path or credential | Verified in review |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | PR record, merge graph, Surefire reports |
| D11 | Merged `--no-ff` through a reviewed pull request | PR-01 → `develop` |
| D12 | Backlog updated | US-01, US-02, US-03, US-06 Done |

**Outcome:** the first core workflow delivered on a feature branch across four commits, reviewed
through a recorded pull request where two comments produced code changes before the merge, and
merged `--no-ff` so the feature boundary is permanent in history. 106 tests green, including all 64
compatibility pairs asserted against an independently written reference matrix.

---

# Task 6 — MVP Completion and Git Collaboration

**Deliverable:** functional MVP, resolved merge conflict, tagged version and updated backlog.


---

## 1. The MVP, against what the task sheet asked for

| Task-sheet requirement | Delivered |
|---|---|
| **Create, view, update and search** records | Donor registration, list, detail, edit, availability toggle, donation recording; request recording, worklist, detail with audit trail; search on both with filters combined by AND |
| **Role-based status workflow** | `NEW → MATCHED → FULFILLED / REJECTED`; STAFF may match, only ADMIN may fulfil or reject; illegal transitions refused with 409, wrong roles with 403 |
| **Summary dashboard** | Registered and callable donors per group, requests per status, shortage flags |

Beyond the minimum, because the DevOps tasks need it: authentication with three roles, and a REST
API mirroring every screen so the health check, the Selenium fixtures and the viva commands assert
against the same rules.

---

## 2. The role-based workflow

```
            Staff or Admin            Admin
  NEW ─────────────────────► MATCHED ──────► FULFILLED   (terminal)
   │                            │
   └──────────── Admin ─────────┴──────────► REJECTED    (terminal)
```

### 2.1 Two guards per transition, checked in a fixed order

| Guard | Question | Decided by | Failure |
|---|---|---|---|
| 1 | Is this role permitted? | `RequestService` | **403** |
| 2 | Is the transition legal? | `RequestStatus.canMoveTo` | **409** |

Staff attempting `NEW → FULFILLED` violates both. **The role is checked first**, because telling
someone a transition is impossible when they were never allowed to attempt it is misleading.

### 2.2 Why the two closed states are terminal

A `FULFILLED` or `REJECTED` request is never reopened; it is superseded by a new request. That keeps
the audit trail of what actually happened intact.

---

## 3. Git collaboration operations

```bash
# second feature branch
git checkout develop && git checkout -b feature/request-workflow
git commit            # 'feat: add blood requests with role-guarded status workflow'

# meanwhile develop moves - a small fix branch, merged --no-ff
git checkout develop && git checkout -b fix/navbar-health-indicator
git commit            # 'fix: surface the health endpoint in the navigation'
git checkout develop && git merge --no-ff fix/navbar-health-indicator
git branch -d fix/navbar-health-indicator

# back on the feature branch
git checkout feature/request-workflow
git commit            # 'feat: add authentication, three roles, request screens and dashboard'

# bring develop in - this is where the conflict happens
git merge develop                    # CONFLICT in fragments/layout.html
# ... resolve, then:
mvn clean verify                     # 178 tests green BEFORE committing the resolution
git add src/main/resources/templates/fragments/layout.html
git commit                           # the merge commit records the resolution

# review, then promote
git checkout develop
git merge --no-ff feature/request-workflow -m "Merge PR-02: ..."
git tag -a task-06 && git branch -d feature/request-workflow
git checkout main && git merge --no-ff develop -m "Release v1.0.0: functional MVP"
git tag -a v1.0.0 -m "Functional MVP: donor registry, matching engine, role-based workflow, dashboard"
```

---

## 4. The merge conflict

### 4.1 How it arose

Both branches edited **the last `<li>` of the same navigation list**:

| Branch | Change |
|---|---|
| `develop` | An unconditional **Health** link with a status dot, so operations can reach `/actuator/health` in one click |
| `feature/request-workflow` | A role-conditional **My profile** link for `DONOR`, beside the new Dashboard and Requests links |

Git merged every other hunk in the file automatically. It could not merge this one, because both
sides inserted different content at the same position, and nothing in the diff tells Git whether one
replaces the other or both belong.

### 4.2 As Git reported it

```
$ git merge develop
Auto-merging src/main/resources/templates/fragments/layout.html
CONFLICT (content): Merge conflict in src/main/resources/templates/fragments/layout.html
Automatic merge failed; fix conflicts and then commit the result.

$ git status --short
M  src/main/resources/static/css/app.css              <- merged automatically, different hunks
UU src/main/resources/templates/fragments/layout.html <- both modified, unmerged
```

`UU` means **both modified**. `app.css` shows plain `M` because the two branches touched different
parts of it.

```html
<<<<<<< HEAD
      <li class="nav-item" sec:authorize="hasRole('DONOR')">
        <a class="nav-link" th:href="@{/me}" data-testid="nav-me">My profile</a>
=======
      <li class="nav-item">
        <a class="nav-link bb-nav-health" th:href="@{/actuator/health}" target="_blank"
           data-testid="nav-health"><span class="bb-health-dot"></span> Health</a>
>>>>>>> develop
      </li>
```

`<<<<<<< HEAD` to `=======` is **our** side — the branch being merged *into*. `=======` to
`>>>>>>> develop` is **their** side.

### 4.3 The resolution

**Both changes were kept.** They serve different people and neither replaces the other: the profile
link is for a donor, the health link is for whoever operates the deployment. The role-conditional
item comes first, the universal one last, and the reasoning is recorded as a comment in the template
so the next person to touch that list does not tidy one away.

**The judgement Git could not make:** should the Health link be restricted to STAFF and ADMIN? It
was left unconditional, because `/actuator/health` is deliberately unauthenticated for the container
probe and the Ansible check (FR-26) — hiding the link would protect nothing while removing useful
information from a donor.

### 4.4 Why it was resolved on the feature branch

Merging `develop` **into** the feature branch keeps the integration branch clean: if the resolution
turns out to be wrong, the feature branch is thrown away and `develop` is untouched. Resolving on
`develop` would put a possibly broken merge on the branch everything else builds from.

### 4.5 Why the full suite ran before committing the resolution

A conflict resolution is the one kind of change that no diff review can fully validate: **the result
exists in neither parent**, so neither side's tests were ever run against it. Committing an
unverified resolution is how a merge breaks a build that both branches passed. 178 tests were run on
the resolved working tree first, and only then committed.

---

## 5. Problems met and solved

Three real defects were found while verifying this task — all by **exercising the running
application over HTTP after the unit suite was already green**.

### 5.1 BUG-03 — the dashboard reported 8 callable donors with 1 registered

**Cause.** The total was the **sum of the per-group figures**, and those figures deliberately
overlap: a single O− donor is callable for all eight recipient groups, so it was counted eight
times.

**Fix.** The distinct number of people who could be phoned today is exactly the shortlist for
**AB+**, because AB+ is the universal recipient and therefore accepts every group. A regression test
asserts the total is strictly *less than* the sum whenever more than one group is represented.

### 5.2 BUG-04 — the request table was sometimes empty at startup

**Cause.** Two separate `ApplicationRunner` beans. Spring gives them **no guaranteed order**, so
`seedRequests` sometimes ran before `seedDonors`, saw an empty donor table, and skipped silently by
design.

**Fix.** One runner that calls the two seeding methods in order.

**Lesson.** "Sometimes" in a startup sequence almost always means ordering.

### 5.3 BUG-05 — the worklist showed MEDIUM above HIGH above CRITICAL

**Cause.** `urgency` is `@Enumerated(STRING)`, so `order by r.urgency desc` sorted the **stored
text**: `MEDIUM`, `HIGH`, `CRITICAL` — alphabetical, and exactly backwards for the two that matter.

**Why the tests missed it.** They asserted *which* requests came back and never in what order. The
ordering lived in SQL, so a JPA test over H2 agreed with production — both wrong in the same way.

**Fix.** Ordering moved to the service using `Urgency.getWeight()`, with the reason recorded on both
the query and the comparator so nobody optimises it back into SQL. A regression test asserts the
exact sequence.

These three are the concrete argument for the next two tasks: Task 9 automates the manual HTTP
checking that found them, and Task 10 makes it a gate that blocks deployment.

---

## 6. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Two separate guards per transition, role checked first | "Is it legal?" is the state machine (409); "may this role?" is authorisation (403). Checking the role first avoids a misleading message |
| Services take an `Actor` record, not `SecurityContextHolder` | The workflow rules become unit-testable with no mock authentication, and authorisation sits where the REST API cannot bypass it. Hiding a button is presentation, not security |
| Two security filter chains rather than one | `/api/**` is Basic and stateless, so an unauthenticated call returns 401 — something a script can act on. Everything else is form login, so a browser is redirected. One chain with Basic made every screen answer 401 with a pop-up |
| CSRF disabled on the API chain only | CSRF defends against a request riding on an **ambient session**. The API chain is stateless and credential-per-request, so there is none. The browser chain keeps it enabled |
| Fulfilling stamps the donation in the same transaction | A donation can never be recorded without the request advancing, nor the request advance without the donor becoming ineligible |
| The conflict was resolved on the feature branch | A wrong resolution then costs the feature branch, not the branch everything else builds from |

---

## 7. Release baseline

```
$ git checkout main && mvn clean verify
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ git tag -l
task-01 task-02 task-03 task-04 task-05 task-06 v1.0.0
```

| Tag | Marks |
|---|---|
| `task-06` | On `develop`, at the PR-02 merge |
| `v1.0.0` | On `main` — the release-ready source baseline |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-06-conflict/README.md`](../evidence/task-06-conflict/README.md) | How the conflict arose, how it was resolved, and the commands to verify it afterwards |
| [`evidence/task-06-conflict/layout.html.conflicted`](../evidence/task-06-conflict/layout.html.conflicted) | The file exactly as Git left it, markers preserved |
| [`docs/pr/PR-02-request-workflow.md`](pr/PR-02-request-workflow.md) | Eleven design decisions, seven review comments |
| `git log --merges --oneline` | Both `--no-ff` merges and the conflict resolution |

---

## 9. Task 6 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Every acceptance criterion met and demonstrable | AC table in PR-02 |
| D2 | Every business rule has a passing test | 178 tests; every transition and role combination |
| D3 | `mvn clean verify` green, including on the conflict resolution | `BUILD SUCCESS` |
| D4 | Business rules in the service layer or a domain enum | `RequestStatus`, `RequestService` |
| D7 | No hard-coded credential | Accounts moved to configuration, BCrypt-hashed |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-06-conflict/` |
| D11 | Merged `--no-ff`; conflict resolved on the feature branch | PR-02 → `develop` → `main` |
| D12 | Backlog updated with BUG-03, BUG-04, BUG-05 | `docs/backlog.md` |

**Outcome:** the functional MVP is complete and tagged `v1.0.0`, a genuine merge conflict was
created, resolved on the feature branch and verified with the full suite before committing, and
three real defects were found and fixed — every one of them by exercising the running application
after the unit tests were already green, which is the argument for everything in Tasks 9 and 10.

---

# Task 7 — Jenkins Installation and Continuous Integration Job

**Deliverable:** configured Jenkins job, successful build log, trigger evidence and archived
artefact.


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

---

# Task 8 — Pipeline as Code and Server Deployment

**Deliverable:** Jenkinsfile, successful pipeline run, deployed application URL/screenshot and
configuration evidence.


---

## 1. What pipeline-as-code actually changes

Task 7's freestyle job works, and its configuration lives in Jenkins' own database. That has three
consequences that only become visible when something goes wrong.

| | Freestyle job (Task 7) | `Jenkinsfile` (Task 8) |
|---|---|---|
| Where the definition lives | Jenkins' `config.xml`, inside `JENKINS_HOME` | The repository, beside the code it builds |
| Reviewed | Not at all, unless someone exports the XML | In the same pull request as the change |
| History of *why* a stage exists | None | `git log` on the `Jenkinsfile` |
| Restoring it on a fresh controller | Re-create it, or restore a backup | It is already in the clone |
| Changing it per branch | Impossible without copying the job | A branch's own `Jenkinsfile` governs its own build |

The practical demonstration of the last point: [`jenkins/pipeline-config.xml`](../jenkins/pipeline-config.xml)
is **33 lines** and only tells Jenkins where to find the `Jenkinsfile`, while
[`jenkins/ci-freestyle-config.xml`](../jenkins/ci-freestyle-config.xml) is **130 lines** describing
every step, publisher and wrapper in Jenkins' own XML dialect.

### 1.1 Why declarative and not scripted

- The **stage view** renders each stage separately — what makes the Task 10 quality gate legible at
  a glance rather than buried in a log.
- **`post` blocks run whether the stage passed or failed**, so `junit` is published in an `always`
  block: a red build is exactly when the test report is needed.
- **`parameters` are declared in the file**, so the parameterised environment this task requires is
  version-controlled rather than being UI state.

---

## 2. The stages

```
Checkout → Build → Unit Test → Package → Deploy to Tomcat → Health Check
```

| Stage | What it does | Why it is a separate stage |
|---|---|---|
| **Checkout** | `checkout scm`, then print the commit and the `java`/`mvn` versions | A build log that does not say which commit and which JDK it used is not evidence of anything |
| **Build** | `mvn clean compile` | A compile failure should be reported *as* a compile failure, not buried inside a test run |
| **Unit Test** | `mvn test`, publishing Surefire XML in an `always` block | Fast feedback, and the report survives a red build |
| **Package** | `mvn package -DskipTests`, archive `bloodbank.war` fingerprinted | `-DskipTests` because the tests already ran; re-running them would double the build time to prove nothing |
| **Deploy to Tomcat** | `scripts/deploy-tomcat.sh` with the parameters as environment variables | The deployment target the task sheet names |
| **Health Check** | `scripts/health-check.sh` | Deployment is not success; *behaving correctly* is |

### 2.1 The same script, by hand and in CI

The deploy and health-check stages call `scripts/deploy-tomcat.sh` and `scripts/health-check.sh` —
the same scripts a developer runs by hand. There is therefore exactly one deployment procedure and
one definition of "healthy", and no possibility of "but CI does it differently".

The parameters are passed as environment variables because `scripts/env.sh` already reads every one
of them from the environment with a default, so **no second code path was needed** to make the
pipeline parameterisable.

### 2.2 Why the health check is behavioural

A Tomcat that returns `200` on `/actuator/health` while the matching engine is broken has failed in
the only way that matters. `scripts/health-check.sh` asserts nine things through the API, including
the three that encode the project's real rules:

```
ok   no incompatible donor in a B+ shortlist => 0      (FR-14, the compatibility matrix)
ok   STAFF fulfilling a request is refused => 403      (NFR-07, server-side authorisation)
ok   ADMIN fulfilling a NEW request is refused => 409  (FR-18, the state machine)
```

---

## 3. Deployment to Tomcat

`scripts/deploy-tomcat.sh` runs the official `tomcat:10.1-jdk21-temurin` image with the freshly
built WAR mounted into `webapps/` as **`ROOT.war`**, so the context path is `/` in every environment
and no URL in the Selenium suite, the health check or the documentation changes between them.

| Choice | Reason |
|---|---|
| **Recreate, not restart** | The old container is removed and a new one created. An immutable replacement is what makes the Task 14 rollback a re-deploy rather than a rebuild |
| **Named volume `bloodbank-data`** | The H2 database outlives the container, which is what makes recreate safe |
| **`--restart unless-stopped`** | The reliability requirement NFR-03 |

Task 11 replaces the volume mount with a proper image that bakes the WAR in. This script stays,
because it is the fastest way to put a freshly built WAR in front of a browser without an image
rebuild.

---

## 4. The parameterised setting, and why it is verified rather than assumed

Four parameters, each a value that genuinely differs between environments, each with a default that
makes a plain "Build Now" behave exactly like the committed configuration.

| Parameter | Default | Why it is a parameter |
|---|---|---|
| `DEPLOY_PORT` | `9090` | 8080 is occupied on this host. Changing it deploys a second instance alongside the first |
| `TOMCAT_IMAGE` | `tomcat:10.1-jdk21-temurin` | Pinned to a minor version, not `:latest`, so a rebuild in six months produces the same runtime |
| `SPRING_PROFILE` | *(empty)* | `local` enables the H2 console and debug logging |
| `RUN_HEALTH_CHECK` | `true` | Lets a deployment be inspected by hand in a broken state without the pipeline tearing it down |

A parameter with no sensible default is a trap rather than a feature, which is why all four have one.

### 4.1 Verified: the parameters really do change the deployment

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

Both the published port and the active Spring profile changed. Build #5 restored the defaults. The
point is that the parameter was **observed reaching the running container**, not assumed from the
build log.

---

## 5. Pipeline runs

| Build | Result | Total | Package stage | Note |
|---|---|---|---|---|
| #1 | SUCCESS | 10 m 25 s | — | First run, cold dependency cache |
| #2 | SUCCESS | 10 m 25 s | **597.6 s** | Still slow — this is the problem |
| **#3** | SUCCESS | **34.0 s** | **4.1 s** | After the fix |
| #4 | SUCCESS | 32 s | 4 s | Parameterised run on port 9095 |
| #5 | SUCCESS | 33 s | 4 s | Defaults restored |

### 5.1 Build #3 stage view

```
pipeline build #3  status=SUCCESS  total=34.0s

STAGE                        STATUS         TIME
Checkout                     SUCCESS        1.1s
Build                        SUCCESS        3.3s
Unit Test                    SUCCESS       10.6s
Package                      SUCCESS        4.1s
Deploy to Tomcat             SUCCESS        8.4s
Health Check                 SUCCESS        1.4s
```

34 seconds against the 6-minute budget in NFR-13 — and the margin is what makes the deliberately red
build in Task 10 cheap to demonstrate live.

---

## 6. Problems met and solved

### 6.1 A 625-second pipeline

**Symptom.** Build #2 spent **597 of its 625 seconds** in the Package stage, downloading every
dependency from scratch.

**Cause.** `MAVEN_OPTS` pointed `maven.repo.local` at `.m2/repository` **inside the workspace**. A
cache that lives in the workspace is wiped with the workspace, so it is not a cache — it is a
guarantee of a cold download on every single build.

**Fix.** Removed, so builds use the shared `~/.m2`. That is also correct for a second reason: it is
the repository a developer running the same command by hand uses, which is what makes "the pipeline
builds what I build" true rather than approximately true.

**Result.** The Package stage went from 597 s to 4 s, and the pipeline from 625 s to 34 s.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Declarative rather than scripted pipeline | The stage view renders stages separately; `post` blocks publish reports on red builds; parameters in the file are version-controlled rather than UI state |
| The deploy and health-check stages call the same scripts a developer runs | Exactly one deployment procedure and one definition of "healthy" |
| The health check asserts behaviour, not a 200 response | A Tomcat answering while the matching engine is broken has failed in the only way that matters |
| Deployment recreates the container rather than restarting it | An immutable replacement is what makes the Task 14 rollback a re-deploy and not a rebuild |
| `-DskipTests` in the Package stage | The tests already ran in the previous stage; re-running them would double the build time to prove nothing |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`Jenkinsfile`](../Jenkinsfile) | Seven stages, four parameters, `post` blocks |
| [`evidence/task-08-pipeline/build-03-console.log`](../evidence/task-08-pipeline/build-03-console.log) | Full console of the 34-second green run |
| [`evidence/task-08-pipeline/stage-view.txt`](../evidence/task-08-pipeline/stage-view.txt) | Per-stage status and timings |
| [`evidence/task-08-pipeline/build-04-console-parameterised.log`](../evidence/task-08-pipeline/build-04-console-parameterised.log) | The run with `DEPLOY_PORT=9095` |
| [`evidence/task-08-pipeline/parameter-override-proof.txt`](../evidence/task-08-pipeline/parameter-override-proof.txt) | `docker ps` and `docker inspect` showing both parameters reached the container |

---

## 9. Task 8 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | `mvn clean verify` green in the pipeline | Unit Test stage, 178 tests |
| D5 | The Jenkins pipeline is green for the commit | Build #3 SUCCESS |
| D7 | No hard-coded host, port, path or credential | Four build parameters with defaults |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-08-pipeline/` |
| D10 | The deployed container answers its health check | Health Check stage, nine assertions |
| D11 | Committed on `main` per the branching model | `task-08` |
| D12 | Backlog updated | US-12 Done |

**Outcome:** the build moved out of Jenkins' database and into the repository as a declarative
seven-stage `Jenkinsfile` with four parameters, deploying to Tomcat and verifying behaviour rather
than liveness. Both job types are kept so the difference can be demonstrated: 33 lines of job XML
against 130. The pipeline runs in 34 seconds after a workspace-local Maven cache was removed, and a
parameterised run was observed reaching the container on port 9095 with the `local` profile active.

---

# Task 9 — Selenium Test Design and Local Execution

**Deliverable:** test plan, Selenium scripts, local test report and failure screenshot mechanism.


---

## 1. Test plan — choosing five journeys

The portal has twenty UI endpoints. Testing all of them through a browser would be slow, and slow
suites stop being run. The five journeys were chosen by one rule: **if this stopped working, would
the blood bank be back on the phone tree?**

| # | Journey | Why it is critical | If it broke |
|---|---|---|---|
| **J1** | Staff registers, finds and updates a donor | Without a registry nothing else has anything to work with; the only journey that writes a record others rely on | No new donors could be added at all |
| **J2** | Search the registry and read the ranked shortlist | Search and ranking are the two things the portal exists to replace | Nobody could answer "who can we call for a B+ patient in Thane right now?" |
| **J3** | Record a request and see it on the dashboard | The only journey crossing a **screen boundary** — exactly where defect BUG-02 lived | Management loses visibility of supply against demand |
| **J4** | Staff assigns a donor; Staff is refused the fulfil action | Guards the accountability claim. A transition anyone may perform is not a workflow | Any staff member could close any request |
| **J5** | Admin fulfils; the donor becomes ineligible | The only journey asserting a **side effect crossing entities** | The portal would keep recommending someone who has just given blood |

Three criteria were used, in this order: **blast radius** (J1, J2), **cross-boundary behaviour** that
unit tests structurally cannot reach (J3, J5), and **the rules a user could otherwise talk their way
around** (J4).

### 1.1 What is deliberately not a Selenium journey

Compatibility across all 64 blood-group pairs, the eligibility boundary at 89/90/91 days, and every
illegal state transition are **unit tests**. They run in milliseconds, need no browser, and asserting
64 pairs through a UI would take minutes to prove exactly what a JUnit test proves instantly.
Selenium is reserved for what only a browser can check: that the pieces are wired together, that a
form really submits, and that a rule survives the round trip through HTTP.

---

## 2. The suite

16 tests across five journeys.

| Journey | Test | Asserts |
|---|---|---|
| J1 | `registersAValidDonor` | AC-01.1 — saved, count increases, findable with the group+city+available filters |
| J1 | `rejectsAnInvalidPhoneNumber` | AC-01.3 — a 5-digit phone is rejected with a field-level message **and nothing is saved** |
| J1 | `updatesADonor` | AC-03.1 — a city change persists and is visible on the detail page |
| J2 | `filtersByBloodGroup` | AC-02.1 — reads the group badge of every visible row, rather than trusting the query string |
| J2 | `filtersCombineWithAnd` | AC-02.2 — the combined result is smaller than either filter alone, which an OR could not produce |
| J2 | `showsAMessageWhenNothingMatches` | AC-02.4 — an explicit message, and the table is not rendered at all |
| J2 | `showsTheRankedShortlist` | AC-06.8, FR-14 — only B+, B−, O+, O− appear for a B+ patient; ranks are a contiguous 1..n |
| J3 | `recordsARequestAndSeesItOnTheDashboard` | AC-05.1, AC-05.3 — starts `New`, records its creator, and the dashboard aggregate moves |
| J3 | `dashboardReportsSupplyPerGroup` | AC-08.1 — all eight groups; callable never exceeds the register (would have caught BUG-03) |
| J3 | `worklistIsOrderedBySeverity` | BUG-05 regression, at the level a user sees it |
| J4 | `staffAssignsTheTopRankedDonor` | AC-07.1, FR-19 — status becomes `Matched`, the donor recorded, the shortlist withdrawn |
| J4 | **`staffIsRefusedTheFulfilAction`** | **AC-07.4** — the fulfil and reject actions are absent for STAFF, and the reason is stated |
| J4 | `adminIsOfferedTheActions` | The same request, same state, different role — without this, a missing button could equally be a rendering failure |
| J5 | `fulfillingMakesTheDonorIneligible` | AC-07.2, FR-20, AC-07.5 — donation stamped, future eligibility date, engine stops offering them, terminal state offers no actions |
| J5 | `adminRejectsWithAReason` | AC-07.3, FR-21 — the reason is mandatory and displayed |
| J5 | `donorControlsOwnAvailability` | AC-04.1 — a DONOR is not offered the registry or worklist, and can toggle availability |

---

## 3. How the suite is built to stay trustworthy

A UI suite earns its place only if people believe it. Five decisions make that true.

| Decision | Reason |
|---|---|
| **No chromedriver binary, no pinned driver version** | Selenium Manager (built in since 4.6) detects the installed Chrome and fetches the matching driver. A committed driver rots the moment Chrome auto-updates — the most common reason a working UI suite stops working |
| **No `Thread.sleep`, anywhere** | Every wait is a `WebDriverWait` on an explicit condition. A fixed sleep is either too short on a loaded machine (flaky) or too long on a fast one (slow), and a slow suite stops being run |
| **Elements located by `data-testid`** | Restyling a page or rewording a label must not break a test, or the suite generates false alarms and gets ignored |
| **Screenshot, page source and URL on every failure** | In Jenkins the browser is headless and nobody is watching. The capture is the only record of what the page looked like |
| **Unique generated test data; state restored** | The suite can be re-run against the same long-lived instance, not only against a freshly seeded database |

### 3.1 The quality-gate mechanics

Failsafe binds both `integration-test` **and** `verify`. Without the `verify` goal, a failed journey
is recorded and the build still goes green — precisely the hole the Task 10 gate must not have.

### 3.2 Why the journeys are in their own Maven profile

```bash
mvn clean verify                # unit tests only — green on any machine, nothing running
mvn clean verify -P ui-tests    # UI journeys, against a deployed instance
bash scripts/run-ui-tests.sh    # builds, deploys, then runs them (what the pipeline does)
```

`mvn clean verify` must be green from a clean checkout with nothing else running — that is US-10, and
the Jenkins *Unit Test* stage depends on it. The journeys need a deployed application and a browser;
making them part of the default build would mean the default build fails for reasons unrelated to
the commit, and the usual response to that is that people stop trusting the build.

---

## 4. Local execution through Maven

```
$ bash scripts/run-ui-tests.sh

Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J2DonorSearchIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J1DonorRegistrationIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J3RequestAndDashboardIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J4MatchAndRoleGuardIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J5FulfilAndEligibilityIT

Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 5. The failure screenshot mechanism, demonstrated

An assertion in J2 was deliberately changed to expect text that is not on the page, the single test
was run, and the capture was kept:

```
FAILURE captured: target/screenshots/J2DonorSearchIT.showsAMessageWhenNothingMatches.png
FAILURE url:      http://localhost:9091/donors?group=AB-&city=Nagpur
```

[`evidence/task-09-selenium/failure-demo/`](../evidence/task-09-selenium/failure-demo) contains all
three artefacts: the **PNG** of the page, the **page source** for failures a picture cannot explain
(an element in the DOM but not visible), and a **`.txt`** with the URL and page title — which answers
the first question anyone asks of a screenshot: *which page is this?*

---

## 6. Problems met and solved

### 6.1 The screenshot mechanism photographed the wrong page

The first version captured in `TestWatcher#testFailed`, which JUnit runs **after** `@AfterEach`.
`@AfterEach` ends the session, so every screenshot was a picture of the **login page** rather than
of the failure.

**Fix.** Capture now happens in `AfterTestExecutionCallback`, which runs immediately after the test
body and before any teardown.

**Lesson.** A screenshot mechanism that photographs the wrong page is worse than none, because it
*looks* like evidence.

### 6.2 BUG-07 — a DONOR signing in landed on HTTP 403

**Cause.** Login used one fixed success URL, `/dashboard`, which a DONOR may not open.

**Why the unit tests missed it.** Every one authenticated *directly as a role* and requested a
specific URL. None walked the login form and followed the redirect.

**Fix.** `RoleAwareSuccessHandler`, which keeps the useful default of returning a deep-linked user to
the page they asked for and only makes the fallback role-aware.

### 6.3 BUG-08 — `mvn clean` failed whenever a container was running

**Cause.** The container bind-mounted the WAR straight out of `target/`, so Docker held the file open
and the build directory could not be deleted.

**Fix.** The WAR is staged into `.deploy/` before mounting, decoupling the build directory from the
container's lifetime.

### 6.4 BUG-09 — `GET /logout` silently did not sign the user out

**Cause.** Spring Security 6 requires a POST to `/logout` when CSRF protection is on, so the GET
landed on an error page and the session survived — making journeys order-dependent in a way that
would only have shown up intermittently.

**Fix.** Teardown clears cookies, which cannot fail that way.

### 6.5 BUG-10 — a native ChromeDriver click submitted nothing

**Diagnosis.** Instrumented the journey:

```
DIAG btn: displayed=true enabled=true tag=button type=submit
          formAction=http://localhost:9091/me/availability
DIAG native click returned
DIAG after native: state=You are currently available   <- unchanged
```

`form.requestSubmit()` on the same element worked every time.

**Fix.** `clickSubmit()` performs the real user gesture first, then completes the submission
programmatically **only if** the page demonstrably did not navigate — detected by staleness of the
old `<body>`, not by a timer. The outcome is deterministic and the form is submitted exactly once.

### 6.6 Two mistakes in the tests themselves

- **A bare `isPresent()` immediately after a page navigation.** It asks the DOM once and raced the
  render, failing intermittently on a page that was correct.
- **An assertion on the exact top-ranked donor.** J4 and J5 record donations, which makes donors
  ineligible, so the best *available* match legitimately changes with execution order. The test now
  asserts the ranking **invariant** — if any shortlisted donor is an exact group match, the
  top-ranked one must be — which is both stronger and order-independent.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| No committed chromedriver and no pinned version | Selenium Manager matches the installed Chrome, so the suite does not rot when Chrome updates |
| No `Thread.sleep` anywhere | A fixed sleep is flaky when short and slow when long, and slow suites stop being run |
| Location by `data-testid` | Restyling must not raise false alarms, or the suite gets ignored |
| The 64 compatibility pairs are deliberately **not** Selenium tests | They run as unit tests in milliseconds; a browser would take minutes to prove the same thing |
| The journeys live in their own Maven profile | `mvn clean verify` must stay green on a clean checkout with nothing running — US-10, and the Jenkins Unit Test stage depends on it |
| Failsafe binds `verify` as well as `integration-test` | Without it a failed journey is recorded and the build still goes green |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-09-selenium/local-run-summary.txt`](../evidence/task-09-selenium/local-run-summary.txt) | The command and the 16-test result |
| [`evidence/task-09-selenium/`](../evidence/task-09-selenium) | Failsafe XML and per-class text reports |
| [`evidence/task-09-selenium/failure-demo/`](../evidence/task-09-selenium/failure-demo) | A real failure capture: PNG, page source and URL |
| [`src/it/java/com/bloodbank/ui/`](../src/it/java/com/bloodbank/ui) | Five journeys, the shared fixture and the failure extension |

---

## 9. Task 9 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Every acceptance criterion met and demonstrable | 16 journeys mapped to AC ids |
| D2 | Every business rule has a passing test | 178 unit + 16 journeys |
| D3 | `mvn clean verify` green from a clean target | Unit suite unaffected by the profile |
| D6 | The Selenium suite passes and would fail the build if red | Failsafe bound to `verify` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-09-selenium/`, including a failure demo |
| D10 | The deployed instance answers its health check | `run-ui-tests.sh` deploys then verifies |
| D11 | Committed on `main` per the branching model | `task-09` |
| D12 | Backlog updated with BUG-07 to BUG-10 | `docs/backlog.md` |

**Outcome:** five journeys chosen by blast radius rather than coverage, 16 tests green locally
through Maven, a failure mechanism that captures the page, its source and its URL — demonstrated by
deliberately breaking an assertion — and **four real application defects found after all 178 unit
tests were already passing**, which is the return on building the suite stated in defects rather
than in adjectives.

---

# Task 10 — Continuous Testing in Jenkins

**Deliverable:** Jenkins test report, failed pipeline evidence, defect correction commit and
successful rerun.


---

## 1. The gate in the pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI TEST ┃ → Promote
                                                                 ┗━ GATE ━┛
```

Every stage that **promotes** a build — publishing the image to the registry, replacing the running
container (added in Task 12) — is placed *after* the UI Test stage. If a journey fails, that stage
fails, the pipeline stops, and the stages below are never entered. **Nothing has to inspect a flag,
because a failing regression cannot physically reach them.**

### 1.1 What is deliberately not in this stage

| Not used | Why |
|---|---|
| `catchError` / `\|\| true` | Records the failure and lets the build continue. That is a gate that *reports*, not a gate that *stops* |
| `unstable` | A yellow build still promotes. "Mostly working" is not a deployment criterion for a portal that decides who gives blood |
| `retry` | A journey that passes on the second attempt is telling you the suite is flaky. Hiding that is how a suite stops being believed |

Reports and screenshots are published in an `always` block, because a red gate is exactly when they
are needed — and the next build overwrites the workspace.

---

## 2. The deliberate defect

### 2.1 First attempt — the wrong defect (build #8)

The ADMIN-only guard was weakened in `RequestService` itself. The build went red — at the **Unit
Test** stage:

```
Unit Test                    FAILED       24.3s
Package                      FAILED        0.1s
...
UI Test (quality gate)       FAILED        0.1s
Promote                      FAILED        0.1s
```

That is correct behaviour and good defence in depth: three unit tests caught it, and nothing was
deployed. But it proved the **unit suite** works, not the **Selenium gate** — the build never
reached deployment, so the gate was never exercised.

Build #8 is kept as evidence, because it is a genuine result worth being able to explain.

### 2.2 Second attempt — the right defect (build #9)

The defect was moved to where only a browser can see it: the `sec:authorize="hasRole('ADMIN')"`
attribute was removed from the fulfil form on the request detail page.

The **service layer was untouched** and still refuses a STAFF caller with 403, so no data was ever at
risk — but the screen now advertises an action the user cannot take.

```
$ mvn test
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0      <- all green WITH the defect in place
```

This is the defect shape the gate exists for, and it is a realistic mistake: a template tidy-up that
removes an attribute someone did not realise was load-bearing.

---

## 3. The failed pipeline — build #9

```
build #9  status=FAILED  total=93.9s
  Checkout                     SUCCESS       1.1s
  Build                        SUCCESS       3.0s
  Unit Test                    SUCCESS      12.2s      <- 178 tests, all green
  Package                      SUCCESS       5.3s
  Deploy to Tomcat             SUCCESS      10.8s
  Health Check                 SUCCESS       1.4s      <- behavioural checks passed too
  UI Test (quality gate)       FAILED       58.1s      <- journey J4
  Promote                      FAILED        0.1s      <- never entered
```

From the console log:

```
[ERROR] J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction:84
        [AC-07.4: a STAFF user must not be offered the fulfil action]

QUALITY GATE FAILED - a user journey is broken.
Nothing after this stage runs: no image is published and no container is replaced.
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.png
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.html
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.txt

Stage "Promote" skipped due to earlier failure(s)
```

**This is the headline result of the task.** Unit tests, packaging, deployment and the behavioural
health check *all passed*. The regression was invisible to every one of them, and the browser caught
it.

The archived screenshot shows the request detail page as a STAFF user, with the "Confirm donation
(fulfil)" button that should not be there.

---

## 4. The correction and the rerun

| Build | Result | What happened |
|---|---|---|
| #7 | SUCCESS | Baseline: gate added, everything green |
| #8 | FAILURE | Service-layer defect → caught at Unit Test |
| **#9** | **FAILURE** | **UI-only defect → caught at the gate; Promote skipped** |
| #10 | FAILURE | Defect fixed, but journey J2 flaked — see §5 |
| **#11** | **SUCCESS** | All green, Promote reached |

### 4.1 Build #11 — green after the fix

```
build #11  status=SUCCESS  total=211.9s
  Unit Test                    SUCCESS      47.7s      178 tests
  Health Check                 SUCCESS       4.9s
  UI Test (quality gate)       SUCCESS      98.1s      16 journeys
  Promote                      SUCCESS       0.1s      <- reached

Quality gate passed - build #11 is promotable.
SUCCESS - deployed and healthy at http://localhost:9090
```

The correction commit is `b518aad fix: restore the ADMIN-only guard on the fulfil action (BUG-06)`.

---

## 5. Problems met and solved

Build #9 failed **two** journeys. J4 was the intended defect. J2 was a genuine flake in a journey
whose subject is not timing at all, and fixing it took two attempts — which makes it the most
instructive failure in the project.

| Attempt | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | `StaleElementReference` | Read elements immediately after clicking *Search*, which is a full navigation | Wait for the results table before reading |
| 2 | Badges came back **empty**, though the archived page source showed ten correctly labelled rows | `getText()` returns only **rendered** text, so it depends on paint timing; in headless Chrome an element can be present and located while its text is still empty | Wait for **visibility** rather than presence, and read `textContent` |
| 3 | The badge list came back **empty entirely** | **The wait was already satisfied before the click.** The unfiltered list shown before clicking *Search* has the same results table, so `visibilityOfElementLocated` returned instantly against the **old** page | `clickSubmit()`, which waits for the old `<body>` to go **stale** — the only reliable signal that the navigation actually happened |

> **Waiting for a condition that is already true is not waiting at all.** After an action that
> replaces the page, wait for something about the **old** page to become false — staleness — before
> waiting for anything about the new one.

Both flake fixes were committed **separately** from the defect fix. That separation is the point: a
quality gate is only worth having if a red build means the application is broken. Every flake trains
people to re-run the build instead of reading it, and a gate nobody believes is not a gate.

---

## 6. Test reporting in Jenkins

- **Unit results** published from `target/surefire-reports/*.xml` after the Unit Test stage.
- **Journey results** published from `target/failsafe-reports/*.xml` after the gate.
- Both use `allowEmptyResults: false`, so a build whose tests silently did not run **cannot be
  green** — "0 tests, 0 failures" otherwise looks identical to success.
- Screenshots are archived with `onlyIfSuccessful: false`, since they only exist when the build
  failed.
- Jenkins renders the pass/fail trend across builds, so the red stretch (#8–#10) and the recovery at
  #11 are visible without opening a single log.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The gate is the stage **order**, not a check inside a stage | Nothing has to inspect a flag, because a failing regression cannot physically reach the promotion stages |
| No `catchError`, no `\|\| true`, no `unstable`, no `retry` | The first three turn a gate that stops into a gate that reports. The last hides flakiness, which is how a suite stops being believed |
| The first deliberate defect was replaced | Weakening the service-layer guard turned the build red at Unit Test — correct, but it proved the unit suite works, not that the gate does, because the build never reached deployment |
| Flake fixes committed separately from the defect fix | A gate is only worth having if a red build means the application is broken |
| Reports and screenshots published in an `always` block | A red gate is exactly when they are needed, and the next build overwrites the workspace |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt`](../evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt) | **The red gate with Promote never entered** |
| [`evidence/task-10-quality-gate/build-09-console-ui-gate-red.log`](../evidence/task-10-quality-gate/build-09-console-ui-gate-red.log) | Full console of the failed pipeline |
| [`evidence/task-10-quality-gate/gate-failure-screenshots/`](../evidence/task-10-quality-gate/gate-failure-screenshots) | The archived screenshot, page source and URL from the red build |
| [`evidence/task-10-quality-gate/stage-view-08-unit-test-red.txt`](../evidence/task-10-quality-gate/stage-view-08-unit-test-red.txt) | The first attempt, caught at Unit Test |
| [`evidence/task-10-quality-gate/stage-view-11-green-after-fix.txt`](../evidence/task-10-quality-gate/stage-view-11-green-after-fix.txt) | All stages green, Promote reached |
| [`evidence/task-10-quality-gate/build-history.txt`](../evidence/task-10-quality-gate/build-history.txt) | The red stretch #8–#10 and the recovery at #11 |

---

## 9. Task 10 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | AC-07.4 demonstrably enforced | J4 red with the defect, green after the fix |
| D2 | A test that would catch the defect exists | `staffIsRefusedTheFulfilAction` |
| D5 | The Jenkins pipeline is green for the corrected commit | Build #11 SUCCESS |
| D6 | **A red gate stopped the deployment** | Build #9: Promote never entered |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-10-quality-gate/` |
| D10 | The deployed container answers its health check | Post-gate health check |
| D11 | Defect and correction committed on `main` | `b518aad` |
| D12 | Backlog updated with BUG-06 | `docs/backlog.md` |

**Outcome:** the Selenium suite is a blocking gate rather than a report. Build #9 passed 178 unit
tests, packaged, deployed and passed a behavioural health check, and still went red because a
browser found an action offered to a user who was not permitted to take it — with no image published
and no container replaced. Two flakes exposed by the exercise were fixed in commits of their own,
because a gate is only worth having if a red build means the application is broken.

---

# Task 11 — Docker Image and Container Lifecycle

**Deliverable:** Dockerfile, image details, Docker command log and running container evidence.


---

## 1. The image

### 1.1 Multi-stage build

| Stage | Base image | Fate |
|---|---|---|
| 1 — build | `maven:3.9-eclipse-temurin-21` | **Discarded** |
| 2 — runtime | `tomcat:10.1-jdk21-temurin` | Shipped (1.05 GB) |

A single-stage build would ship Maven, the full toolchain, the source tree and every downloaded
dependency. That is a larger image, a larger attack surface, and the project's source code handed to
anyone who can pull it.

**Layer ordering matters as much as the staging.** `pom.xml` is copied and dependencies resolved
*before* `src/` is copied, because the pom changes rarely and the source changes constantly. Docker
reuses the cached dependency layer on every build where the pom has not moved — the difference
between a ~10-second rebuild and a ~5-minute one.

**`-DskipTests` is correct here, and only here.** The pipeline has already run 179 unit tests and 16
Selenium journeys before this image is built. Re-running them inside the image build would double
the pipeline time to prove exactly what was just proved, and would make the image build depend on a
browser being available.

### 1.2 Hardening, with the reason for each

| Measure | Reason |
|---|---|
| `rm -rf /usr/local/tomcat/webapps/*` | Removes the manager, host-manager, docs and examples applications. The examples webapp has historically carried its own vulnerabilities and has no business near a deployment |
| Runs as **uid 999**, not root | A container escape from a root process is considerably more useful to an attacker than one from an account that owns only its own data directory |
| JVM is **PID 1** (verified in the log) | `docker stop` delivers `SIGTERM` to Tomcat itself. Measured: **stops in under 1 second**, rather than being `SIGKILL`ed after the 10-second timeout |
| `setpriv`, not `su` | `su` leaves a shell as PID 1 and never forwards signals — which would silently undo the point above |
| `HEALTHCHECK` with a 60 s `start-period` | The container reports its own health using the same endpoint Ansible and Jenkins probe, and a slow JVM start is not misreported as a failure |
| OCI labels + `.dockerignore` | Any running image traces back to the commit that built it; the build context excludes `target/`, local databases, `.git/` and secrets |

### 1.3 Image details

```
$ docker images bloodbank --format "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}"
REPOSITORY   TAG      IMAGE ID       SIZE
bloodbank    latest   e40f29476505   1.05GB
bloodbank    local    e40f29476505   1.05GB      <- same image, two names

$ docker image inspect bloodbank:latest --format '...'
version:  local
revision: 195621b
created:  2026-10-07T05:20:11Z
student:  Ayush Mayekar (23102A0018)
```

**Two tags, two different jobs.** The build-number tag is immutable and is what a rollback pins to;
`latest` moves and is a convenience only. **Deploying `latest` makes rollback impossible**, because
nothing names the version that was running before — which is exactly what Task 14 needs.

---

## 2. The container lifecycle

`bash scripts/docker-lifecycle.sh` writes a **transcript** — each command echoed before it runs,
with its real output — rather than a summary written afterwards. All 14 stages are in
[`evidence/task-11-docker/docker-command-log.txt`](../evidence/task-11-docker/docker-command-log.txt).

| # | Stage | What it demonstrates |
|---|---|---|
| 1 | **Build** | Multi-stage build with build-args for traceability |
| 2 | **Tag** | Immutable build-number tag alongside moving `latest` |
| 3 | **Image details** | Size, layer history, OCI labels |
| 4 | **Run** | `-d`, `--name`, port map, named volume, restart policy |
| 5 | **Port mapping** | `docker port` → `8080/tcp -> 0.0.0.0:9090` |
| 6 | **Health** | Application answers; `docker inspect` reports `healthy` |
| 7 | **Logs** | `docker logs --tail 15` |
| 8 | **Inspect** | Image, state, restart policy, ports, mounts |
| 9 | **Process** | `uid 999, pid 1, java` — unprivileged **and** PID 1 |
| 10 | **Stats** | CPU, memory and network of the running container |
| 11 | **Stop** | `stopped in 0s` — a graceful SIGTERM shutdown |
| 12 | **Restart** | Same container started again, healthy |
| 13 | **Replacement** | Container destroyed and recreated — **29 donors before, 29 after** |
| 14 | **Summary** | Image, container, URL, volume, commit |

### 2.1 Stage 9 — unprivileged and PID 1

```
$ docker exec bloodbank-app sh -c 'ps -o uid,pid,comm -p 1'
  UID   PID COMMAND
  999     1 java
```

Both facts matter. **uid 999** means a container escape yields an account that owns only its own data
directory. **PID 1** means `docker stop` delivers SIGTERM to the JVM itself.

### 2.2 Stage 13 — the one that matters

```
donors before destroying the container: 29
$ docker rm -f bloodbank-app
$ docker run -d --name bloodbank-app ... -v bloodbank-data:/opt/bloodbank/data bloodbank:latest
donors after a brand-new container:     29
  ok   data survived container replacement (29 donors) - NFR-04 satisfied
```

The container is **disposable** and the state lives on the volume. That is precisely what makes the
recreate deployment strategy safe, and what makes the Task 14 rollback a re-deploy of a previous
image tag rather than a restore from backup.

---

## 3. Problems met and solved

### 3.1 BUG-11 — the hardened image could not open its own database

Running the hardened image against the existing volume failed:

```
java.nio.file.AccessDeniedException: /opt/bloodbank/data/bloodbank.mv.db
```

**Diagnosis.** `docker exec … ls -ld /opt/bloodbank/data` showed the directory owned by `root`, even
though the Dockerfile `chown`s it to `bloodbank`.

**Cause.** A **named volume keeps the ownership it was created with**. This volume had first been
created by the earlier, root-running Tomcat deployment from Task 8, and mounting it shadows the
image's own directory. Docker only copies the image directory's ownership into a **fresh** volume —
so **nothing in a Dockerfile can repair a volume that already exists**.

**Fix.** [`docker/entrypoint.sh`](../docker/entrypoint.sh) starts as root for exactly one purpose —
correcting the ownership, and only when it is actually wrong — then drops privileges with `setpriv`
and `exec`s Tomcat. This is the pattern the official `postgres` and `mysql` images use, and it makes
the image self-healing on any host instead of depending on whoever created the volume having got it
right.

```
entrypoint: data directory is owned by uid 0, expected 999 - correcting
entrypoint: dropping privileges to bloodbank (999:999)
```

**Why `setpriv` and not `su`.** `setpriv` execs the target process directly, so Tomcat stays PID 1
and receives `SIGTERM` from `docker stop`. With `su`, the shell becomes PID 1, signals are not
forwarded, and every stop would hit the ten-second timeout and kill the JVM.

---

## 4. `docker-compose.yml`

Runs the application plus the private registry for local use. It is deliberately **not** what the
pipeline uses — Jenkins builds a versioned image and deploys a specific tag (Task 12), and Ansible
provisions the node (Tasks 13–14). Letting Compose drive deployment as well would create two
definitions of "how this is deployed", which is the drift this subject is about.

---

## 5. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Multi-stage build | A single-stage image would ship Maven, the toolchain, the source tree and every dependency — bigger image, bigger attack surface, source handed to anyone who can pull it |
| `pom.xml` copied and dependencies resolved before `src/` | The pom changes rarely and the source constantly, so Docker reuses the cached layer — a 10-second rebuild instead of 5 minutes |
| The JVM is PID 1, achieved with `setpriv` rather than `su` | `docker stop` sends SIGTERM to PID 1. With `su`, a shell becomes PID 1, signals are never forwarded, and every stop hits the timeout |
| Runs as uid 999, not root | A container escape from root is considerably more useful to an attacker |
| Two tags with different jobs | The build-number tag is immutable and is what a rollback pins to; deploying `latest` makes rollback impossible |
| `-DskipTests` in the image build, and only there | The pipeline already ran 179 unit tests and 16 journeys; repeating them would double pipeline time and make the image build depend on a browser |

---

## 6. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-11-docker/docker-command-log.txt`](../evidence/task-11-docker/docker-command-log.txt) | A 300-line transcript of all 14 lifecycle stages with real output |
| [`Dockerfile`](../Dockerfile) | Multi-stage build, hardening, OCI labels |
| [`docker/entrypoint.sh`](../docker/entrypoint.sh) | Volume-ownership correction and privilege drop |
| [`.dockerignore`](../.dockerignore) | What is kept out of the build context, and why |
| [`docker-compose.yml`](../docker-compose.yml) | The local stack: application plus registry |

---

## 7. Task 11 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | The image builds from a clean context | Multi-stage build, `BUILD SUCCESS` |
| D7 | No hard-coded host, port, path or credential | Environment variables at `docker run` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-11-docker/docker-command-log.txt` |
| D10 | The container reports healthy and answers `/actuator/health` | `docker inspect` → `healthy` |
| D11 | Committed on `main` per the branching model | `task-11` |
| D12 | Backlog updated with BUG-11 | `docs/backlog.md` |

**Outcome:** a 1.05 GB runtime image that carries no build toolchain, runs unprivileged as uid 999
with the JVM as PID 1 so it stops gracefully in under a second, and reports its own health. The full
lifecycle is recorded as a transcript rather than a summary, and stage 13 proves the container is
disposable: destroyed and recreated with all 29 donors intact.

---

# Task 12 — Jenkins-Docker Continuous Deployment

**Deliverable:** versioned image, registry evidence and end-to-end commit-to-container pipeline.


---

## 1. The complete pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI TEST ┃ → Promote → Post-deploy
                                                                 ┗━ GATE ━┛      ↓
                                               build versioned image → push → pull back → fresh container
```

```
pipeline build #15  status=SUCCESS  total=101.3s

Checkout                             SUCCESS        1.1s
Build                                SUCCESS        3.2s
Unit Test                            SUCCESS       11.0s     179 tests
Package                              SUCCESS        4.4s
Deploy to Tomcat                     SUCCESS        7.9s
Health Check                         SUCCESS        1.4s
UI Test (quality gate)               SUCCESS       43.7s     16 journeys
Promote: image, registry, deploy     SUCCESS       24.9s
Post-deploy verification             SUCCESS        1.4s
```

**101 seconds from commit to a running, health-checked container**, against the 6-minute budget in
NFR-13 — and every promotion stage sits *after* the quality gate, so none of it is reachable while a
journey is red.

---

## 2. The decision worth defending

**The deployment pulls the image back out of the registry before running it.**

```bash
docker push   localhost:5001/bloodbank:15
docker rmi    localhost:5001/bloodbank:15     # drop the local copy
docker pull   localhost:5001/bloodbank:15     # fetch it back
docker run -d ... localhost:5001/bloodbank:15
```

Deleting the local image and pulling it back costs about a second and looks redundant — the image
was just built on this machine. It is the difference between a release that is **verified** and one
that is **assumed**:

- If the push silently failed, or the registry wrote a corrupt layer, or the tag was wrong,
  deploying the **local** image would succeed anyway and nobody would notice.
- The failure would then surface at the **first attempted rollback** — when the previous tag is
  pulled and found to be missing. That is the worst possible moment to discover it.

Pulling before deploying means that if the pipeline is green, the registry **provably** contains a
usable image. The Task 14 rollback depends entirely on that being true.

---

## 3. Versioned images

| Tag | Mutable? | Purpose |
|---|---|---|
| `bloodbank:<BUILD_NUMBER>` | **No** | The real identity of a release. Monotonic, unique, and what a rollback pins to |
| `bloodbank:latest` | Yes | Convenience for "give me the newest". Never deployed by anything automated |

**Deploying `latest` makes rollback impossible**, because nothing names the version that was running
before. The container is additionally labelled at run time:

```
$ docker inspect bloodbank-app --format '{{.Config.Image}}'
localhost:5001/bloodbank:15

$ docker inspect bloodbank-app \
    --format '{{index .Config.Labels "org.opencontainers.image.version"}} {{index .Config.Labels "org.opencontainers.image.revision"}}'
15 c2b890a
```

Any running container answers two questions without reference to anything else: **which build am I,
and which commit am I?**

---

## 4. The registry

```
$ curl -s http://localhost:5001/v2/_catalog
{"repositories":["bloodbank"]}

$ curl -s http://localhost:5001/v2/bloodbank/tags/list
{"name":"bloodbank","tags":["latest","1","15"]}

$ docker ps --filter name=bloodbank --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
NAMES                IMAGE                         STATUS
bloodbank-app        localhost:5001/bloodbank:15   Up 21 seconds (healthy)
bloodbank-registry   registry:2                    Up 6 minutes
```

**Why a private registry rather than Docker Hub.** No credentials to manage, no rate limits, and no
internet dependency during the practical examination — the demonstration works on an isolated
machine. Port **5001** because macOS reserves 5000 for AirPlay Receiver. Images persist on the
`bloodbank-registry-data` volume, so previous versions survive a registry restart, which is what
makes rollback possible after a reboot.

### 4.1 Using Docker Hub instead

Two changes, and nothing else:

```groovy
// 1. the image prefix
REGISTRY_HOST = 'docker.io/ayushmayekar'

// 2. credentials, injected by the plugin - never written into the Jenkinsfile
withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                  usernameVariable: 'REG_USER',
                                  passwordVariable: 'REG_PASS')]) {
    sh 'echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin'
    sh 'bash scripts/release.sh "${BUILD_NUMBER}"'
}
```

The `credentials-binding` plugin is already installed for exactly this. The token is masked in the
console log, and `--password-stdin` keeps it out of the process list — `docker login -p "$TOKEN"`
would expose it to anyone able to run `ps` on the agent.

---

## 5. Why `scripts/release.sh` and not shell inside the `Jenkinsfile`

The whole release is one script that the pipeline calls with the build number, and that a developer
can run by hand with any version string:

```bash
bash scripts/release.sh 99
```

One definition of what "release" means — the same reason the deploy and health-check stages call the
same scripts a developer uses. **A release procedure that exists only inside a CI server is a
procedure nobody can test, and nobody can run when the CI server is the thing that is broken.**

---

## 6. Data survives the upgrade

The container is replaced on every release and the named volume is re-attached, so donors and
requests persist across versions — demonstrated directly in Task 11 stage 13 (29 donors before and
after a full container replacement). The container is disposable; the data is not.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The deployment pulls the image back from the registry before running it | A silently failed push would otherwise surface at the first attempted rollback — the worst possible moment. Pulling first means a green pipeline proves the registry holds a usable image |
| The image is tagged with the Jenkins build number | Monotonic, unique and immutable. That tag is what the Task 14 rollback pins to |
| `latest` is never deployed by anything automated | Deploying it makes rollback impossible, because nothing names the version that was running before |
| The release is one script, called by the pipeline and runnable by hand | A release procedure that exists only inside a CI server is one nobody can test, and nobody can run when the CI server is broken |
| A private registry rather than Docker Hub | No credentials, no rate limits, no internet dependency during the practical examination |
| A second health check after the release | The first verified the build; this one verifies the thing actually serving traffic |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-12-registry/stage-view-15-full-cd.txt`](../evidence/task-12-registry/stage-view-15-full-cd.txt) | All 11 stages green in 101 seconds |
| [`evidence/task-12-registry/build-15-console-full-cd.log`](../evidence/task-12-registry/build-15-console-full-cd.log) | Full console of the end-to-end run |
| [`evidence/task-12-registry/registry-evidence.txt`](../evidence/task-12-registry/registry-evidence.txt) | Catalogue, tag list, running container and image labels |
| [`scripts/release.sh`](../scripts/release.sh) | Build, publish, pull back, deploy |

---

## 9. Task 12 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D3 | `mvn clean verify` green in the pipeline | Unit Test stage, 179 tests |
| D5 | The Jenkins pipeline is green for the commit | Build #15 SUCCESS |
| D6 | The Selenium quality gate passed before promotion | UI Test stage green, 16 journeys |
| D7 | No credential in the `Jenkinsfile` | Local registry; Docker Hub path uses `credentials-binding` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-12-registry/` |
| D10 | The deployed container answers its health check | Post-deploy verification stage |
| D11 | Committed on `main` and tagged | `task-12`, `v1.1.0` |
| D12 | Backlog updated | US-14 Done |

**Outcome:** the full commit-to-container path in 101 seconds — 179 unit tests, 16 Selenium journeys
as a blocking gate, a versioned image published to a private registry, pulled back to prove the push
worked, and deployed as a fresh container that answers its health check. Immutable build-number tags
are what make the Task 14 rollback possible.

---

# Task 13 — Configuration Management Script

**Deliverable:** configuration specification, Puppet manifest or Ansible playbook and first
execution log.


---

## 1. Why Ansible rather than Puppet

Both were allowed by the task sheet. Ansible was chosen for three reasons that matter to *this*
project.

| | Ansible | Puppet |
|---|---|---|
| Agent on the target | None — talks to the Docker API directly | Needs an agent installed and running |
| Idempotency | A property of each module, so it falls out of writing ordinary tasks | Also idempotent, via a different resource/catalog model |
| On Apple Silicon macOS | `pipx install ansible` and it works | Open-source Puppet on macOS/ARM is awkward and would have to run inside a Linux container — a moving part between the thing *managing* the deployment and the thing *being* deployed |

---

## 2. The configuration specification

The task asks for "all server prerequisites: packages, users, folders, files, ports and services".
[`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml) states each of them **once, as data**.

**No task in any role contains a literal port, path or image name** — they all read these variables,
which is what makes an override a one-flag change:

```bash
ansible-playbook site.yml -e app_image_tag=14 -e app_host_port=9095
```

| Category | Specified | Note |
|---|---|---|
| **Packages** | `docker`, `curl` | Deliberately short. The application ships as a container, so the node needs a runtime and a way to probe it — **not** a JDK, Maven or Tomcat. That shrinkage is the practical payoff of containerising |
| **Users** | uid/gid `999` | Pinned, because the container entrypoint compares the data volume's ownership against it (BUG-11) |
| **Folders** | volume `bloodbank-data` → `/opt/bloodbank/data` | The database must outlive every container |
| **Files** | the image itself, by immutable tag | The artefact *is* the image; there is no file to copy and no config to template on the node |
| **Ports** | app `9090→8080`, registry `5001→5000` | Neither default is used: 8080 is occupied and macOS reserves 5000 |
| **Services** | app + registry containers, `restart: unless-stopped` | NFR-03 — survive a crash and a reboot |

---

## 3. The target node

```ini
[bloodbank_nodes]
bloodbank-node-01 ansible_connection=local
```

The target is this machine, managed through its Docker daemon. Making it a real remote host is
**one line**:

```ini
[bloodbank_nodes]
prod-01 ansible_host=10.0.0.21 ansible_user=deploy
```

Nothing in `site.yml` or in any role refers to `localhost`; every task uses the inventory hostname
and the group variables. That property is the reason for writing it this way rather than hard-coding
connection details into the plays — and it is the honest answer to "this is just running on your
laptop": **the playbook does not know or care**.

---

## 4. The playbook

### 4.1 Three roles in a dependency chain

```
prereqs  →  registry  →  app
```

The ordering is a **dependency chain, not a preference**: the application's image is identified by a
registry URL, so a node whose registry is not running cannot deploy at all.

| Role | Does |
|---|---|
| **prereqs** | Asserts every required command exists; asserts the Docker **daemon responds**, not merely that the CLI is installed; reports what the node is; ensures both data volumes exist |
| **registry** | Ensures the registry container is running, waits for its API, reports its catalogue |
| **app** | Records what is deployed now, pulls the pinned image, ensures the container matches the declared state, waits for health, asserts the running image is the one requested |

### 4.2 Why `prereqs` checks rather than installs

Installing Docker from a playbook on macOS would mean driving a GUI installer, which cannot be done
honestly. The role fails with an actionable message instead of pretending:

```
'docker' is not available on bloodbank-node-01. Install it before provisioning:
on macOS use Docker Desktop or 'brew install docker'; on Debian/Ubuntu use
'apt-get install -y docker'.
```

That is also the right behaviour in general: **a provisioning run that silently installs a container
runtime on a machine that did not have one is not provisioning, it is a surprise.** On a Linux fleet
these `assert` tasks become `package:` tasks and **nothing else in the playbook changes**.

Checking the daemon separately from the binary is not pedantry — during this project the daemon
stopped responding while containers kept running, and "docker is installed" would have been true and
useless.

---

## 5. Health checking is behavioural

[`ansible/roles/app/tasks/health.yml`](../ansible/roles/app/tasks/health.yml) asserts the same rules
`scripts/health-check.sh` does, expressed as Ansible tasks so a run is self-contained.

| Check | Requirement |
|---|---|
| An unauthenticated API call is rejected | FR-24 |
| A B+ shortlist contains no incompatible donor | FR-14 |
| A STAFF user cannot fulfil a request | NFR-07 |
| The dashboard reports all eight blood groups | FR-22 |

```
TASK [Health check summary]
  "http://localhost:9090 is healthy AND behaving correctly: authentication enforced,
   18 compatible donors shortlisted for B+, role guard returns 403, dashboard reports
   8 blood groups, 39 donors registered."
```

"The container is up" and "the application is correct" are different claims, and only the second one
is worth provisioning for.

---

## 6. First execution log

```
$ ansible-playbook site.yml

TASK [prereqs : Check that the Docker daemon is responding, not merely installed]  ok
TASK [prereqs : Report what the node is]
  "bloodbank-node-01: docker 28.1.1, Docker Desktop, 10 CPU, 7.7 GiB RAM"
TASK [registry : Ensure the registry container is running]                        changed
TASK [registry : Show the catalogue]
  "registry at localhost:5001 holds: bloodbank"
TASK [app : Report the current deployment]
  "currently running tomcat:10.1-jdk21-temurin"
TASK [app : Ensure the application container is running the pinned image]          changed
TASK [app : Assert that the application reports UP]                               ok
TASK [Health check summary]                                                        ok

PLAY RECAP
bloodbank-node-01 : ok=28  changed=1  unreachable=0  failed=0
```

---

## 7. Problems met and solved

### 7.1 `The module interpreter '/usr/bin/env python3' was not found`

**Cause.** Ansible `exec()`s the interpreter directly rather than through a shell, so an `env`-style
path is taken **literally** and does not resolve.

**Fix.** An absolute interpreter path in the inventory.

### 7.2 `Non-string value found for env option. Key: SERVER_PORT`

**Cause.** `community.docker` refuses a templated environment value that resolves to a number.

**Fix.** `| string` on each. The module is right to refuse: YAML would otherwise turn `8080` into an
integer and `true` into a boolean depending only on how the value happened to be written.

### 7.3 BUG-12 — `/actuator/info` returned the HTML login page with HTTP 200

**Symptom.** An authenticated `curl` to `/actuator/info` came back as a login page, status 200.

**Cause.** Actuator was matched by the **browser** filter chain, so it expected form login. A script,
a container probe and an Ansible `uri` task cannot follow a redirect to a login form or scrape a CSRF
token.

**Why it is worse than merely unusable.** It answered **200**, so a naive check would have called
that success.

**Fix.** Actuator moved onto the API chain: Basic, stateless, `/actuator/health` open for probes and
everything else ADMIN-only. This is the right split on its own terms — Actuator is machine-facing,
and the chain it belongs on is the one built for machines.

This was a real **application** defect found by writing the configuration management, not by the
application's own tests — a reminder that the operational surface needs the same scrutiny as the
functional one.

---

## 8. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Ansible rather than Puppet | Agentless and push-based; Puppet on Apple Silicon would have had to run inside a container, adding a moving part between the thing managing the deployment and the thing being deployed |
| No task contains a literal port, path or image name | Every value is read from `group_vars`, which makes an override a one-flag change rather than an edit |
| The package list is two entries long | The application ships as a container, so the node needs a runtime and a way to probe it. That shrinkage is the practical payoff of containerising |
| `prereqs` checks rather than installs | A run that silently installs a container runtime on a machine that did not have one is a surprise, not provisioning |
| The role checks that the daemon **responds**, not that the CLI exists | During this project the daemon stopped answering while containers kept running |
| Health checking is behavioural, not a ping | "The container is up" and "the application is correct" are different claims |

---

## 9. Evidence index

| File | Shows |
|---|---|
| [`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml) | The configuration specification — every prerequisite as data |
| [`ansible/inventory.ini`](../ansible/inventory.ini) | The target node, and the one-line change to a remote host |
| [`ansible/site.yml`](../ansible/site.yml) | The provisioning playbook |
| [`ansible/roles/`](../ansible/roles) | `prereqs`, `registry`, `app`, and the behavioural health check |
| [`evidence/task-13-ansible/first-run.log`](../evidence/task-13-ansible/first-run.log) | First execution: `ok=28 changed=1 failed=0` |

---

## 10. Task 13 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D7 | No hard-coded port, path or image name in any role | All in `group_vars/all.yml` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-13-ansible/first-run.log` |
| D10 | The provisioned container answers its health check | Behavioural check, four assertions |
| D11 | Committed on `main` per the branching model | `task-13` |
| D12 | Backlog updated with BUG-12 | `docs/backlog.md` |

**Outcome:** every prerequisite the node needs is stated once as data, and three roles in a
dependency chain provision from it — asserting that the Docker daemon responds rather than that the
CLI exists, and verifying behaviour rather than liveness. Writing the health check surfaced a real
application defect that the application's own tests could not see.

---

# Task 14 — Automated Provisioning and Reliability Validation

**Deliverable:** provisioned node, idempotency evidence, health-check result and rollback/recovery
demonstration.


---

## 1. Provisioning a clean environment

The container **and the local images** were destroyed, then the node was provisioned from nothing.

```
state after destruction:
  containers:           0 application container(s)
  local images:         0
  registry still holds: {"name":"bloodbank","tags":["17","latest","1","15"]}
  data volume:          bloodbank-data

###  PROVISION FROM NOTHING  ###
TASK [app : Report the current deployment]
  "no application container present - this is a clean provision"
TASK [app : Pull the pinned image from the registry]            changed
TASK [app : Ensure the application container is running ...]    changed
TASK [Health check summary]
  "http://localhost:9090 is healthy AND behaving correctly: authentication enforced,
   18 compatible donors shortlisted for B+, role guard returns 403, dashboard reports
   8 blood groups, 39 donors registered."

PLAY RECAP   ok=29  changed=2  failed=0

state after provisioning from nothing:
  running image: localhost:5001/bloodbank:17
  donors:        39   (data survived the container and image being destroyed)
  health:        {"status":"UP","groups":["liveness","readiness"]}
```

Two things are worth pointing at. The **registry** is what made recovery possible — the image was
gone locally and was pulled back by name. The **volume** is what made it painless: 39 donors were
there before and after, because the container holds no state.

---

## 2. Idempotency

```
$ ansible-playbook site.yml          # first run
PLAY RECAP   ok=29  changed=2  failed=0

$ ansible-playbook site.yml          # immediately again
TASK [app : Report the current deployment]
  "currently running localhost:5001/bloodbank:17"
TASK [app : Report whether the container was replaced]
  "container already matches the declared state - nothing to do"

PLAY RECAP   ok=29  changed=0  failed=0
```

**This is the task's central claim**, and three specific decisions produce it.

| Decision | Without it |
|---|---|
| `changed_when: false` on every task that only *checks* something | Every pre-flight check would report a change, and `changed` would stop meaning anything |
| `comparisons: '*': strict` on the `docker_container` tasks | The module compares *declared* state against *actual* state. Without it, Docker's own defaults differ from what was asked for and the container is recreated on every run |
| `force_source: false` on the image pull | Already have this exact immutable tag? Then there is nothing to do |

### 2.1 Why it matters

Idempotency is not a nicety. It is what makes a playbook safe to run on a schedule, safe to run when
nobody is sure whether it already ran, and safe to run during an incident. **A playbook that must
only be run once is a script with extra syntax.**

---

## 3. Health check — behavioural, not a ping

Both `site.yml` and `rollback.yml` include the same checks, so a provision and a recovery are held
to the same standard:

```
ok: FR-24  an unauthenticated API call is rejected
ok: FR-14  a B+ shortlist contains no incompatible donor
ok: NFR-07 role guard enforced (HTTP 403)
ok: FR-22  the dashboard reports all eight blood groups
```

A Tomcat that answers `200` while the matching engine is broken has failed in the only way that
matters to a blood bank.

---

## 4. Rollback and recovery

```
$ ansible-playbook rollback.yml -e rollback_to=15

STATE BEFORE
  running image: localhost:5001/bloodbank:17
  donors:        39

TASK [Confirm the rollback target actually exists in the registry]   ok
TASK [Announce the rollback]
  "Rolling bloodbank-node-01 back from 'localhost:5001/bloodbank:17' to tag '15'.
   Data on volume bloodbank-data is untouched."
TASK [app : Ensure the application container is running the pinned image]  changed
TASK [Health check summary]                                           ok
TASK [Confirm the data volume is still attached and untouched]
  "bloodbank-data still attached - no data was touched by the rollback"
TASK [Rollback summary]
  "was: localhost:5001/bloodbank:17 | now: localhost:5001/bloodbank:15 |
   running: True | data volume bloodbank-data still attached and untouched."

PLAY RECAP   ok=23  changed=1  failed=0

STATE AFTER
  running image: localhost:5001/bloodbank:15
  donors:        39
  health:        {"status":"UP"}
```

**Under a minute, no rebuild, no data loss.**

### 4.1 Why a rollback is possible at all

Three properties, each established deliberately in an earlier task:

1. **Images are tagged with the build number and never overwritten** (Task 12) — so an earlier
   version still exists *and still has a name*. Deploying `latest` would make this impossible.
2. **The registry retains them on a volume** — so they survive a registry restart.
3. **All state lives on a separate volume** (Task 11) — so replacing the container does not touch the
   data.

### 4.2 The rollback uses the same code path as a deployment

`rollback.yml` includes the **same `app` role**, with the tag overridden. A recovery path that
differs from the normal path is one that has never really been tested, because it is only exercised
during incidents.

### 4.3 Two safety properties

- **The target must be named.** `rollback_to` has no default. Silently rolling back to "whatever
  seems reasonable" during an incident is how the wrong version reaches production.
- **The target is verified in the registry *before* the running container is touched.** Discovering
  that the tag is missing *after* removing the current container would turn a rollback into an
  outage.

---

## 5. Problems met and solved

### 5.1 The first rollback failed its verification, although the rollback worked

The `app` role asked the application what version it was, via `/actuator/info`. Tag 15 predates the
Actuator security fix from Task 13, so on that image the endpoint redirected to the HTML login page —
and the check failed, even though the rollback itself had worked perfectly.

> **Verify a deployment using facts the *deployer* owns, not features of the artefact being
> deployed.** An older release cannot be expected to support a newer introspection endpoint, and a
> rollback that can only verify versions *newer* than the bug it is escaping is useless precisely
> when it is needed.

**Fix.** Verification now reads the container's image reference from the Docker API:

```yaml
- deployed.container.Config.Image == app_image
- deployed.container.State.Running
```

That works for every version, past and future. The rollback also now asserts that the data volume is
still attached — because "it came back up" and "it came back up with the data" are different claims.

### 5.2 A non-deterministic idempotency result

One capture of the reliability run reported `changed=1` where every other run reported `changed=0`,
and the log recorded only the count — so finding out which task was responsible meant running the
playbook by hand afterwards, by which time the environment had moved on.

**Fix.** The check now prints the **name of every task** that reported a change. A failing assertion
that says only `changed=1` is a failure you have to reproduce before you can read it; naming the task
makes it diagnosable from the captured log alone, which is the whole reason for capturing logs.

---

## 6. One command for all four properties

`bash scripts/verify-reliability.sh` performs the whole demonstration and **asserts** each step:

```
1 / 4  PROVISION A CLEAN ENVIRONMENT
  ok   provisioned from nothing: localhost:5001/bloodbank:17, 39 donors (data survived)

2 / 4  IDEMPOTENCY - the same run again must change nothing
  ok   IDEMPOTENT - the second run reported changed=0

3 / 4  BEHAVIOURAL HEALTH CHECK
  ok   STAFF fulfilling a request is refused => 403
  HEALTH CHECK PASSED

4 / 4  ROLLBACK AND RECOVERY
  ok   ROLLED BACK to a different image with the data intact
  ok   back on localhost:5001/bloodbank:17 with 39 donors

ALL FOUR RELIABILITY PROPERTIES VERIFIED
```

It is one command because a reliability claim that takes eight remembered commands to show is a
claim that will not be shown — and under examination conditions, a forgotten step reads as a
property that does not hold. It picks the rollback target automatically as the newest published tag
that is not the one deployed, so the demonstration does not depend on remembering which versions
exist.

---

## 7. The four reliability properties, and the evidence for each

| Property | Requirement | Evidence |
|---|---|---|
| Provisioning from nothing | US-15 | Container **and local images** destroyed → `changed=2`, healthy, 39 donors |
| **Idempotency** | AC-15.2 | Second identical run → **`changed=0`** |
| Behavioural health check | AC-15.3 | Four rule assertions in every run |
| **Rollback** | AC-15.4, NFR-05 | 17 → 15 in under a minute, healthy, data intact |
| Crash recovery | NFR-03 | `restart: unless-stopped` declared and asserted on the container |

---

## 8. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| `changed_when: false` on every check-only task | Otherwise every pre-flight check reports a change and "changed" stops meaning anything |
| `comparisons: strict` on the container tasks | The module then compares declared state against actual state rather than against Docker's defaults, which differ and would recreate the container every run |
| The rollback uses the same `app` role as a deployment | A recovery path that differs from the normal path is only exercised during incidents — the worst time to find out it is broken |
| `rollback_to` has no default | Silently rolling back to "whatever seems reasonable" is how the wrong version reaches production |
| The target is verified in the registry **before** the container is touched | Finding it missing afterwards would turn a rollback into an outage |
| Verification reads the container's image reference, not the application's self-report | An older release cannot be expected to support a newer introspection endpoint |

---

## 9. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-14-reliability/clean-provision.log`](../evidence/task-14-reliability/clean-provision.log) | Provisioning after destroying the container and the local images |
| [`evidence/task-14-reliability/idempotency-second-run.log`](../evidence/task-14-reliability/idempotency-second-run.log) | **`ok=29 changed=0`** |
| [`evidence/task-14-reliability/rollback-run.log`](../evidence/task-14-reliability/rollback-run.log) | 17 → 15, health-checked, 39 donors intact |
| [`evidence/task-14-reliability/verify-reliability-full-run.log`](../evidence/task-14-reliability/verify-reliability-full-run.log) | All four properties in one asserting run |
| [`ansible/rollback.yml`](../ansible/rollback.yml) | The rollback playbook and its safety checks |

---

## 10. Task 14 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D7 | No hard-coded value; the image tag is a variable | `-e app_image_tag=…` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-14-reliability/` |
| D10 | The provisioned and rolled-back container both answer the health check | Behavioural check in both playbooks |
| D11 | Committed on `main` and tagged | `task-14`, `v1.2.0` |
| D12 | Backlog updated | US-15 Done |

**Outcome:** all four reliability properties demonstrated rather than asserted — provisioning from
nothing after the container and local images were destroyed, `changed=0` on an immediate rerun,
behavioural health assertions in every run, and a rollback from image 17 to 15 in under a minute with
the data untouched. The rollback uses the same role as a deployment, so the recovery path is
exercised every day rather than only during incidents.

---

# Task 15 — Final End-to-End Release, Documentation and Viva

**Deliverable:** final repository, live demonstration, complete report, screenshots/video,
presentation and viva.


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

---

## End of report

**Ayush Mayekar** · 23102A0018 · BE Computer Engineering, Division A · DevOps · AY 2026-27

Generated from the fifteen task documents by `scripts/build-final-report.sh`.
