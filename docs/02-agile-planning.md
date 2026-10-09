# Task 2 — Agile Planning and DevOps Workflow

**Deliverable:** backlog, task board, sprint plan, Definition of Done and DevOps workflow diagram.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 29 September 2026 · **Tag:** `task-02`

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
