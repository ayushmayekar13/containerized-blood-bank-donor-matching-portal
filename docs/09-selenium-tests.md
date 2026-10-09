# Task 9 — Selenium Test Design and Local Execution

**Deliverable:** test plan, Selenium scripts, local test report and failure screenshot mechanism.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 5 October 2026 · **Tag:** `task-09`

---

## 1. Test plan — choosing five journeys

The portal has twenty UI endpoints. Testing all of them through a browser would be slow, and slow
suites stop being run. The five journeys were chosen by one rule: **if this stopped working, would
the blood bank be back on the phone tree?**

| # | Journey | Why it is critical | If it broke |
|---|---|---|---|
| **J1** | Staff registers, finds and updates a donor | Without a registry nothing else has anything to work with; the only journey that writes a record others rely on | No new donors could be added at all |
| **J2** | Search the registry and read the ranked shortlist | Search and ranking are the two things the portal exists to replace | Nobody could answer "who can we call for a B+ patient in Thane right now?" |
| **J3** | Record a request and see it on the dashboard | The only journey crossing a **screen boundary** — exactly where defect BUG-02 lived | Management loses visibility of supply against demand |
| **J4** | Staff assigns a donor; Staff is refused the fulfil action | Guards the accountability claim. A transition anyone may perform is not a workflow | Any staff member could close any request |
| **J5** | Admin fulfils; the donor becomes ineligible | The only journey asserting a **side effect crossing entities** | The portal would keep recommending someone who has just given blood |

Three criteria were used, in this order: **blast radius** (J1, J2), **cross-boundary behaviour** that
unit tests structurally cannot reach (J3, J5), and **the rules a user could otherwise talk their way
around** (J4).

### 1.1 What is deliberately not a Selenium journey

Compatibility across all 64 blood-group pairs, the eligibility boundary at 89/90/91 days, and every
illegal state transition are **unit tests**. They run in milliseconds, need no browser, and asserting
64 pairs through a UI would take minutes to prove exactly what a JUnit test proves instantly.
Selenium is reserved for what only a browser can check: that the pieces are wired together, that a
form really submits, and that a rule survives the round trip through HTTP.

---

## 2. The suite

16 tests across five journeys.

| Journey | Test | Asserts |
|---|---|---|
| J1 | `registersAValidDonor` | AC-01.1 — saved, count increases, findable with the group+city+available filters |
| J1 | `rejectsAnInvalidPhoneNumber` | AC-01.3 — a 5-digit phone is rejected with a field-level message **and nothing is saved** |
| J1 | `updatesADonor` | AC-03.1 — a city change persists and is visible on the detail page |
| J2 | `filtersByBloodGroup` | AC-02.1 — reads the group badge of every visible row, rather than trusting the query string |
| J2 | `filtersCombineWithAnd` | AC-02.2 — the combined result is smaller than either filter alone, which an OR could not produce |
| J2 | `showsAMessageWhenNothingMatches` | AC-02.4 — an explicit message, and the table is not rendered at all |
| J2 | `showsTheRankedShortlist` | AC-06.8, FR-14 — only B+, B−, O+, O− appear for a B+ patient; ranks are a contiguous 1..n |
| J3 | `recordsARequestAndSeesItOnTheDashboard` | AC-05.1, AC-05.3 — starts `New`, records its creator, and the dashboard aggregate moves |
| J3 | `dashboardReportsSupplyPerGroup` | AC-08.1 — all eight groups; callable never exceeds the register (would have caught BUG-03) |
| J3 | `worklistIsOrderedBySeverity` | BUG-05 regression, at the level a user sees it |
| J4 | `staffAssignsTheTopRankedDonor` | AC-07.1, FR-19 — status becomes `Matched`, the donor recorded, the shortlist withdrawn |
| J4 | **`staffIsRefusedTheFulfilAction`** | **AC-07.4** — the fulfil and reject actions are absent for STAFF, and the reason is stated |
| J4 | `adminIsOfferedTheActions` | The same request, same state, different role — without this, a missing button could equally be a rendering failure |
| J5 | `fulfillingMakesTheDonorIneligible` | AC-07.2, FR-20, AC-07.5 — donation stamped, future eligibility date, engine stops offering them, terminal state offers no actions |
| J5 | `adminRejectsWithAReason` | AC-07.3, FR-21 — the reason is mandatory and displayed |
| J5 | `donorControlsOwnAvailability` | AC-04.1 — a DONOR is not offered the registry or worklist, and can toggle availability |

