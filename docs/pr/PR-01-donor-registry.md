# PR-01 — Donor registry and matching engine

| | |
|---|---|
| **Branch** | `feature/donor-registry` → `develop` |
| **Author** | Ayush Mayekar (23102A0018) |
| **Opened** | 1 October 2026 |
| **Merged** | 1 October 2026, `--no-ff` |
| **Stories** | US-01, US-02, US-03, US-06 |
| **Closes** | #2, #3 |
| **Task** | 5 |
| **Commits** | 3 |

> This file is the permanent record of the pull request: description, review
> comments, responses and the merge. It is kept in the repository so the review
> trail survives independently of any hosting platform, and because the repository
> was local when the review took place.

---

## What this pull request does

Implements the first core workflow of the portal: register a donor, search the registry, and
produce the ranked list of donors who can actually be called for a patient of a given blood group.

The change is organised around one idea — **the rules belong to the domain, not to the screens**.
`BloodGroup` carries the red-cell compatibility matrix, `Donor` computes its own eligibility, and
`MatchingService` composes the three predicates and the ranking. Controllers bind input, call one
service method and pick a view. That is what makes 64 compatibility pairs and 8 eligibility
boundaries provable in milliseconds without a browser or a servlet container.

### Commits

| Commit | Contents |
|---|---|
| `39e120c` | `feat: add donor domain model with blood group compatibility` — `BloodGroup` with the matrix, `Donor` with eligibility, `DonorRepository`, 63 tests |
| `6b8c055` | `feat: add donor matching engine with ranked shortlist` — `MatchingService`, injected `Clock`, 21 tests |
| `8021da1` | `feat: add donor registry screens, search and match preview` — service, form object, controllers, JSON API, exception handler, views, seed data, 22 tests |

## Acceptance criteria and the tests that prove them

| Acceptance criterion | Proved by |
|---|---|
| AC-01.1 — a valid donor is saved and appears in the list | `DonorServiceTest$Create#createsDonor` |
| AC-01.2 — an invalid blood group is rejected | `CompatibilityMatrixTest$Parsing#rejectsUnknownText`, `DonorForm` constraint |
| AC-01.3 — a phone number that is not 10 digits is rejected | `DonorForm` `@Pattern`, exercised by Selenium J1 (Task 9) |
| AC-01.4 — a donor with no donation history is eligible | `DonorServiceTest$Create#newDonorWithNoHistoryIsEligible`, `DonorEligibilityTest#neverDonatedIsEligible` |
| AC-02.1 — filtering by group returns only that group | `DonorServiceTest$Search#filterByGroup` (6 cases) |
| AC-02.2 — group and city filters combine with AND | `DonorServiceTest$Search#filtersCombineWithAnd` |
| AC-02.3 — "available only" excludes unavailable donors | `DonorServiceTest$Search#availableOnlyExcludesUnavailable` |
| AC-02.4 — an empty result says so explicitly | `donors/list.html` `data-testid="no-donors"`, Selenium J2 (Task 9) |
| AC-03.1 — updating the city persists | `DonorServiceTest$Update#updatesCity` |
| AC-03.2 — recording a donation excludes the donor immediately | `DonorServiceTest$Update#recordDonationMakesIneligible` |
| AC-03.3 — a donation more than 90 days ago makes the donor eligible again | `DonorEligibilityTest#eligibilityBoundary` (8 cases) |
| AC-06.1 — a B+ request draws only B+, B−, O+, O− | `MatchingServiceTest$Predicates#onlyCompatibleGroupsAreShortlisted` |
| AC-06.2 — all 64 recipient/donor pairs are correct | `CompatibilityMatrixTest#allSixtyFourPairsAreCorrect` |
| AC-06.3 — a donor who donated 30 days ago is excluded | `MatchingServiceTest$Predicates#ineligibleDonorsAreExcluded` |
| AC-06.4 — an unavailable donor is excluded | `MatchingServiceTest$Predicates#unavailableDonorsAreExcluded` |
| AC-06.5 — the same-city donor ranks higher | `MatchingServiceTest$Ranking#sameCityRanksHigher` |
| AC-06.6 — an exact group match ranks higher than a merely compatible one | `MatchingServiceTest$Ranking#exactGroupMatchRanksFirst` |
| AC-06.7 — a shortlist over 500 donors in under 2 seconds | `MatchingServiceTest#performanceOverFiveHundredDonors` |
| AC-06.8 — the ranked shortlist is displayed with group, city and eligibility | `match.html`, Selenium J2 (Task 9) |

**Test count:** 106 green (63 domain + 21 matching + 22 registry). `mvn clean verify` green.

## Design decisions taken, and why

1. **Compatibility lives on the `BloodGroup` enum, not in a service.** Compatibility is a property
   of blood groups, not of a workflow. Groups are modelled by the antigens they carry (A, B, Rh),
   so the 27 compatible pairs fall out of two rules instead of being a hand-written 64-cell table
   that could be mistyped.
2. **Eligibility is computed, never stored.** A stored `eligible` column would need a nightly job
   and would be wrong the moment the clock passed midnight.
3. **`Clock` is injected as a bean.** Services take a `Clock` instead of calling `LocalDate.now()`,
   which is the only way to assert the boundary at *exactly* 89 and 90 days.
