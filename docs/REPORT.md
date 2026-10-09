# Project Report

# Containerized Blood Bank Donor Matching Portal

**Student:** Ayush Mayekar · **Roll No.** 23102A0018
**Class:** BE Computer Engineering, Division A
**Subject:** DevOps · **Academic Year:** 2026-27, ODD Semester
**Department of Computer Engineering**

---

## Contents

1. [Abstract](#1-abstract) · 2. [Problem and objectives](#2-problem-and-objectives) ·
3. [Requirements](#3-requirements) · 4. [Architecture](#4-architecture) ·
5. [The DevOps pipeline](#5-the-devops-pipeline) · 6. [Testing strategy](#6-testing-strategy) ·
7. [Containerization and deployment](#7-containerization-and-deployment) ·
8. [Configuration management and reliability](#8-configuration-management-and-reliability) ·
9. [Results](#9-results) · 10. [Defects and troubleshooting](#10-defects-and-troubleshooting) ·
11. [Limitations](#11-limitations) · 12. [Future enhancements](#12-future-enhancements) ·
13. [Conclusion](#13-conclusion)

---

## 1. Abstract

This project delivers a web portal that matches blood requests to donors, and — more importantly for
this subject — the automated DevOps pipeline that builds, tests, containerizes, deploys and
provisions it.

The application maintains a searchable donor registry, records blood requests, and ranks the donors
who can actually be contacted for each request by applying the ABO/Rh compatibility matrix, the
90-day donation interval and the donor's availability. A role-based workflow governs the lifecycle of
every request, so only an administrator may confirm that a donation took place.

The pipeline runs 179 unit tests and 16 Selenium user journeys on every commit, with the browser
suite acting as a **blocking quality gate**: a broken user journey cannot reach deployment. A passing
build produces a versioned Docker image, publishes it to a private registry, and deploys a fresh
container pulled back from that registry — **101 seconds from commit to a healthy container**.
Ansible describes the target node as code, is demonstrably idempotent, and rolls back to any previous
release in under a minute without touching the data.

Eleven defects were found and fixed during development. Four of them were found by the Selenium suite
*after* all unit tests were already passing, which is the clearest measurement of what the testing
strategy was worth.

## 2. Problem and objectives

### 2.1 The problem

Blood cannot be manufactured; it must come from a human donor and has a shelf life of about 35–42
days. A donor who has just given whole blood is ineligible for roughly 90 days. And red-cell
compatibility is considerably wider than "same group only" — a B+ patient can receive from B+, B−, O+
or O−.

Most blood banks still match requests to donors by telephone and paper register. The consequences are
measurable: time-to-match runs to hours, compatible donors in the same city go uncontacted because
nobody computes the matrix from memory, ineligible donors are called back, and nobody can answer "how
many eligible O− donors do we have right now?" without reading a register.

### 2.2 Objectives

**Functional** — a searchable donor registry; recorded blood requests; automatic ranking of
compatible, eligible, available donors; a role-based request workflow; a supply-versus-demand
dashboard.

**DevOps (the assessed objectives)** — version control with a real branching policy; a reproducible
Maven build; a Jenkins pipeline defined as code; automated functional testing as a blocking quality
gate; containerized delivery through a registry; and infrastructure described as code with proven
idempotency and a tested rollback.

Full statement: [`01-problem-definition.md`](01-problem-definition.md).

## 3. Requirements

26 functional and 15 non-functional requirements, each traced to the test that proves it:
[`SRS.md`](SRS.md).

The three requirements the project is really built around:

| ID | Requirement |
|---|---|
| **FR-14** | Red-cell compatibility must be correct for all 64 recipient/donor pairs |
| **FR-08** | A donor is eligible only when the last donation is **at least** 90 days ago |
| **NFR-07** | Authorisation is enforced server-side, not by hiding a control in the user interface |

## 4. Architecture

| Layer | Technology | Why |
|---|---|---|
| Language | Java 21 | Spring Boot 3 requires 17+; same JVM on the build host and in the image |
| Framework | Spring Boot 3.3, MVC + Thymeleaf | One artefact serves the UI and the REST API; Actuator supplies the health endpoint the pipeline, the container and Ansible all consume |
| Persistence | Spring Data JPA + **H2 file database** | Real SQL with no external service, so the whole pipeline runs on one laptop and offline; the file lives on a Docker volume |
| Security | Spring Security, BCrypt, three roles | Needed to demonstrate the role-based workflow |
| Build | **Maven 3.10** → `bloodbank.war` | Required by the task sheet; Surefire/Failsafe separate fast unit tests from slow browser tests |
| Deployment target | **Tomcat 10.1** | Required by the task sheet; the WAR deploys as `ROOT.war` so the context path is `/` everywhere |
| UI testing | Selenium 4 + JUnit 5, headless Chrome | Selenium Manager resolves the driver, so no binary is committed and nothing rots when Chrome updates |

**The rule that makes the whole thing testable:** every business rule lives in the service layer or
on a domain enum. Controllers bind input, call one service method and choose a view. That is why 64
compatibility pairs and 8 eligibility boundaries are proved in milliseconds, and Selenium only has to
cover five journeys.

The matching engine composes three independent predicates — compatible ∧ available ∧ eligible — and
then ranks: **exact group → same city → longest rested → name**. Each rule is a decision: exact-group
first preserves scarce O− stock; longest-rested rotates the load across the register rather than
repeatedly calling the same willing people; name last makes the ordering deterministic so tests
cannot flake.

Details: [`ARCHITECTURE.md`](ARCHITECTURE.md) · [`API.md`](API.md).

## 5. The DevOps pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check ─┨ UI TEST ┠─► Promote → Post-deploy
                                                                   GATE
```

| Stage | Does | Why it is separate |
|---|---|---|
| Checkout | Clone; print commit and JDK | A build log that does not say which commit and which JDK it used is not evidence |
| Build | `mvn clean compile` | A compile failure should be reported as one, not buried in a test run |
| Unit Test | 179 tests, report published in an `always` block | A red build is exactly when the report is needed |
| Package | `bloodbank.war`, archived and fingerprinted | The artefact every later stage consumes |
| Deploy | Container on port 9090 | Something for the browser to drive |
| Health Check | Nine behavioural assertions | "Answering" is not "correct" |
| **UI Test** | **16 Selenium journeys — the gate** | **Everything below is unreachable while this is red** |
| Promote | Versioned image → registry → pull back → fresh container | |
| Post-deploy | Health check again | The first verified the build; this verifies the release |

**The gate is the stage order, not a check inside a stage.** No `catchError`, no `|| true`, no
`unstable`, no `retry` — the first three turn a gate that stops into a gate that reports, and the
last hides flakiness, which is how a suite stops being believed.

Jenkins itself is described as code: two version-controlled Groovy scripts create the security realm
and register the JDK and Maven tools, with the setup wizard disabled. The entire controller can be
deleted and rebuilt with two commands, which is how the reproducibility claim is verified rather than
asserted.

Details: [`07`](07-jenkins-ci.md) · [`08`](08-pipeline-as-code.md) · [`jenkins/SETUP.md`](../jenkins/SETUP.md).

## 6. Testing strategy

| Level | Count | Tool | Covers |
|---|---|---|---|
| Unit | **179** | JUnit 5 + Surefire | The 64-pair compatibility matrix, eligibility at 0/1/45/**89/90**/91/120/400 days, every ranking rule, every legal and illegal transition, every role guard, dashboard aggregation |
| HTTP contract | *(within the 179)* | MockMvc over the real filter chain | 401, 403, 404, 409, 422 — asserted over HTTP because the claim is that the API cannot bypass the UI's restrictions |
| **UI journey** | **16** | Selenium 4 + Failsafe | Five critical flows: registry, search and ranking, request-to-dashboard, the role guard, fulfil-to-eligibility |

**What is deliberately not a Selenium test:** the 64 compatibility pairs, the eligibility boundary and
the illegal transitions are unit tests. Asserting 64 pairs through a browser would take minutes to
prove exactly what JUnit proves instantly. Selenium is reserved for what only a browser can check:
that the pieces are wired together, that a form really submits, and that a rule survives the round
trip through HTTP.

**What makes the browser suite trustworthy:** no committed chromedriver; no `Thread.sleep` anywhere;
elements located by `data-testid` so restyling cannot break a test; a screenshot, page source and URL
captured on every failure; and unique generated test data with state restored, so the suite can be
re-run against a long-lived environment.

Details: [`09`](09-selenium-tests.md) · [`10`](10-continuous-testing.md).

## 7. Containerization and deployment

**Multi-stage image.** Maven builds the WAR in a stage that is discarded; the runtime image is Tomcat
plus the WAR. A single-stage build would ship the toolchain, the source tree and every dependency —
a bigger image, a bigger attack surface, and the source handed to anyone who can pull it.

**Hardening, each for a reason:** Tomcat's manager, docs and examples applications are removed; the
application runs as uid 999; the JVM is PID 1, so `docker stop` reaches it directly and the container
stops in under a second rather than being killed after ten; OCI labels carry the build number and git
revision, so any running image traces back to its commit.

**Versioning.** The build-number tag is immutable and is the real identity of a release; `latest`
moves and is never deployed by anything automated — **deploying `latest` makes rollback impossible**,
because nothing names the version that was running before.

**The deployment pulls the image back from the registry before running it.** That costs about a
second and is the difference between a release that is verified and one that is assumed: a silently
failed push would otherwise surface at the first attempted rollback, which is the worst possible
moment.

**State.** All data lives on the named volume `bloodbank-data`. Proven directly: the container was
destroyed and recreated with 29 donors present before and after. The container is disposable; the
data is not — which is what makes recreate-deployment and rollback safe.

Details: [`11`](11-docker-lifecycle.md) · [`12`](12-jenkins-docker-cd.md).

## 8. Configuration management and reliability

Ansible, chosen over Puppet because it is agentless and because open-source Puppet on Apple Silicon
would have had to run inside a container — adding a moving part between the thing managing the
deployment and the thing being deployed.

`group_vars/all.yml` states every prerequisite once, as data: packages, users, folders, ports, images,
containers, health settings. **No task in any role contains a literal port, path or image name.** The
package list is two entries long — `docker` and `curl` — because the application ships as a
container, so the node needs a runtime and a way to probe it, not a JDK or Tomcat. That shrinkage is
the practical payoff of containerising.

**The four reliability properties, each demonstrated:**

| Property | Evidence |
|---|---|
| Provisioning from nothing | Container **and local image** destroyed → provisioned from the registry, 39 donors intact |
| **Idempotency** | Second identical run: **`ok=29 changed=0`** |
| Behavioural health check | Authentication, compatibility matrix, role guard, dashboard — in every run |
| **Rollback** | 17 → 15 in under a minute, health-checked, data untouched |

Rollback re-deploys an earlier immutable tag using the **same role** as a deployment. A recovery path
that differs from the normal path is one that has never really been tested, because it is only
exercised during incidents.

Details: [`13`](13-ansible-configuration.md) · [`14`](14-provisioning-rollback.md).

## 9. Results

| Measure | Target | Achieved |
|---|---|---|
| Compatibility correctness (S2) | 64/64 pairs | **64/64**, with the 27-compatible total also asserted |
| Eligibility correctness (S3) | 0 ineligible donors shortlisted | **0** — boundary tested at 89/90/91 days |
| Workflow safety (S4) | 0 illegal transitions accepted | **0** across every transition and role combination |
| Build reproducibility (S5) | Green from a clean clone | **Green** |
| Pipeline duration (S6) | < 6 min | **101 s** |
| Quality gate effectiveness (S7) | A failing journey stops deployment | **Demonstrated** — build #9 |
| Provisioning idempotency (S8) | `changed=0` on rerun | **`changed=0`** |
| Recovery (S9) | Rollback healthy in < 2 min | **< 1 min**, data intact |
| Manual deployment steps (S10) | 0 | **0** |

**Every one of the ten success criteria frozen in Task 1 was met.**

| | |
|---|---|
| Subject tasks | 15, each a tagged commit |
| Tests | 179 unit + 16 Selenium journeys |
| Pipeline stages | 11 |
| Defects found and fixed | 11 |
| Release tags | `v1.0.0` → `v1.3.0` |

## 10. Defects and troubleshooting

Eleven defects, each recorded with its **cause**, not merely its remedy
([`TROUBLESHOOTING.md`](TROUBLESHOOTING.md), [`backlog.md`](backlog.md)).

The four that say the most about the process:

| ID | Defect | Why it is instructive |
|---|---|---|
| **BUG-05** | The worklist showed MEDIUM above HIGH above CRITICAL | `urgency` is `@Enumerated(STRING)`, so `order by urgency desc` sorted the **text**. The unit tests asserted *which* requests came back and never in what order — and because the ordering lived in SQL, the H2 test agreed with production, both wrong in the same way |
| **BUG-07** | A DONOR signing in landed on HTTP 403 | Login used one fixed success URL the DONOR role may not open. Every unit test authenticated *directly as a role*; none walked the login form and followed the redirect |
| **BUG-06** | The request page offered an ADMIN-only action to STAFF | The service layer still refused it, so no data was at risk and **all 179 unit tests passed**. Only a browser could see it — this is the defect used to prove the quality gate |
| **BUG-11** | The hardened image could not open its own database | A named volume keeps the ownership it was created with, and Docker only copies the image directory's ownership into a **fresh** volume — so nothing in a Dockerfile can repair an existing one |

Two lessons generalised beyond this project:

- **Waiting for a condition that is already true is not waiting at all.** One Selenium journey failed
  three times for three different reasons; the third was a wait satisfied instantly by the *old* page,
  because the unfiltered list had the same results table as the filtered one.
- **Verify a deployment using facts the deployer owns, not features of the artefact being deployed.**
  The first rollback failed its verification because it asked the application what version it was —
  and the older image predated the endpoint that answers. A rollback that can only verify versions
  newer than the bug it is escaping is useless precisely when it is needed.

## 11. Limitations

1. **H2 is single-node.** Adequate for demonstrating persistence; PostgreSQL would be the first
   change for a multi-user deployment.
2. **Donor personal data is unencrypted at rest, with no access audit log.** Both are prerequisites
   for any real deployment handling identifiable medical data.
3. **Only the 90-day interval is modelled as eligibility.** Real deferral criteria include weight,
   haemoglobin, medication, recent travel and illness.
4. **"Same city" is a string comparison**, not a distance calculation.
5. **Ansible manages a Docker node on this machine.** Switching to a remote host over SSH is a
   one-line inventory change, because nothing in the plays refers to `localhost` — but it has not
   been exercised against a real remote host.
6. **The registry is local and unauthenticated.** Correct for an offline demonstration; a real
   deployment needs TLS and credentials.
7. **No static analysis stage** (SonarQube or similar) — omitted to keep the pipeline under the
   six-minute budget.
8. **Single environment.** There is no separate staging tier; the pipeline deploys to one place.

## 12. Future enhancements

Tracked in [`backlog.md`](backlog.md). In the order they would actually be done:

| Priority | Item |
|---|---|
| 1 | PostgreSQL with backups, replacing H2 (FE-10) |
| 2 | Encryption at rest and an access audit log for donor data (FE-11) |
| 3 | Static analysis in the pipeline (FE-14) |
| 4 | SMS/WhatsApp notification to matched donors (FE-01) |
| 5 | Geolocation and distance-based ranking (FE-02) |
| 6 | Blood **unit** inventory with component split and expiry (FE-03) |
| 7 | A staging environment and a blue-green release strategy (FE-13) |
| 8 | Kubernetes with horizontal scaling (FE-12) |

## 13. Conclusion

The application works and the business rules are correct, but the result worth stating is about the
pipeline.

**Pipeline build #9** is the whole argument in one artefact. Checkout, Build, Unit Test (179 green),
Package, Deploy and the behavioural Health Check all passed — and the build still went red, because a
browser found the request page offering an action to a user who was not permitted to take it. The
Promote stage was never entered, no image was published, and no container was replaced.

That is the difference between **having tests** and **having a gate**. A test suite tells you
something is wrong. A gate makes it impossible for the wrong thing to reach production. Arranging the
pipeline so that every promotion stage sits after the browser suite means no human has to remember to
check, and no flag has to be read correctly.

The same principle shows up throughout: images are tagged immutably so that rollback has something to
name; the deployment pulls from the registry so that a broken push cannot hide; Ansible is idempotent
so that running it during an incident is safe; and the rollback uses the same code path as a
deployment so that the recovery path is exercised every day rather than only during incidents.

Eleven defects were found during development, four of them by a browser after the unit suite was
already green. That number is the most honest measure of what this pipeline is worth.

---

**Repository:** see [`README.md`](../README.md) · **Demonstration:** `bash scripts/demo-all.sh` ·
**Viva preparation:** [`VIVA_GUIDE.md`](VIVA_GUIDE.md)
