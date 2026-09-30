# Software Requirements Specification (summary)

**System:** Containerized Blood Bank Donor Matching Portal
**Author:** Ayush Mayekar · Roll No. 23102A0018 · BE CMPN Div. A
**Version:** 1.0 · **Date:** 30 September 2026
**Basis:** IEEE 830 structure, summarised to the depth appropriate for a 15-task MVP

---

## 1. Introduction

### 1.1 Purpose
This document specifies the requirements for the Blood Bank Donor Matching Portal MVP: a web
application that maintains a donor registry, records blood requests, ranks compatible and eligible
donors for each request, enforces a role-based request workflow and reports supply against demand.
It is the contract against which Tasks 5, 6, 9 and 10 are verified.

### 1.2 Scope
In scope: donor CRUD and search, blood request CRUD and search, the donor matching engine, the
role-based status workflow, the summary dashboard, authentication with three roles, and a REST API
mirroring the above. Out of scope: notifications, blood **unit** inventory, camp scheduling,
geolocation, multi-tenancy and hospital-system integration (see `docs/backlog.md` → future items).

The system **matches donors**; it does not screen, label or certify blood units.

### 1.3 Definitions

| Term | Meaning |
|---|---|
| Donor | A registered person willing to donate whole blood |
| Blood group | One of `A+ A- B- B+ AB+ AB- O+ O-` (ABO antigen + Rh factor) |
| Compatible donor | A donor whose red cells may be transfused to the recipient's group, per §3.2 |
| Eligible donor | A donor whose last donation is ≥ 90 days ago, or who has never donated |
| Available donor | A donor whose `available` flag is true |
| Match | A donor that is compatible **and** eligible **and** available |
| Request | A recorded need for N units of a blood group for a named patient |
| Transition | A change of a request's status, permitted only to specific roles |
| MVP | The frozen feature set F1–F11 of `docs/01-problem-definition.md` §8 |

### 1.4 References
Task sheet `CMPN-A.xlsx`, row 22 · `docs/01-problem-definition.md` ·
`docs/02-agile-planning.md` (acceptance criteria) · `docs/ARCHITECTURE.md` · `docs/API.md`

---

## 2. Overall description

### 2.1 Product perspective
A standalone, self-contained web application. It is delivered as a WAR deployed into Tomcat inside
a Docker container, with an embedded H2 database on a named volume. It has no external
dependencies at run time — deliberate, so that the whole DevOps pipeline is reproducible on one
laptop and demonstrable offline.

### 2.2 User classes and characteristics

| Class | Technical skill | Frequency of use | Privileges |
|---|---|---|---|
| `STAFF` (blood bank technician) | Basic computer literacy | Many times per day | Donor CRUD, request create/view/search, run matching, `NEW → MATCHED` |
| `ADMIN` (medical officer) | Basic computer literacy | Several times per day | Everything STAFF can do, plus `MATCHED → FULFILLED`, `* → REJECTED`, dashboard |
| `DONOR` | Smartphone-level literacy | Occasional | View own record, toggle own availability |
| Anonymous | — | — | Login page and `/actuator/health` only |

### 2.3 Operating environment
Java 21 · Spring Boot 3.3 (WAR) · Tomcat 10.1 · H2 2.x file database · Docker 28 engine ·
any modern browser. Build on Maven 3.9+; CI on Jenkins LTS; provisioning by Ansible.

### 2.4 Design and implementation constraints
- Maven must be the build tool and Tomcat the deployment target (course requirement).
- The deployable artefact must be a single archivable WAR.
- No CDN or internet dependency at run time (offline demonstration).
- Host ports: Jenkins 8090, application 9090, registry 5001 (8080 is occupied on the host).
- Business rules must live in the service layer so they are unit-testable without a browser.

### 2.5 Assumptions and dependencies
- One blood bank (single tenant); all users share one registry.
- Blood group recorded at registration is correct; the portal does not re-type blood.
- The 90-day whole-blood interval is the only eligibility rule modelled; weight, haemoglobin and
  medical deferrals are out of MVP scope.
- Users are created by the operator; there is no self-registration in the MVP.

---

## 3. Specific requirements

### 3.1 Functional requirements

Each requirement is traceable to a user story (`docs/02-agile-planning.md` §3) and to the test that
proves it. Priority: **M**ust / **S**hould.

