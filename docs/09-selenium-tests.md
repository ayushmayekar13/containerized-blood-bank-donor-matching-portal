# Task 9 — Selenium Test Design and Local Execution

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 5 October 2026

| Required deliverable | Delivered as |
|---|---|
| Test plan | §1–§3 of this document — five journeys, why each was chosen, and what each asserts |
| Selenium scripts | [`src/it/java/com/bloodbank/ui/`](../src/it/java/com/bloodbank/ui) — `J1`…`J5`, plus `BaseJourney` and `ScreenshotOnFailure` |
| Local test report | [`evidence/task-09-selenium/`](../evidence/task-09-selenium) — **16 tests, 0 failures**, Failsafe XML and summary |
| Failure screenshot mechanism | [`evidence/task-09-selenium/failure-demo/`](../evidence/task-09-selenium/failure-demo) — a real capture from a deliberately broken assertion: PNG, page source, and the URL at the moment of failure |

---

## 1. Test plan — choosing five journeys out of everything that could be tested

The portal has twenty UI endpoints. Testing all of them through a browser would be slow, and slow
suites stop being run. The five journeys were chosen by one rule: **if this stopped working, would
the blood bank be back on the phone tree?**

| # | Journey | Why it is critical | If it broke |
|---|---|---|---|
| **J1** | Staff registers, finds and updates a donor | Without a donor registry nothing else has anything to work with, and this is the only journey that writes a record others can rely on existing | No new donors can be added at all |
| **J2** | Search the registry and read the ranked shortlist | Search and ranking are the two things the portal exists to replace | Nobody could answer "who can we call for a B+ patient in Thane right now?" — the exact failure of the manual process |
| **J3** | Record a request and see it on the dashboard | The only journey that crosses a **screen boundary**: a record created on one page must appear in an aggregate on another — exactly where defect BUG-02 lived | Management loses visibility of supply against demand |
| **J4** | Staff assigns a donor; Staff is refused the fulfil action | Guards the accountability claim. A transition anyone may perform is not a workflow | Any staff member could close any request — and this is the defect deliberately re-introduced in Task 10 |
| **J5** | Admin fulfils; the donor becomes ineligible | The only journey asserting a **side effect crossing entities**. Fulfilling must make the matched donor ineligible for 90 days | The portal would keep recommending someone who has just given blood — the single worst thing it could do |

Three criteria were used, in this order: **blast radius** (J1, J2), **cross-boundary behaviour**
that unit tests structurally cannot reach (J3, J5), and **the rules a user could otherwise talk
their way around** (J4).

### What is deliberately *not* a Selenium journey

Compatibility across all 64 blood-group pairs, the eligibility boundary at 89/90/91 days, and every
illegal state transition are **unit tests**. They run in milliseconds, they do not need a browser,
and asserting 64 pairs through a UI would take minutes to prove exactly what a JUnit test proves
instantly. Selenium is reserved for what only a browser can check: that the pieces are wired
together, that a form really submits, and that a rule survives the round trip through HTTP.

## 2. The five journeys and their assertions

| Journey | Test | Asserts |
|---|---|---|
| J1 | `registersAValidDonor` | AC-01.1 — the donor is saved, the registry count increases, and the donor is findable with the group+city+available filters a technician would actually use |
| J1 | `rejectsAnInvalidPhoneNumber` | AC-01.3 — a 5-digit phone is rejected with a field-level message **and nothing is saved** |
| J1 | `updatesADonor` | AC-03.1 — a city change persists and is visible on the detail page |
| J2 | `filtersByBloodGroup` | AC-02.1 — reads the group badge of every visible row, rather than trusting the query string |
| J2 | `filtersCombineWithAnd` | AC-02.2 — the combined result is smaller than either filter alone, which an OR could not produce |
| J2 | `showsAMessageWhenNothingMatches` | AC-02.4 — an explicit message, and the table is not rendered at all |
| J2 | `showsTheRankedShortlist` | AC-06.8, FR-14 — only B+, B−, O+, O− appear for a B+ patient; the ranking invariant holds; ranks are a contiguous 1..n |
| J3 | `recordsARequestAndSeesItOnTheDashboard` | AC-05.1, AC-05.3 — starts in `New`, records who created it, and the dashboard aggregate moves |
| J3 | `dashboardReportsSupplyPerGroup` | AC-08.1 — all eight groups reported; callable never exceeds the register (the check that would have caught BUG-03) |
| J3 | `worklistIsOrderedBySeverity` | BUG-05 regression, at the level a user sees it |
| J4 | `staffAssignsTheTopRankedDonor` | AC-07.1, FR-19 — status becomes `Matched`, the donor is recorded, the shortlist is withdrawn |
| J4 | `staffIsRefusedTheFulfilAction` | **AC-07.4** — the fulfil and reject actions are absent for STAFF, and the reason is stated |
| J4 | `adminIsOfferedTheActions` | The same request, same state, different role — without this, a missing button could equally be a rendering failure |
| J5 | `fulfillingMakesTheDonorIneligible` | AC-07.2, FR-20, AC-07.5 — the donation is stamped, the donor shows a future eligibility date, the engine stops offering them, and the terminal state offers no actions |
| J5 | `adminRejectsWithAReason` | AC-07.3, FR-21 — the reason is mandatory and is displayed |
| J5 | `donorControlsOwnAvailability` | AC-04.1 — a DONOR is not offered the registry or worklist, sees their own record, and can toggle their availability |

**16 tests across 5 journeys. All green.**

## 3. How the suite is built to stay trustworthy

