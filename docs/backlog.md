# Product Backlog — Containerized Blood Bank Donor Matching Portal

Owner: Ayush Mayekar (23102A0018) · Release: MVP `v1.0.0` → `v1.3.0` · Sprint: 29 Sep – 9 Oct 2026

Priority uses MoSCoW (**M**ust / **S**hould / **C**ould / **W**on't-this-release).
Status reflects the end of the sprint. Full acceptance criteria live in
[02-agile-planning.md](02-agile-planning.md).

## Sprint backlog (committed)

| ID | User story (as a … I want … so that …) | Epic | Pri | Pts | Task | Status |
|---|---|---|---|---|---|---|
| US-01 | As Staff I want to register a donor with group, contact and city so the donor can be found later | A Donor registry | M | 3 | 5 | Done |
| US-02 | As Staff I want to search donors by group, city and availability so I know who to call now | A | M | 3 | 5 | Done |
| US-03 | As Staff I want to update a donor and record their last donation so eligibility is automatic | A | M | 2 | 5 | Done |
| US-04 | As a Donor I want to see my profile and control my availability so I am not called while away | A | S | 2 | 6 | Done |
| US-05 | As Staff I want to record a blood request so it has an owner and a tracked state | B Requests | M | 3 | 6 | Done |
| US-06 | As Staff I want a ranked list of compatible, eligible, available donors so I stop guessing | B | M | 8 | 5 | Done |
| US-07 | As Admin I want only the right role to advance a request so outcomes are accountable | B | M | 5 | 6 | Done |
| US-08 | As Admin I want a dashboard of supply vs demand with shortage flags | C Visibility | M | 3 | 6 | Done |
| US-09 | As Operator I want authentication with three roles so workflow permissions are enforceable | C | M | 3 | 6 | Done |
| US-10 | As Operator I want a reproducible Maven build producing a deployable WAR | D DevOps | M | 3 | 3,4 | Done |
| US-11 | As Operator I want Jenkins to build and test automatically on commit | D | M | 5 | 7 | Done |
| US-12 | As Operator I want the pipeline as code in a Jenkinsfile with a parameterised environment | D | M | 5 | 8 | Done |
| US-13 | As Operator I want failing UI tests to stop deployment | D | M | 5 | 9,10 | Done |
| US-14 | As Operator I want versioned images in a registry and a fresh container deployed on success | D | M | 5 | 11,12 | Done |
| US-15 | As Operator I want the environment as code, idempotent, health-checked and roll-back-able | D | M | 5 | 13,14 | Done |

Committed: **15 stories / 64 points**. Plus 18 points of non-story sprint work (Tasks 1, 2 and 15
documentation) = 82 points of planned effort.

## Defects raised during the sprint

| ID | Title | Found by | Task | Resolution |
|---|---|---|---|---|
| BUG-01 | Donor who donated 30 days ago appears in match shortlist | Unit test on eligibility boundary | 5 | Eligibility comparison was `>` on days instead of `>=` against the 90-day cut-off; corrected with boundary tests at 89/90/91 days |
| BUG-02 | Dashboard shortage flag counted unavailable donors as supply | Selenium journey 3 | 6 | Shortage calculation now uses the same eligible-and-available predicate as the matching engine |
| BUG-03 | Staff could fulfil a `NEW` request directly from the request detail page | Deliberate defect for the Task 10 quality-gate demonstration | 10 | Role and transition guard restored in the service layer; pipeline turned red first, then green after the fix |

BUG-03 is intentional: it is the defect introduced in Task 10 to prove that the Selenium quality
gate stops a deployment, and its correction commit is part of that task's evidence.

## Product backlog (not in this release — future enhancements)

| ID | Item | Pri | Rationale for deferring |
|---|---|---|---|
| FE-01 | SMS/WhatsApp/email notification to matched donors | S | Needs a paid gateway and consent capture; out of local-only MVP |
| FE-02 | Geolocation and distance-based ranking instead of same-city | S | Needs a geocoding service; city match is a sufficient proxy for the MVP |
| FE-03 | Blood **unit** inventory with component split and expiry tracking | S | Doubles the data model; the MVP matches donors, it does not manage stock |
| FE-04 | Donation camp scheduling and donor invitations | C | Separate workflow, no overlap with matching |
| FE-05 | Donor self-registration with OTP verification | C | Needs an OTP provider and anti-abuse work |
| FE-06 | Multi-blood-bank tenancy with per-tenant data isolation | C | Architectural change (tenant key on every entity) |
| FE-07 | Forecasting demand per blood group from historical requests | C | Needs a year of real data |
| FE-08 | Mobile application for donors | C | New delivery channel; the portal is responsive in the meantime |
| FE-09 | HL7/FHIR integration with hospital information systems | W | Requires a partner hospital and certification |
| FE-10 | External PostgreSQL cluster with replication and backups | S | H2 on a named volume is enough to demonstrate persistence; documented as the first production change |
| FE-11 | Encryption at rest and access audit log for donor personal data | S | Required before any real deployment; recorded as a known limitation in Task 1 §7 (C9) |
| FE-12 | Kubernetes deployment with horizontal scaling and rolling updates | C | Beyond the course scope, which specifies Docker and Ansible |
| FE-13 | Blue-green or canary release strategy | C | Current strategy is recreate-with-rollback, which the syllabus asks for |
| FE-14 | SonarQube static analysis stage in the pipeline | S | Natural next pipeline stage; omitted to keep build time under the 6-minute target |
| FE-15 | PDF/CSV export of the matching report | S | Kept as the open `feature/export-report` branch so a live pull request can be demonstrated |

## Backlog hygiene rules used

1. Nothing enters the sprint backlog without acceptance criteria.
2. Anything discovered mid-sprint goes to the product backlog, never silently into the current
   task — this is what kept the 15-task plan honest.
3. Every defect gets an ID and a failing test before a fix is written.
4. A story is only "Done" against the [Definition of Done](definition-of-done.md), not when the
   code merely compiles.