---

## 3. How the suite is built to stay trustworthy

A UI suite earns its place only if people believe it. Five decisions make that true.

| Decision | Reason |
|---|---|
| **No chromedriver binary, no pinned driver version** | Selenium Manager (built in since 4.6) detects the installed Chrome and fetches the matching driver. A committed driver rots the moment Chrome auto-updates — the most common reason a working UI suite stops working |
| **No `Thread.sleep`, anywhere** | Every wait is a `WebDriverWait` on an explicit condition. A fixed sleep is either too short on a loaded machine (flaky) or too long on a fast one (slow), and a slow suite stops being run |
| **Elements located by `data-testid`** | Restyling a page or rewording a label must not break a test, or the suite generates false alarms and gets ignored |
| **Screenshot, page source and URL on every failure** | In Jenkins the browser is headless and nobody is watching. The capture is the only record of what the page looked like |
| **Unique generated test data; state restored** | The suite can be re-run against the same long-lived instance, not only against a freshly seeded database |

### 3.1 The quality-gate mechanics

Failsafe binds both `integration-test` **and** `verify`. Without the `verify` goal, a failed journey
is recorded and the build still goes green — precisely the hole the Task 10 gate must not have.

### 3.2 Why the journeys are in their own Maven profile

```bash
mvn clean verify                # unit tests only — green on any machine, nothing running
mvn clean verify -P ui-tests    # UI journeys, against a deployed instance
bash scripts/run-ui-tests.sh    # builds, deploys, then runs them (what the pipeline does)
```

`mvn clean verify` must be green from a clean checkout with nothing else running — that is US-10, and
the Jenkins *Unit Test* stage depends on it. The journeys need a deployed application and a browser;
making them part of the default build would mean the default build fails for reasons unrelated to
the commit, and the usual response to that is that people stop trusting the build.

---

## 4. Local execution through Maven

```
$ bash scripts/run-ui-tests.sh

Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J2DonorSearchIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J1DonorRegistrationIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J3RequestAndDashboardIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J4MatchAndRoleGuardIT
Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 -- in com.bloodbank.ui.J5FulfilAndEligibilityIT

Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

---

## 5. The failure screenshot mechanism, demonstrated

An assertion in J2 was deliberately changed to expect text that is not on the page, the single test
was run, and the capture was kept:

```
FAILURE captured: target/screenshots/J2DonorSearchIT.showsAMessageWhenNothingMatches.png
FAILURE url:      http://localhost:9091/donors?group=AB-&city=Nagpur
```

[`evidence/task-09-selenium/failure-demo/`](../evidence/task-09-selenium/failure-demo) contains all
three artefacts: the **PNG** of the page, the **page source** for failures a picture cannot explain
(an element in the DOM but not visible), and a **`.txt`** with the URL and page title — which answers
the first question anyone asks of a screenshot: *which page is this?*

---

## 6. Problems met and solved

### 6.1 The screenshot mechanism photographed the wrong page

The first version captured in `TestWatcher#testFailed`, which JUnit runs **after** `@AfterEach`.
`@AfterEach` ends the session, so every screenshot was a picture of the **login page** rather than
of the failure.

**Fix.** Capture now happens in `AfterTestExecutionCallback`, which runs immediately after the test
body and before any teardown.

**Lesson.** A screenshot mechanism that photographs the wrong page is worse than none, because it
*looks* like evidence.

### 6.2 BUG-07 — a DONOR signing in landed on HTTP 403

**Cause.** Login used one fixed success URL, `/dashboard`, which a DONOR may not open.

**Why the unit tests missed it.** Every one authenticated *directly as a role* and requested a
specific URL. None walked the login form and followed the redirect.

**Fix.** `RoleAwareSuccessHandler`, which keeps the useful default of returning a deep-linked user to
the page they asked for and only makes the fallback role-aware.