| ID | Requirement | Story | Pri | Verified by |
|---|---|---|---|---|
| FR-01 | The system shall allow STAFF/ADMIN to create a donor with full name, blood group, 10-digit phone, email and city | US-01 | M | `DonorValidationTest`, Selenium J1 |
| FR-02 | The system shall reject a donor whose blood group is not one of the eight valid values | US-01 | M | `DonorValidationTest` |
| FR-03 | The system shall list all donors with blood group, city, availability and next-eligible date | US-01 | M | Selenium J1 |
| FR-04 | The system shall allow searching donors by blood group, city and availability, combined with logical AND | US-02 | M | `DonorSearchTest`, Selenium J2 |
| FR-05 | The system shall display an explicit "no donors found" message when a search returns nothing | US-02 | M | Selenium J2 |
| FR-06 | The system shall allow STAFF/ADMIN to update any donor field including availability and last-donation date | US-03 | M | Selenium J1 |
| FR-07 | The system shall treat a donor with no last-donation date as eligible | US-01 | M | `EligibilityTest` |
| FR-08 | The system shall treat a donor as eligible only when the last donation is **at least 90 days** before today | US-03 | M | `EligibilityTest` (89/90/91-day boundaries) |
| FR-09 | The system shall allow a DONOR to view only their own record and toggle only their own availability | US-04 | S | `RoleAccessTest` |
| FR-10 | The system shall allow STAFF/ADMIN to create a blood request with patient name, blood group, units (1–10), hospital, city and urgency (`LOW/MEDIUM/HIGH/CRITICAL`) | US-05 | M | `RequestValidationTest`, Selenium J3 |
| FR-11 | The system shall set a new request's status to `NEW` and stamp its creation time | US-05 | M | `RequestWorkflowTest` |
| FR-12 | The system shall allow searching requests by status and blood group | US-05 | M | `RequestSearchTest` |
| FR-13 | The system shall compute, for a given request, the list of **matching** donors — compatible ∧ eligible ∧ available | US-06 | M | `MatchingEngineTest` |
| FR-14 | The system shall determine red-cell compatibility according to the matrix in §3.2 for all 64 recipient/donor pairs | US-06 | M | `CompatibilityMatrixTest` (64 cases) |
| FR-15 | The system shall rank matches: exact blood-group match first, then same city, then longest time since last donation | US-06 | M | `MatchRankingTest` |
| FR-16 | The system shall return a match shortlist for 500 donors within 2 seconds | US-06 | M | `MatchingPerformanceTest` |
| FR-17 | The system shall permit only these transitions: `NEW→MATCHED` (STAFF/ADMIN), `NEW→REJECTED` (ADMIN), `MATCHED→FULFILLED` (ADMIN), `MATCHED→REJECTED` (ADMIN) | US-07 | M | `RequestWorkflowTest`, Selenium J4/J5 |
| FR-18 | The system shall refuse every other transition, including any transition out of `FULFILLED` or `REJECTED` | US-07 | M | `RequestWorkflowTest` |
| FR-19 | The system shall record the matched donor against the request on `NEW→MATCHED` | US-07 | M | `RequestWorkflowTest` |
| FR-20 | The system shall set the matched donor's last-donation date to today on `MATCHED→FULFILLED`, making that donor ineligible for 90 days | US-07 | M | `RequestWorkflowTest`, Selenium J5 |
| FR-21 | The system shall require a rejection reason on any transition to `REJECTED` | US-07 | S | `RequestWorkflowTest` |
| FR-22 | The system shall present a dashboard with the count of matching donors per blood group and the count of requests per status | US-08 | M | `DashboardServiceTest`, Selenium J3 |
| FR-23 | The system shall flag as a shortage any blood group having at least one open request and zero matching donors | US-08 | M | `DashboardServiceTest` |
| FR-24 | The system shall require authentication for all pages except the login page, static assets and `/actuator/health` | US-09 | M | `SecurityConfigTest`, Selenium J4 |
| FR-25 | The system shall expose a REST API under `/api/**` mirroring FR-01 to FR-23, returning JSON | US-09 | M | `ApiSmokeTest`, `scripts/health-check.sh` |
| FR-26 | The system shall expose `/actuator/health` without authentication for container and Ansible health checks | US-09 | M | `scripts/health-check.sh` |

### 3.2 Red-cell compatibility matrix (the rule behind FR-14)

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

Rules encoded: a recipient accepts a donor when the donor's ABO antigens are a subset of the
recipient's, and a Rh-negative recipient accepts only Rh-negative donors. O− is therefore the
universal donor (8 recipients) and AB+ the universal recipient (8 donors); the matrix has 27 of 64
pairs compatible, which is itself asserted as a test.

### 3.3 Non-functional requirements