4. **Exact group match outranks mere compatibility.** A clinical-stewardship decision, not a
   technical one: a universal O− donor should not be spent on an O+ patient while an O+ donor is
   available.
5. **Longest-rested donor ranks above a recently-eligible one.** Spreads the load across the
   register rather than repeatedly calling the same willing people.
6. **`countMatches` delegates to `findMatches`.** Two independent supply calculations is exactly
   how defect BUG-02 happened; one code path makes a disagreement impossible.
7. **Bootstrap is vendored, not loaded from a CDN.** The practical demonstration must work offline.
8. **Views carry `data-testid` attributes.** Selenium then binds to stable hooks instead of CSS
   classes, so restyling cannot break the suite.

## How to verify locally

```bash
source scripts/env.sh
mvn clean verify
java -jar target/bloodbank.war

# registry and search
open http://localhost:9090/donors
open 'http://localhost:9090/donors?group=O-&city=Thane&availableOnly=true'

# the ranked shortlist
open 'http://localhost:9090/match?group=B%2B&city=Thane'
curl -s 'http://localhost:9090/api/donors/eligible?group=B%2B&city=Thane' | jq .
```

---

## Review

Reviewer: Ayush Mayekar · self-review against the checklist in `CONTRIBUTING.md` §3.1
Review date: 1 October 2026

### Checklist outcome

| Item | Result |
|---|---|
| Every acceptance criterion met and tested | **Pass** — table above; AC-01.3, AC-02.4 and AC-06.8 are UI-level and deferred to Selenium in Task 9, which is noted rather than claimed as done here |
| Business rules in the service layer or a domain enum | **Pass** |
| Boundary values tested | **Pass** — 0, 1, 45, 89, 90, 91, 120, 400 days |
| No hard-coded host, port, path or credential | **Pass** |
| Authorisation enforced server-side | **Deferred** — authentication arrives in Task 6; raised as comment R4 below |
| Error semantics follow `docs/API.md` §5 | **Pass** after R2 |
| Documentation updated in the same commit | **Pass** |
| `mvn clean verify` green | **Pass** — 106 tests |
| Commit messages follow §2 | **Pass** |

### Review comments

**R1 — `MatchingService.findMatches` filters eligibility in Java, not in SQL. Is that going to scale?**
*Response:* Intentional. Eligibility depends on today's date, so expressing it as a SQL predicate
would need either a date parameter threaded through every query or a constant that becomes wrong at
midnight. The repository narrows to compatible groups in SQL — which is the selective filter — and
the service applies the date rule. `MatchingServiceTest#performanceOverFiveHundredDonors` measures
the real cost: well inside the 2-second budget in NFR-01. If the register grows past a few thousand
donors, the correct fix is a `lastDonationDate <= :cutoff` parameter, not a stored flag. Recorded as
a note in `docs/ARCHITECTURE.md` §5.

**R2 — A duplicate phone number returned HTTP 500 on the first run of the API.**
*Response:* Correct, and fixed in `8021da1`. `DuplicatePhoneException` is now mapped to **409** by
`GlobalExceptionHandler`, and unknown ids to **404**, matching `docs/API.md` §5. A 500 for a
foreseeable conflict is a contract violation, not merely an untidy message.

**R3 — The `/match` screen threw a 500 with `Property or field 'rhPositive' cannot be found on null`.**
*Response:* Fixed. The cause was Thymeleaf attribute precedence: `th:replace` has a higher
precedence than `th:each`, so putting both on the same element ran the fragment replacement before
the iteration variable existed. `th:each` now sits on an outer element with `th:replace` on the
inner span. Recorded in `docs/TROUBLESHOOTING.md` because it is a non-obvious failure mode that
will recur.

**R4 — Anyone can open `/donors` and edit any record; there is no authentication.**
*Response:* Accepted and out of scope for this pull request. `SecurityConfig` currently permits all
requests, which is the Task 4 skeleton state. Authentication, BCrypt password encoding and the
three roles are US-09 and arrive with the role-based workflow in Task 6 — they are only meaningful
once there are transitions to restrict. Tracked as issue #5.

**R5 — The seed data looks arbitrary.**
*Response:* It is deliberately not arbitrary, and the commit now says so. Sixteen donors cover all
eight groups and four cities, and the donation dates are chosen to put donors at **90 days** (just
eligible), **89 days** (just ineligible), never donated, and recently donated, so the eligibility
boundary is visible on screen during the demonstration. Dates are relative to today, so the dataset
does not rot. Two donors are flagged unavailable so the availability predicate is also visible.

**R6 — `findMatches` is called twice when the dashboard renders: once for the list and once for the count.**
*Response:* Acknowledged. For the MVP's data volume this is a few milliseconds and the correctness
benefit — a dashboard that cannot disagree with the shortlist — is worth more than the saving. If
the dashboard becomes slow, the fix is to compute all eight groups in a single pass, which is
recorded as a future item rather than guessed at now.

### Verdict

**Approved.** Merge with `--no-ff` so the feature boundary stays visible in history.

```bash
git checkout develop
git merge --no-ff feature/donor-registry -m "Merge PR-01: donor registry and matching engine"
git tag -a task-05 -m "Task 5 - Feature Development with Branching"
git branch -d feature/donor-registry
```