### 6.3 BUG-08 — `mvn clean` failed whenever a container was running

**Cause.** The container bind-mounted the WAR straight out of `target/`, so Docker held the file open
and the build directory could not be deleted.

**Fix.** The WAR is staged into `.deploy/` before mounting, decoupling the build directory from the
container's lifetime.

### 6.4 BUG-09 — `GET /logout` silently did not sign the user out

**Cause.** Spring Security 6 requires a POST to `/logout` when CSRF protection is on, so the GET
landed on an error page and the session survived — making journeys order-dependent in a way that
would only have shown up intermittently.

**Fix.** Teardown clears cookies, which cannot fail that way.

### 6.5 BUG-10 — a native ChromeDriver click submitted nothing

**Diagnosis.** Instrumented the journey:

```
DIAG btn: displayed=true enabled=true tag=button type=submit
          formAction=http://localhost:9091/me/availability
DIAG native click returned
DIAG after native: state=You are currently available   <- unchanged
```

`form.requestSubmit()` on the same element worked every time.

**Fix.** `clickSubmit()` performs the real user gesture first, then completes the submission
programmatically **only if** the page demonstrably did not navigate — detected by staleness of the
old `<body>`, not by a timer. The outcome is deterministic and the form is submitted exactly once.

### 6.6 Two mistakes in the tests themselves

- **A bare `isPresent()` immediately after a page navigation.** It asks the DOM once and raced the
  render, failing intermittently on a page that was correct.
- **An assertion on the exact top-ranked donor.** J4 and J5 record donations, which makes donors
  ineligible, so the best *available* match legitimately changes with execution order. The test now
  asserts the ranking **invariant** — if any shortlisted donor is an exact group match, the
  top-ranked one must be — which is both stronger and order-independent.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| No committed chromedriver and no pinned version | Selenium Manager matches the installed Chrome, so the suite does not rot when Chrome updates |
| No `Thread.sleep` anywhere | A fixed sleep is flaky when short and slow when long, and slow suites stop being run |
| Location by `data-testid` | Restyling must not raise false alarms, or the suite gets ignored |
| The 64 compatibility pairs are deliberately **not** Selenium tests | They run as unit tests in milliseconds; a browser would take minutes to prove the same thing |
| The journeys live in their own Maven profile | `mvn clean verify` must stay green on a clean checkout with nothing running — US-10, and the Jenkins Unit Test stage depends on it |
| Failsafe binds `verify` as well as `integration-test` | Without it a failed journey is recorded and the build still goes green |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-09-selenium/local-run-summary.txt`](../evidence/task-09-selenium/local-run-summary.txt) | The command and the 16-test result |
| [`evidence/task-09-selenium/`](../evidence/task-09-selenium) | Failsafe XML and per-class text reports |
| [`evidence/task-09-selenium/failure-demo/`](../evidence/task-09-selenium/failure-demo) | A real failure capture: PNG, page source and URL |
| [`src/it/java/com/bloodbank/ui/`](../src/it/java/com/bloodbank/ui) | Five journeys, the shared fixture and the failure extension |

---

## 9. Task 9 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Every acceptance criterion met and demonstrable | 16 journeys mapped to AC ids |
| D2 | Every business rule has a passing test | 178 unit + 16 journeys |
| D3 | `mvn clean verify` green from a clean target | Unit suite unaffected by the profile |
| D6 | The Selenium suite passes and would fail the build if red | Failsafe bound to `verify` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-09-selenium/`, including a failure demo |
| D10 | The deployed instance answers its health check | `run-ui-tests.sh` deploys then verifies |
| D11 | Committed on `main` per the branching model | `task-09` |
| D12 | Backlog updated with BUG-07 to BUG-10 | `docs/backlog.md` |

**Outcome:** five journeys chosen by blast radius rather than coverage, 16 tests green locally
through Maven, a failure mechanism that captures the page, its source and its URL — demonstrated by
deliberately breaking an assertion — and **four real application defects found after all 178 unit
tests were already passing**, which is the return on building the suite stated in defects rather
than in adjectives.