| ID | Category | Requirement | Verified by |
|---|---|---|---|
| NFR-01 | Performance | A donor search or match shortlist over 500 donors completes in < 2 s | `MatchingPerformanceTest` |
| NFR-02 | Performance | Any page renders in < 1 s on the demonstration laptop | manual timing, recorded in `evidence/` |
| NFR-03 | Reliability | The container restarts automatically on failure (`--restart unless-stopped`) | Ansible `site.yml`, Task 14 |
| NFR-04 | Reliability | Donor and request data survive container replacement and image upgrade | named volume `bloodbank-data`, Task 12 |
| NFR-05 | Recoverability | A rollback to the previous image tag completes and passes a health check in < 2 min | `ansible/rollback.yml`, Task 14 |
| NFR-06 | Security | Passwords are never stored in plain text (BCrypt) | `SecurityConfigTest` |
| NFR-07 | Security | Authorisation is enforced server-side in the service layer, not only by hiding UI controls | `RoleAccessTest` |
| NFR-08 | Security | Forms are CSRF-protected | Spring Security defaults, asserted in `SecurityConfigTest` |
| NFR-09 | Maintainability | Business rules are unit-testable without a servlet container or browser | package structure, Task 3 §architecture |
| NFR-10 | Portability | The same WAR runs via `java -jar`, in Tomcat, and in the Docker image with no rebuild | Task 8 and Task 11 evidence |
| NFR-11 | Observability | `/actuator/health` reports application and database status | `scripts/health-check.sh` |
| NFR-12 | Usability | The portal is usable at 1280×800 with no horizontal scrolling and works with no internet access | vendored CSS, Selenium runs headless at 1280×800 |
| NFR-13 | Deployability | Commit to deployed container takes < 6 min with zero manual steps | Jenkins pipeline duration, Task 12 |
| NFR-14 | Testability | Every functional requirement maps to at least one automated test | traceability matrix §4 |
| NFR-15 | Auditability | Every request records who created it and the time of each transition | `RequestWorkflowTest` |

### 3.4 External interface requirements

- **User interface** — server-rendered Thymeleaf pages, vendored Bootstrap 5 (no CDN).
  Screens: Login · Dashboard · Donor list/search · Donor form · Request list/search · Request
  form · Request detail with match shortlist · My profile (DONOR).
- **API** — JSON over HTTP under `/api`, full list in [API.md](API.md).
- **Database** — H2 in file mode at `/opt/bloodbank/data/bloodbank.mv.db` inside the container,
  on volume `bloodbank-data`; schema managed by Hibernate DDL with a seed `data.sql`.
- **Operations interface** — `/actuator/health` (unauthenticated), container `HEALTHCHECK`,
  Jenkins JUnit/artefact publishing, Ansible `community.docker` modules.

---

## 4. Requirements traceability matrix

| Requirement | Story | Task delivered | Automated test | Evidence |
|---|---|---|---|---|
| FR-01…FR-09 | US-01…US-04 | 5 | `DonorValidationTest`, `DonorSearchTest`, `EligibilityTest`, `RoleAccessTest` | Selenium J1, J2 |
| FR-10…FR-12 | US-05 | 6 | `RequestValidationTest`, `RequestSearchTest` | Selenium J3 |
| FR-13…FR-16 | US-06 | 5 | `CompatibilityMatrixTest` (64 pairs), `MatchingEngineTest`, `MatchRankingTest`, `MatchingPerformanceTest` | `evidence/task-09-selenium/` |
| FR-17…FR-21 | US-07 | 6 | `RequestWorkflowTest` | Selenium J4, J5 |
| FR-22…FR-23 | US-08 | 6 | `DashboardServiceTest` | Selenium J3 |
| FR-24…FR-26 | US-09 | 6 | `SecurityConfigTest`, `ApiSmokeTest` | `scripts/health-check.sh` output |
| NFR-01, NFR-14 | US-06, US-10 | 5, 9 | `MatchingPerformanceTest`, full suite | Surefire/Failsafe reports |
| NFR-03…NFR-05, NFR-13 | US-14, US-15 | 12, 14 | Ansible health check + idempotency + rollback | `evidence/task-14-ansible/` |

## 5. Known limitations recorded against this SRS

1. Only the 90-day interval is modelled as eligibility; real deferral criteria are far wider.
2. Donor personal data is stored unencrypted on a local volume (see C9 in Task 1); encryption at
   rest and an access audit log are prerequisites for any real deployment.
3. Users are provisioned by the operator; there is no password-reset or self-registration flow.
4. H2 is single-node; concurrent writes at real blood-bank scale would need PostgreSQL (FE-10).
5. "Same city" is a string comparison, not a distance calculation (FE-02).
