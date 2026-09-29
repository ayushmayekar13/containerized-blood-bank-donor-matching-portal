# Task 1 — Problem Definition and Scope

**Project:** Containerized Blood Bank Donor Matching Portal
**Student:** Ayush Mayekar · Roll No. 23102A0018 · BE Computer Engineering, Div. A
**Subject:** DevOps · Academic Year 2026-27, ODD Semester
**Date frozen:** 29 September 2026

---

## 1. Real-time need for the system

Blood is a perishable, uncertain-supply resource. In most Indian hospitals the gap between a
patient needing blood and a matching donor being contacted is still bridged by phone calls,
WhatsApp forwards and paper registers. Three facts make this a genuine operational problem:

1. **Blood cannot be manufactured.** Every unit must come from a human donor, and whole blood has
   a shelf life of roughly 35–42 days. Stock cannot simply be built up in advance.
2. **A donor is not eligible continuously.** After a whole-blood donation a donor must wait about
   90 days (three months) before donating again. A register that does not track the last donation
   date will repeatedly call back people who are ineligible.
3. **Compatibility is not "same group only".** O− is a universal red-cell donor, AB+ is a universal
   recipient, and in an emergency a B+ patient can receive from B+, B−, O+ or O−. A phone-based
   process almost never exploits this, so compatible donors sitting in the same city go unused.

The result is a measurable failure: **time-to-match** (request raised → compatible, eligible,
reachable donor identified) is routinely measured in hours when it should be measured in seconds,
and nobody can answer "how many eligible O− donors do we have in this city right now?" without
reading a register.

### Pain points in the existing (manual) process

| # | Pain point | Consequence |
|---|---|---|
| P1 | Donor records in registers / scattered spreadsheets | No search; duplicates; records lost |
| P2 | Eligibility (90-day rule) tracked mentally or not at all | Ineligible donors called; eligible ones missed |
| P3 | Compatibility judged from memory | Compatible donors never contacted |
| P4 | No request lifecycle — a request is a phone call | Nobody knows if a request is still open |
| P5 | No separation of duties | Any staff member can mark anything "done"; no accountability |
| P6 | No aggregate view of supply | Shortages discovered only when a request fails |
| P7 | No audit trail | Disputes cannot be resolved; no data for planning |

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

## 3. Target users

