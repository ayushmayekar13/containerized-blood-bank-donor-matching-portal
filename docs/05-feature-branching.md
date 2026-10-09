# Task 5 — Feature Development with Branching

**Deliverable:** working feature 1, feature branch, pull request, review comments and merge
evidence.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 1 October 2026 · **Tag:** `task-05` · **Pull request:** [PR-01](pr/PR-01-donor-registry.md)

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
