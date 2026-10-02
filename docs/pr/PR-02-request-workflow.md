# PR-02 — Blood requests, role-based workflow, dashboard and authentication

| | |
|---|---|
| **Branch** | `feature/request-workflow` → `develop` |
| **Author** | Ayush Mayekar (23102A0018) |
| **Opened** | 2 October 2026 |
| **Merged** | 2 October 2026, `--no-ff`, after resolving a conflict with `develop` |
| **Stories** | US-04, US-05, US-07, US-08, US-09 |
| **Closes** | #4, #5 |
| **Task** | 6 |
| **Commits** | 3 (two feature commits + one merge resolving the conflict) |

---

## What this pull request does

Completes the MVP. After PR-01 the portal could register donors and rank them; it had no requests,
no workflow, no dashboard and no authentication. This adds all four, which together make the
remaining nine DevOps tasks meaningful — a pipeline that deploys an application with no
authorisation rules cannot demonstrate a quality gate that catches an authorisation regression.

| Added | Requirement |
|---|---|
| `BloodRequest`, `RequestStatus`, `Urgency`, repository, search | FR-10 to FR-12 |
| Role-based status workflow `NEW → MATCHED → FULFILLED / REJECTED` | FR-17 to FR-21 |
| Summary dashboard with shortage flags | FR-22, FR-23 |
| Authentication, three roles, two security filter chains | FR-24 to FR-26 |
| Donor self-service page | FR-09 |

## Acceptance criteria and the tests that prove them

| Acceptance criterion | Proved by |
|---|---|
| AC-04.1 — a Donor sees their own record, not the registry | `SecurityRoleTest$BrowserChain#donorCannotSeeTheRegistry`, `#donorIsPermittedAtOwnProfile` |
| AC-04.2 — a Donor editing another donor is refused | `ProfileController` resolves the id from the username; `SecurityRoleTest` |
| AC-05.1 — a new request is `NEW` with a creation timestamp | `RequestWorkflowTest$Creation#startsInNew` |
| AC-05.2 — units outside 1–10 are rejected | `RequestForm` constraints; `SecurityRoleTest#validationFailureIs400WithFieldErrors` |
| AC-07.1 — Staff may move `NEW → MATCHED` and the donor is recorded | `RequestWorkflowTest$LegalTransitions#staffMayMatch` |
| AC-07.2 — Admin may fulfil, and the donor's donation date is stamped | `#adminMayFulfilAndTheDonorBecomesIneligible` |
| AC-07.3 — Admin may reject with a reason | `#adminMayRejectNew`, `#adminMayRejectMatched` |
| **AC-07.4 — Staff may NOT fulfil** | `RequestWorkflowTest$RoleGuards#staffMayNotFulfil`, `SecurityRoleTest#staffFulfilIs403` |
| AC-07.5 — `FULFILLED` and `REJECTED` are terminal | `$IllegalTransitions#fulfilledIsTerminal`, `#rejectedIsTerminal` |
| AC-07.6 — a Donor can perform no transition | `$RoleGuards#donorCanDoNothing` (3 cases) |
| AC-08.1 — callable donors per blood group | `DashboardServiceTest#countsPerGroup` |
| AC-08.2 — requests per status | `#countsPerStatus` |
| AC-08.3 — a group with demand and no supply is flagged | `#shortageIsFlagged` |
| AC-09.1 — unauthenticated requests are redirected to login | `SecurityRoleTest$BrowserChain#protectedScreensRedirect` (6 paths) |
| AC-09.3 — `/actuator/health` is reachable unauthenticated | `#healthIsOpenAndReportsUp` |

**Test count:** 178 green, up from 106.

## Design decisions taken, and why

1. **Two security filter chains, not one.** `/api/**` gets HTTP Basic, stateless, CSRF disabled →
   an unauthenticated call is **401**, which a script can act on. Everything else gets form login
   with a session and CSRF intact → an unauthenticated request is **redirected** to `/login`, which
   is what a browser needs. A single chain with Basic enabled made every screen answer 401 with a
   browser pop-up instead of showing the login page; the split is the fix, not a flourish.
   CSRF is safe to disable on the API chain *only* because it is stateless and
   credential-per-request: there is no ambient session for a third-party page to ride on, which is
   the attack CSRF protection exists to stop.
2. **Two separate guards per transition, checked in a fixed order.** "Is the transition legal?" is
   a question about the state machine (**409**). "May this role perform it?" is authorisation
   (**403**). Staff attempting `NEW → FULFILLED` violates both; the **role is checked first**,
   because telling someone a transition is impossible when they were never allowed to attempt it is
   misleading.
