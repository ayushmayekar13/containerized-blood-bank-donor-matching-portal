# Task 10 — Continuous Testing in Jenkins

**Deliverable:** Jenkins test report, failed pipeline evidence, defect correction commit and
successful rerun.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 6 October 2026 · **Tag:** `task-10`

---

## 1. The gate in the pipeline

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI TEST ┃ → Promote
                                                                 ┗━ GATE ━┛
```

Every stage that **promotes** a build — publishing the image to the registry, replacing the running
container (added in Task 12) — is placed *after* the UI Test stage. If a journey fails, that stage
fails, the pipeline stops, and the stages below are never entered. **Nothing has to inspect a flag,
because a failing regression cannot physically reach them.**

### 1.1 What is deliberately not in this stage

| Not used | Why |
|---|---|
| `catchError` / `\|\| true` | Records the failure and lets the build continue. That is a gate that *reports*, not a gate that *stops* |
| `unstable` | A yellow build still promotes. "Mostly working" is not a deployment criterion for a portal that decides who gives blood |
| `retry` | A journey that passes on the second attempt is telling you the suite is flaky. Hiding that is how a suite stops being believed |

Reports and screenshots are published in an `always` block, because a red gate is exactly when they
are needed — and the next build overwrites the workspace.

---

## 2. The deliberate defect

### 2.1 First attempt — the wrong defect (build #8)

The ADMIN-only guard was weakened in `RequestService` itself. The build went red — at the **Unit
Test** stage:

```
Unit Test                    FAILED       24.3s
Package                      FAILED        0.1s
...
UI Test (quality gate)       FAILED        0.1s
Promote                      FAILED        0.1s
```

That is correct behaviour and good defence in depth: three unit tests caught it, and nothing was
deployed. But it proved the **unit suite** works, not the **Selenium gate** — the build never
reached deployment, so the gate was never exercised.

Build #8 is kept as evidence, because it is a genuine result worth being able to explain.

### 2.2 Second attempt — the right defect (build #9)

The defect was moved to where only a browser can see it: the `sec:authorize="hasRole('ADMIN')"`
attribute was removed from the fulfil form on the request detail page.

The **service layer was untouched** and still refuses a STAFF caller with 403, so no data was ever at
risk — but the screen now advertises an action the user cannot take.

```
$ mvn test
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0      <- all green WITH the defect in place
```

This is the defect shape the gate exists for, and it is a realistic mistake: a template tidy-up that
removes an attribute someone did not realise was load-bearing.

---

## 3. The failed pipeline — build #9

```
build #9  status=FAILED  total=93.9s
  Checkout                     SUCCESS       1.1s
  Build                        SUCCESS       3.0s
  Unit Test                    SUCCESS      12.2s      <- 178 tests, all green
  Package                      SUCCESS       5.3s
  Deploy to Tomcat             SUCCESS      10.8s
  Health Check                 SUCCESS       1.4s      <- behavioural checks passed too
  UI Test (quality gate)       FAILED       58.1s      <- journey J4
  Promote                      FAILED        0.1s      <- never entered
```

From the console log:

```
[ERROR] J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction:84
        [AC-07.4: a STAFF user must not be offered the fulfil action]

QUALITY GATE FAILED - a user journey is broken.
Nothing after this stage runs: no image is published and no container is replaced.
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.png
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.html
  screenshot: J4MatchAndRoleGuardIT.staffIsRefusedTheFulfilAction.txt

Stage "Promote" skipped due to earlier failure(s)
```

**This is the headline result of the task.** Unit tests, packaging, deployment and the behavioural
health check *all passed*. The regression was invisible to every one of them, and the browser caught
it.

The archived screenshot shows the request detail page as a STAFF user, with the "Confirm donation
(fulfil)" button that should not be there.

---

## 4. The correction and the rerun

| Build | Result | What happened |
|---|---|---|
| #7 | SUCCESS | Baseline: gate added, everything green |
| #8 | FAILURE | Service-layer defect → caught at Unit Test |
| **#9** | **FAILURE** | **UI-only defect → caught at the gate; Promote skipped** |
| #10 | FAILURE | Defect fixed, but journey J2 flaked — see §5 |
| **#11** | **SUCCESS** | All green, Promote reached |

### 4.1 Build #11 — green after the fix

```
build #11  status=SUCCESS  total=211.9s
  Unit Test                    SUCCESS      47.7s      178 tests
  Health Check                 SUCCESS       4.9s
  UI Test (quality gate)       SUCCESS      98.1s      16 journeys
  Promote                      SUCCESS       0.1s      <- reached

