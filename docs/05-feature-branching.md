# Task 5 — Feature Development with Branching

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 1 October 2026 · **Branch:** `feature/donor-registry` → `develop`

| Required deliverable | Delivered as |
|---|---|
| Working feature 1 | Donor registry (create / view / update / search) **and** the donor matching engine — the portal's core logic |
| Feature branch | `feature/donor-registry`, branched from `develop`, 4 commits |
| Pull request | [`docs/pr/PR-01-donor-registry.md`](pr/PR-01-donor-registry.md) — full description, acceptance-criteria table, design decisions |
| Review comments | Six comments (R1–R6) with responses in the same document; two caused code changes, two were explicitly deferred |
| Merge evidence | `--no-ff` merge commit into `develop`, tagged `task-05`; `git log --graph` output in §4 below |

---

## 1. Git operations used, in the order they were used

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
git add src/main/java/com/bloodbank/service src/main/java/com/bloodbank/config/ClockConfig.java \
        src/test/java/com/bloodbank/service
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
git log --graph --oneline -12
```

### Why `--no-ff`

Without it, Git would fast-forward `develop` to the tip of the feature branch and the branch would
vanish from history: `git log` would show four unrelated-looking commits with no record that they
formed one reviewed unit. `--no-ff` forces a merge commit, so `git log --graph` permanently shows
where the feature started and ended, and `git revert -m 1 <merge>` can undo the whole feature as
one operation. Both properties are the reason the pull-request template mandates it.

## 2. The feature delivered

**Donor registry (US-01, US-02, US-03)** — register a donor with validated name, blood group,
10-digit phone, email and city; list, view and update donors; search by blood group, city and
availability with the filters combined using logical AND.

**Matching engine (US-06)** — the project's actual domain logic. A donor matches when all three
independent predicates hold:

| Predicate | Rule | Where it lives |
|---|---|---|
| Compatible | The recipient's group can receive the donor's red cells | `BloodGroup.canReceiveFrom` |
| Available | The donor's availability flag is set | `Donor.isCallable` |
| Eligible | At least 90 days since the last donation | `Donor.isEligible` |

and the matches are then ranked: **exact group → same city → longest rested → name**. Each ranking
rule is a decision, not an accident, and each is justified in
[PR-01 §design decisions](pr/PR-01-donor-registry.md).

## 3. Review outcome

Two review comments produced code changes before the merge:

| Comment | Problem | Fix |
|---|---|---|
| **R2** | A duplicate phone number returned HTTP **500** from the API | `DuplicatePhoneException` mapped to **409** and `DonorNotFoundException` to **404** in `GlobalExceptionHandler`, matching the contract in `docs/API.md` §5. A 500 for a foreseeable conflict is a contract violation, not just an untidy message |
| **R3** | `/match` returned 500: `Property or field 'rhPositive' cannot be found on null` | Thymeleaf attribute precedence: `th:replace` outranks `th:each`, so both on one element ran the fragment before the loop variable existed. `th:each` moved to an outer element. Recorded in `docs/TROUBLESHOOTING.md` |

Two were accepted as deferred, with the issue that tracks each: **R4** (no authentication yet —
US-09, Task 6) and **R6** (the dashboard computes matches twice — acceptable at MVP data volume).

## 4. Merge evidence

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
* ff6245d (tag: task-02) Task 2: agile plan, backlog, Kanban board, DoD and DevOps workflow
* dcd484f (tag: task-01) Task 1: define problem, stakeholders and frozen MVP scope
```

The actual commit hashes on this repository are printed by
`git log --graph --oneline --decorate -12`; the shape of the graph is what matters, and the diamond
is the permanent record of the feature boundary.

## 5. Verification

```bash
source scripts/env.sh
mvn clean verify                     # 106 tests green
java -jar target/bloodbank.war

# AC-01.1 register, AC-02.x search
open http://localhost:9090/donors
open 'http://localhost:9090/donors?group=O-&city=Thane&availableOnly=true'

# AC-06.x the ranked shortlist, and the same result through the API
open 'http://localhost:9090/match?group=B%2B&city=Thane'
curl -s 'http://localhost:9090/api/donors/eligible?group=B%2B&city=Thane' | jq '.[0:3]'
```

Expected shortlist for a **B+** patient in **Thane**, against the seeded registry:

| Rank | Donor | Group | City | Why |
|---|---|---|---|---|
| 1 | Sneha Patil | B+ | Thane | exact group **and** same city |
| 2 | Vikram Shinde | B+ | Pune | exact group |
| 3 | Rohit Deshmukh | O− | Thane | compatible, same city |
| 4 | Tanmay Pawar | O+ | Thane | compatible, same city, rested less than Rohit |
| 5 | Imran Shaikh | O+ | Pune | compatible, never donated |

Arjun Mehta (A+) and Zoya Qureshi (AB+, donated 30 days ago) are correctly absent: the first is
incompatible, the second ineligible.

## 6. Verified test results

```
Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Suite | Tests | Covers |
|---|---|---|
| `CompatibilityMatrixTest` | 47 | All 64 recipient/donor pairs against an independently written reference matrix, the 27-pair total, universal donor and recipient, both encoded rules, and input parsing |
| `DonorEligibilityTest` | 15 | The 90-day boundary at 0, 1, 45, 89, 90, 91, 120 and 400 days, never-donated, availability interaction, and the effect of recording a donation |
| `MatchingServiceTest` | 21 | Each predicate separately, each ranking rule separately, the full priority order with all rules in play, empty and all-ineligible registries, null handling, the dashboard contract and 500-donor performance |
| `DonorServiceTest` | 22 | Search filter combinations over a real in-memory database, create, duplicate phone, update, availability, donation recording, unknown id |
| `BloodBankApplicationTest` | 3 | Context loads, landing page, open health endpoint |