| User | Role in system | What they do |
|---|---|---|
| Blood bank staff / technician | `STAFF` | Register and update donors, record incoming blood requests, run the matching engine, assign a matched donor |
| Blood bank administrator / medical officer | `ADMIN` | Everything STAFF can do, plus approve the final outcome of a request (fulfil or reject) and view the management dashboard |
| Registered donor | `DONOR` | View own profile and donation history, toggle own availability (read-only on everyone else's data) |
| Hospital / requesting desk | (via STAFF) | Raises a blood request that STAFF records in the portal |

## 4. Stakeholder list

| Stakeholder | Interest / stake | Influence |
|---|---|---|
| Patients and their families | Fast, correct match; this is the ultimate beneficiary | Low (indirect) |
| Voluntary blood donors | Not being contacted when ineligible; privacy of contact details | Medium |
| Blood bank staff | A tool that is faster than the phone; low training overhead | **High** (daily users) |
| Blood bank administrator | Accountability, audit trail, supply visibility | **High** (approves the system) |
| Hospital transfusion desk | Reliable turnaround on raised requests | Medium |
| IT / operations team | Deployability, reproducibility, ease of rollback and upgrade | **High** (owns the DevOps pipeline) |
| Regulatory bodies (NBTC / State Blood Transfusion Council) | Correct eligibility enforcement, traceability of every unit | Medium (constraints) |
| Course faculty / examiner | Demonstrated DevOps lifecycle coverage across all 15 tasks | **High** (acceptance) |

## 5. Objectives

**Primary (functional)**

- O1 — Maintain a single, searchable donor registry (create, view, update, search).
- O2 — Record blood requests with patient, blood group, units, hospital, city and urgency.
- O3 — Automatically produce a ranked list of **compatible + eligible + available** donors for any
  request, applying the ABO/Rh compatibility matrix and the 90-day rule.
- O4 — Enforce a request status workflow `NEW → MATCHED → FULFILLED | REJECTED` in which only the
  permitted role may perform each transition.
- O5 — Present a summary dashboard: donors per blood group, requests per status, shortage flags.

**Primary (DevOps — the actual subject of assessment)**

- O6 — Version-control the project in Git with a documented branching policy, feature branches,
  pull-request review, a resolved merge conflict and semantic version tags.
- O7 — Build and package the application reproducibly with Maven, producing an archivable artefact.
- O8 — Automate build → test → package → deploy in Jenkins, defined as code in a `Jenkinsfile`.
- O9 — Automate functional verification with Selenium and make it a **quality gate** that stops
  deployment on failure.
- O10 — Containerize the application with Docker, publish versioned images to a registry, and
  deploy a fresh container automatically after a successful pipeline run.
- O11 — Describe the target environment as code with Ansible, proved **idempotent**, with a health
  check and a tested rollback to the previous stable release.

## 6. Measurable success criteria

| ID | Criterion | Target | How it is measured |
|---|---|---|---|
| S1 | Time to produce a ranked donor shortlist | < 2 s for 500 donors | Response time of `GET /api/requests/{id}/matches` |
| S2 | Compatibility correctness | 64/64 ABO-Rh pairs correct | Unit test over the full 8 × 8 matrix |
| S3 | Eligibility correctness | 0 ineligible donors in any shortlist | Unit test: donation < 90 days ⇒ excluded |
| S4 | Workflow safety | 0 illegal status transitions accepted | Unit test per invalid transition + role check |
| S5 | Build reproducibility | `mvn clean verify` green from a clean clone | Jenkins build on a fresh workspace |
| S6 | Pipeline duration (commit → deployed container) | < 6 min | Jenkins pipeline stage view |
| S7 | Quality gate effectiveness | A failing Selenium test ⇒ deploy stage never runs | Deliberate-defect pipeline run (Task 10) |
| S8 | Provisioning idempotency | 2nd `ansible-playbook` run reports `changed=0` | Ansible play recap |
| S9 | Recovery | Rollback to previous image tag healthy in < 2 min | `rollback.yml` + health check |
| S10 | Deployment manual steps | 0 (one command, end to end) | `scripts/demo-all.sh` |

## 7. Constraints

**Technical**

- C1 — Single developer, single laptop (Apple Silicon macOS); no cloud budget, so every
  environment is local: Jenkins on `localhost:8090`, application container on `localhost:9090`,
  private Docker registry on `localhost:5001`.
- C2 — Host port 8080 is already occupied by another service, so Jenkins and the application must
  be re-mapped (8090 / 9090). This is recorded here because it affects every script.
- C3 — The build tool must be Maven, and the deployment target must be Tomcat or Nginx (course
  requirement). The application is therefore packaged as a **WAR** deployed into Tomcat 10.1.
- C4 — Java 21 is used (Spring Boot 3 requires Java 17+); the system default `java` is 11, so every
  script pins `JAVA_HOME`.
- C5 — No external database server: an embedded **H2 file database** on a Docker volume keeps the
  stack reproducible while still demonstrating data persistence across container restarts.
- C6 — The demonstration must work **offline**; no CDN assets, no Docker Hub dependency at demo
  time (hence the local registry; Docker Hub is documented as the alternative).

**Process / scope**

- C7 — The work is delivered as exactly 15 tasks, each with its own commit, in the sequence fixed
  by the subject task sheet.
- C8 — Calendar: 29 September 2026 → 9 October 2026.

**Domain / regulatory (acknowledged, deliberately out of MVP)**

- C9 — Donor phone numbers and medical eligibility are personal data; in production they would need
  consent capture, encryption at rest and access logging. The MVP stores them in plain form on a
  local database and restricts access by role only — stated as a known limitation.
- C10 — The portal **matches donors; it does not certify blood units.** Serological screening,
  cross-matching and unit labelling remain outside the system boundary.

## 8. Approved MVP scope (frozen)

### In scope

| Ref | Capability |
|---|---|
| F1 | Donor registration (create) with validation on blood group, phone and city |
| F2 | Donor listing and detail view |
| F3 | Donor update, including availability toggle and last-donation date |
| F4 | Donor **search** by blood group, city and availability |
| F5 | Blood request creation (patient, group, units, hospital, city, urgency) |
| F6 | Blood request listing, detail view and search by status / group |
| F7 | **Donor matching engine** — compatibility matrix + 90-day eligibility + availability, same-city first |
| F8 | **Role-based status workflow** `NEW → MATCHED → FULFILLED / REJECTED` with per-role permissions |
| F9 | **Summary dashboard** — donors per blood group, requests per status, shortage flags |
| F10 | Authentication and three roles (`ADMIN`, `STAFF`, `DONOR`) |
| F11 | REST API mirroring F1–F9, plus `/actuator/health` for automated health checks |

### Explicitly out of scope (future enhancements)

SMS/email notification to matched donors · geolocation and distance-based ranking · blood **unit**
inventory with expiry tracking · donation camp scheduling · donor self-registration with OTP ·
multi-blood-bank tenancy · analytics/forecasting · mobile app · HL7/FHIR hospital integration ·
external database cluster and high availability.

### Scope freeze

This scope is **approved and frozen** as of 29 September 2026. Any change after this point is
recorded as a new backlog item for a future release, not absorbed into the MVP — this is what makes
the 15-task plan in Task 2 estimable.

---

## 9. Mapping of the 15 tasks to this problem

| Task | Contribution |
|---|---|
| 1 | This document — problem, users, stakeholders, objectives, constraints, frozen scope |
| 2 | Agile plan: user stories, backlog, Kanban board, Definition of Done, lifecycle diagram |
| 3 | SRS, use-case and architecture diagrams, data model, API list, working local setup |
| 4 | Git/GitHub repository, README, `.gitignore`, structure, issue templates, branch policy |
| 5 | Feature 1 (donor registry + matching) on a feature branch, via pull request |
| 6 | MVP completion, second feature branch, merge-conflict resolution, `v1.0.0` tag |
| 7 | Jenkins installation and a Maven CI job with artefact archiving |
| 8 | `Jenkinsfile` pipeline-as-code with Tomcat deployment and a parameterised environment |
| 9 | Selenium test design for the critical user journeys, executed locally through Maven |
| 10 | Selenium in Jenkins as a quality gate; deliberate defect, correction, successful rerun |
| 11 | `Dockerfile`, image build/tag and full container lifecycle |
| 12 | Jenkins → versioned Docker image → registry → fresh container (continuous deployment) |
| 13 | Ansible inventory and playbook describing every server prerequisite |
| 14 | Clean-environment provisioning, idempotency proof, health check, rollback/recovery |
| 15 | End-to-end release run, full documentation, presentation and viva |