Quality gate passed - build #11 is promotable.
SUCCESS - deployed and healthy at http://localhost:9090
```

The correction commit is `b518aad fix: restore the ADMIN-only guard on the fulfil action (BUG-06)`.

---

## 5. Problems met and solved

Build #9 failed **two** journeys. J4 was the intended defect. J2 was a genuine flake in a journey
whose subject is not timing at all, and fixing it took two attempts — which makes it the most
instructive failure in the project.

| Attempt | Symptom | Cause | Fix |
|---|---|---|---|
| 1 | `StaleElementReference` | Read elements immediately after clicking *Search*, which is a full navigation | Wait for the results table before reading |
| 2 | Badges came back **empty**, though the archived page source showed ten correctly labelled rows | `getText()` returns only **rendered** text, so it depends on paint timing; in headless Chrome an element can be present and located while its text is still empty | Wait for **visibility** rather than presence, and read `textContent` |
| 3 | The badge list came back **empty entirely** | **The wait was already satisfied before the click.** The unfiltered list shown before clicking *Search* has the same results table, so `visibilityOfElementLocated` returned instantly against the **old** page | `clickSubmit()`, which waits for the old `<body>` to go **stale** — the only reliable signal that the navigation actually happened |

> **Waiting for a condition that is already true is not waiting at all.** After an action that
> replaces the page, wait for something about the **old** page to become false — staleness — before
> waiting for anything about the new one.

Both flake fixes were committed **separately** from the defect fix. That separation is the point: a
quality gate is only worth having if a red build means the application is broken. Every flake trains
people to re-run the build instead of reading it, and a gate nobody believes is not a gate.

---

## 6. Test reporting in Jenkins

- **Unit results** published from `target/surefire-reports/*.xml` after the Unit Test stage.
- **Journey results** published from `target/failsafe-reports/*.xml` after the gate.
- Both use `allowEmptyResults: false`, so a build whose tests silently did not run **cannot be
  green** — "0 tests, 0 failures" otherwise looks identical to success.
- Screenshots are archived with `onlyIfSuccessful: false`, since they only exist when the build
  failed.
- Jenkins renders the pass/fail trend across builds, so the red stretch (#8–#10) and the recovery at
  #11 are visible without opening a single log.

---

## 7. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| The gate is the stage **order**, not a check inside a stage | Nothing has to inspect a flag, because a failing regression cannot physically reach the promotion stages |
| No `catchError`, no `\|\| true`, no `unstable`, no `retry` | The first three turn a gate that stops into a gate that reports. The last hides flakiness, which is how a suite stops being believed |
| The first deliberate defect was replaced | Weakening the service-layer guard turned the build red at Unit Test — correct, but it proved the unit suite works, not that the gate does, because the build never reached deployment |
| Flake fixes committed separately from the defect fix | A gate is only worth having if a red build means the application is broken |
| Reports and screenshots published in an `always` block | A red gate is exactly when they are needed, and the next build overwrites the workspace |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt`](../evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt) | **The red gate with Promote never entered** |
| [`evidence/task-10-quality-gate/build-09-console-ui-gate-red.log`](../evidence/task-10-quality-gate/build-09-console-ui-gate-red.log) | Full console of the failed pipeline |
| [`evidence/task-10-quality-gate/gate-failure-screenshots/`](../evidence/task-10-quality-gate/gate-failure-screenshots) | The archived screenshot, page source and URL from the red build |
| [`evidence/task-10-quality-gate/stage-view-08-unit-test-red.txt`](../evidence/task-10-quality-gate/stage-view-08-unit-test-red.txt) | The first attempt, caught at Unit Test |
| [`evidence/task-10-quality-gate/stage-view-11-green-after-fix.txt`](../evidence/task-10-quality-gate/stage-view-11-green-after-fix.txt) | All stages green, Promote reached |
| [`evidence/task-10-quality-gate/build-history.txt`](../evidence/task-10-quality-gate/build-history.txt) | The red stretch #8–#10 and the recovery at #11 |

---

## 9. Task 10 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | AC-07.4 demonstrably enforced | J4 red with the defect, green after the fix |
| D2 | A test that would catch the defect exists | `staffIsRefusedTheFulfilAction` |
| D5 | The Jenkins pipeline is green for the corrected commit | Build #11 SUCCESS |
| D6 | **A red gate stopped the deployment** | Build #9: Promote never entered |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-10-quality-gate/` |
| D10 | The deployed container answers its health check | Post-gate health check |
| D11 | Defect and correction committed on `main` | `b518aad` |
| D12 | Backlog updated with BUG-06 | `docs/backlog.md` |

**Outcome:** the Selenium suite is a blocking gate rather than a report. Build #9 passed 178 unit
tests, packaged, deployed and passed a behavioural health check, and still went red because a
browser found an action offered to a user who was not permitted to take it — with no image published
and no container replaced. Two flakes exposed by the exercise were fixed in commits of their own,
because a gate is only worth having if a red build means the application is broken.
