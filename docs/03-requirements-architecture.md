# Task 3 — Requirements, Architecture and Technology Setup

**Deliverable:** SRS summary, use-case diagram, architecture diagram, data model / API list and
working local setup.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 30 September 2026 · **Tag:** `task-03`

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