3. **Services take an `Actor`, not Spring Security's `SecurityContextHolder`.** The workflow rules
   are then unit-testable with no mock authentication, and authorisation lives in the service layer
   where the API cannot bypass it (NFR-07). Hiding a button is presentation, not security.
4. **The screen's buttons are derived from `allowedNext()` plus the role.** The UI therefore cannot
   offer a transition the service would refuse. The reverse is deliberately not true: the service
   refuses a request that bypasses the screen entirely.
5. **A Staff user is told why the fulfil action is absent.** An action that silently disappears
   reads as a bug; a sentence explaining that only an ADMIN may confirm a donation does not.
6. **Fulfilling stamps the donation in the same transaction as the transition.** A donation can
   never be recorded without the request advancing, nor the request advance without the donor
   becoming ineligible for 90 days.
7. **Assigning a donor re-validates the match rather than trusting the caller**, and returns
   **422** — well-formed input that breaks a business rule is neither a 400 nor a 409.
8. **Urgency affects display order only, never matching.** Relaxing compatibility or eligibility
   under time pressure is precisely the shortcut the portal exists to prevent, so a CRITICAL
   request draws the same shortlist as a LOW one.
9. **Accounts come from configuration, BCrypt-hashed at startup.** No password in Java source
   (NFR-06). The login page lists them on purpose: the role-based workflow is what is being
   examined, and an examiner should be able to switch roles without being told passwords out of
   band.
10. **`/me` resolves the donor record from the authenticated username, never from a parameter.**
    That removes the obvious attack of changing an id in the URL.
11. **The dashboard's callable figure comes from `MatchingService`.** One code path makes BUG-02
    impossible to reintroduce, at the cost of recomputing the shortlist — a trade accepted
    explicitly in PR-01 comment R6.

## Defects found while verifying this change

| ID | Defect | Cause | Fix |
|---|---|---|---|
| **BUG-03** | The dashboard reported 8 callable donors when only 1 was registered | The total was the **sum of the per-group figures**, and one O− donor is callable for all eight recipient groups, so it was counted eight times | The distinct total is the shortlist for **AB+**, the universal recipient, which by definition accepts every group. Regression test: `callableTotalDoesNotDoubleCount` |
| **BUG-04** | The request table was sometimes empty at startup | Two separate `ApplicationRunner` beans have **no guaranteed order**, so requests were occasionally seeded before any donor existed and the guard skipped them silently | Merged into one ordered runner that seeds donors then requests |
| **BUG-05** | The worklist showed MEDIUM above HIGH above CRITICAL | `urgency` is `@Enumerated(STRING)`, so `order by r.urgency desc` sorted the **stored text** — alphabetically, and exactly backwards for the two that matter | Ordering moved to the service using `Urgency.getWeight()`, with the reason recorded on both the query and the comparator. Regression test: `worklistIsOrderedBySeverity` |

All three were found by exercising the running application over HTTP after the unit tests were
already green — which is the argument for the Selenium quality gate added in Tasks 9 and 10.

## The merge conflict

`develop` moved while this branch was in progress: `fix/navbar-health-indicator` added an
unconditional **Health** link as the last item of the main navigation list, while this branch added
a role-conditional **My profile** link in the same position.

```
$ git merge develop
CONFLICT (content): Merge conflict in src/main/resources/templates/fragments/layout.html
```

Resolved by **keeping both** — they serve different people and neither supersedes the other —
ordering the role-conditional item first and recording the reasoning in a comment in the template.
One judgement Git could not make: whether to restrict the Health link by role. It was left
unconditional, because `/actuator/health` is deliberately unauthenticated for the container probe,
so hiding the link would protect nothing.

The full suite was run on the resolved tree **before** committing. Full evidence, including the
file as Git left it with its markers, is in
[`evidence/task-06-conflict/`](../../evidence/task-06-conflict/README.md).

## How to verify locally

```bash
source scripts/env.sh
mvn clean verify && java -jar target/bloodbank.war

# AC-09.1 / AC-09.3
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' http://localhost:9090/   # 302 -> /login
curl -s http://localhost:9090/actuator/health                                      # {"status":"UP"}

# AC-07.4 - the headline assertion: Staff may not fulfil
curl -s -o /dev/null -w '%{http_code}\n' -X POST -u staff:Staff@123 \
     http://localhost:9090/api/requests/1/fulfil                                   # 403

# FR-18 - Admin fulfilling a NEW request is an illegal transition
curl -s -X POST -u admin:Admin@123 http://localhost:9090/api/requests/2/fulfil \
     | jq '.status, .error'                                                        # 409 IllegalTransition

# BUG-05 - the worklist is ordered by severity
curl -s -u staff:Staff@123 http://localhost:9090/api/requests | jq -r '.[].urgency'
# CRITICAL, HIGH, MEDIUM

# AC-08.3 - make the last callable O- donor unavailable and watch the shortage appear
curl -s -X PATCH -u staff:Staff@123 -H 'Content-Type: application/json' \
     -d '{"available":false}' http://localhost:9090/api/donors/1/availability > /dev/null
curl -s -u admin:Admin@123 http://localhost:9090/api/dashboard | jq '.shortages'    # ["O-"]
```