A UI suite earns its place only if people believe it. Five decisions are what make that true here:

| Decision | Reason |
|---|---|
| **No chromedriver binary, no pinned driver version** | Selenium Manager (built in since 4.6) detects the installed Chrome and fetches the matching driver. A committed driver rots the moment Chrome auto-updates — the most common reason a working UI suite stops working |
| **No `Thread.sleep`, anywhere** | Every wait is a `WebDriverWait` on an explicit condition. A fixed sleep is either too short on a loaded machine (flaky) or too long on a fast one (slow), and a slow suite stops being run on every commit |
| **Elements located by `data-testid`** | Restyling a page or rewording a label must not break a test, or the suite generates false alarms and gets ignored |
| **Screenshot + page source + URL on every failure** | In Jenkins the browser is headless and nobody is watching. The capture is the only record of what the page looked like |
| **Unique generated test data; state restored** | The suite can be re-run against the same long-lived instance, not only against a freshly seeded database |

### The quality gate mechanics

Failsafe binds both `integration-test` **and** `verify`. Without the `verify` goal, a failed journey
is recorded and the build still goes green — precisely the hole the Task 10 gate must not have.

### Why the journeys are in their own Maven profile

```bash
mvn clean verify                # unit tests only — green on any machine, nothing running
mvn clean verify -P ui-tests    # UI journeys, against a deployed instance
bash scripts/run-ui-tests.sh    # builds, deploys, then runs them (what the pipeline does)
```

`mvn clean verify` must be green from a clean checkout with nothing else running — that is US-10,
and the Jenkins *Unit Test* stage depends on it. The journeys cannot satisfy that: they need a
deployed application and a browser. Making them part of the default build would mean the default
build fails for reasons unrelated to the commit, and the usual response to that is that people stop
trusting the build.

## 4. Verified local run

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

## 5. The failure-screenshot mechanism, demonstrated

An assertion in `J2` was deliberately changed to expect text that is not on the page, the single
test was run, and the capture was kept:

```
FAILURE captured: target/screenshots/J2DonorSearchIT.showsAMessageWhenNothingMatches.png
FAILURE url:      http://localhost:9091/donors?group=AB-&city=Nagpur
```

`evidence/task-09-selenium/failure-demo/` contains all three artefacts: the **PNG** of the page, the
**page source** for failures a picture cannot explain (an element in the DOM but not visible), and a
**`.txt`** with the URL and page title — which answers the first question anyone asks of a
screenshot: *which page is this?*

### One thing this got wrong first, and why it matters

The first version captured in `TestWatcher#testFailed`, which JUnit runs **after** `@AfterEach`.
`@AfterEach` ends the session, so every screenshot was a picture of the **login page** rather than of
the failure. Capture now happens in `AfterTestExecutionCallback`, which runs immediately after the
test body and before any teardown. A screenshot mechanism that photographs the wrong page is worse
than none, because it looks like evidence.

## 6. Four real defects this suite found

Every one was found by a browser after the 178 unit tests were already green.

| ID | Defect | Why the unit tests missed it |
|---|---|---|
| **BUG-07** | A **DONOR signing in landed on an HTTP 403.** Login used one fixed success URL, `/dashboard`, which a DONOR may not open | Every unit test authenticated *directly as a role* and requested a specific URL. None walked the login form and followed the redirect. Fixed with `RoleAwareSuccessHandler`, which keeps the useful default of returning a deep-linked user to the page they asked for |
| **BUG-08** | `mvn clean` failed with *"Could not copy webapp classes"* whenever a container was running | The container bind-mounted the WAR straight out of `target/`, so Docker held the file open and the build directory could not be deleted. The WAR is now staged into `.deploy/` before mounting, which decouples the build directory from the container's lifetime |
| **BUG-09** | `GET /logout` in the test teardown **silently did not sign the user out** | Spring Security 6 requires a POST to `/logout` when CSRF protection is on, so the GET landed on an error page and the session survived — making journeys order-dependent in a way that would only have shown up intermittently. Teardown now clears cookies, which cannot fail that way |
| **BUG-10** | A native ChromeDriver click on the availability button **returned successfully without submitting the form** | Reproduced deterministically: `displayed=true enabled=true type=submit`, correct `form.action`, click returns, page never navigates — while `form.requestSubmit()` works every time. `clickSubmit()` performs the real user gesture first and completes the submission only if the page demonstrably did not navigate, detected by staleness of the old `<body>` rather than by a timer |

Two further defects in the tests themselves are worth recording, because both are the classic
mistakes:

- **A bare `isPresent()` immediately after a page navigation.** It asks the DOM once and raced the
  render, failing intermittently on a page that was correct. Every assertion that follows a
  navigation must wait for the thing it is about to assert on.
- **An assertion on the exact top-ranked donor.** Journeys J4 and J5 record donations, which makes
  donors ineligible, so the best *available* match legitimately changes with execution order. The
  test now asserts the ranking **invariant** — if any shortlisted donor is an exact group match, the
  top-ranked one must be — which is both stronger and order-independent.

## 7. Verification

```bash
# everything: build, deploy, run the journeys
bash scripts/run-ui-tests.sh

# against an instance that is already running
bash scripts/run-ui-tests.sh --no-deploy

# watch the browser do it (useful in the viva)
bash scripts/run-ui-tests.sh --headed

# a single journey
mvn -P ui-tests verify -Dit.test='J4MatchAndRoleGuardIT'

# see the failure mechanism for yourself: break an assertion, then
ls target/screenshots/
```
