# Task 1 — Problem Definition and Scope

**Deliverable:** problem statement, stakeholder list, objectives, constraints and approved MVP
scope.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 29 September 2026 · **Tag:** `task-01`

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