Every command above was run and produced the stated output before this pull request was merged.

---

## Review

Reviewer: Ayush Mayekar · self-review against `CONTRIBUTING.md` §3.1 · 2 October 2026

### Checklist outcome

| Item | Result |
|---|---|
| Every acceptance criterion met and tested | **Pass** |
| Business rules in the service layer or a domain enum | **Pass** |
| Boundary values tested | **Pass** — every transition and every role combination |
| No hard-coded host, port, path or credential | **Pass** — accounts moved to configuration |
| **Authorisation enforced server-side** | **Pass** — this closes PR-01 comment R4 |
| Error semantics follow `docs/API.md` §5 | **Pass** — 401/403/404/409/422 asserted over HTTP |
| Documentation updated in the same commit | **Pass** |
| `mvn clean verify` green | **Pass** — 178 tests, including on the conflict resolution |
| Commit messages follow §2 | **Pass** |
| Branch up to date with `develop`, conflicts resolved on this branch | **Pass** |

### Review comments

**R1 — Why is CSRF disabled on `/api/**`? That reads like a shortcut to make `curl` work.**
*Response:* It is a shortcut to make `curl` work, and it is also correct. CSRF protection defends
against a third-party page causing a request that rides on the victim's **ambient session**. The API
chain is `STATELESS` and authenticates per request with Basic credentials, so there is no ambient
session to ride on. The browser chain, which does have sessions, keeps CSRF enabled. The reasoning
is in the Javadoc on `SecurityConfig` so nobody later "tidies" it into one chain.

**R2 — `RequestService` checks the role, and `SecurityConfig` also restricts URLs. Is that duplication?**
*Response:* Deliberate layering, not duplication. The URL rules are a coarse filter that keeps an
unauthenticated or wrong-role user off a whole screen cheaply. They cannot express "ADMIN may move
MATCHED to FULFILLED and nothing else", which is a function of the *current state of one record*.
Removing the service-layer check would leave the API able to perform transitions the UI forbids,
which is exactly what NFR-07 prohibits.

**R3 — The dashboard reported 8 callable donors with 1 donor registered.**
*Response:* Correct — defect BUG-03, fixed before merge. The total was summing figures that
deliberately overlap. The distinct count is the AB+ shortlist, because AB+ accepts every group.
A regression test now asserts that the total is *less than* the sum of the per-group figures
whenever more than one group is represented.

**R4 — The worklist order was wrong and the unit tests did not catch it.**
*Response:* Also correct — BUG-05. The tests asserted *which* requests came back, never in what
order, and the ordering was in SQL where a JPA test over H2 happily agreed with the production
behaviour because both were wrong in the same way. `worklistIsOrderedBySeverity` now asserts the
exact sequence. This is the clearest argument in the project for the Task 9 Selenium journeys: the
unit suite was green while the screen was visibly wrong.

**R5 — Three ApplicationRunner-ordering and enum-ordering problems in one task suggests the
verification step was too late.**
*Response:* Accepted. All three were found by exercising the running application by hand *after* the
unit tests passed. That manual step is exactly what Task 9 automates and Task 10 makes a blocking
gate, and this task is the evidence for why that is worth doing rather than a box-ticking exercise.
Noted in the sprint retrospective.

**R6 — The login page shows passwords. That cannot be right.**
*Response:* It is intentional and scoped. These are demonstration credentials for a locally
deployed instance, supplied from configuration and overridable by environment variable; they are
not valid against anything else. The role-based workflow is the behaviour under examination, and an
examiner needs to switch roles quickly. For any real deployment the accounts would come from a
directory and the panel would be removed — recorded as a limitation in `docs/SRS.md` §5 and as
backlog item FE-11.

**R7 — `/me` is reachable by ADMIN as well as DONOR. Why?**
*Response:* So that an administrator can see what a donor sees when supporting them, which is a
routine need. The record shown is still resolved from the authenticated username, so an ADMIN sees
their own linked record, not an arbitrary one. The page now also degrades to an explanatory message
instead of a 500 when the account is not linked to a donor record — found while writing the tests.

### Verdict

**Approved.** Merge `--no-ff` into `develop`, then `develop` into `main` and tag `v1.0.0`: this is
the release-ready source baseline the task asks for.
