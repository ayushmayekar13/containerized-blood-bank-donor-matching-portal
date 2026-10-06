# Task 10 — Continuous Testing in Jenkins

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 6 October 2026

| Required deliverable | Delivered as |
|---|---|
| Jenkins test report | JUnit publisher on `target/failsafe-reports/*.xml` in an `always` block; per-build trend at `http://localhost:8090/job/bloodbank-pipeline/` |
| Failed pipeline evidence | [`build-09-console-ui-gate-red.log`](../evidence/task-10-quality-gate/build-09-console-ui-gate-red.log) and [`stage-view-09-ui-gate-red.txt`](../evidence/task-10-quality-gate/stage-view-09-ui-gate-red.txt) — **UI Test FAILED, Promote never entered** |
| Defect correction commit | `b518aad fix: restore the ADMIN-only guard on the fulfil action (BUG-06)` |
| Successful rerun | [`stage-view-11-green-after-fix.txt`](../evidence/task-10-quality-gate/stage-view-11-green-after-fix.txt) — all stages green, Promote reached |
| Failure screenshots | [`gate-failure-screenshots/`](../evidence/task-10-quality-gate/gate-failure-screenshots) — archived by Jenkins from the red build |

---

## 1. The gate is the stage *order*, not a check inside a stage

```
Checkout → Build → Unit Test → Package → Deploy → Health Check → ┃ UI Test ┃ → Promote
                                                                 ┗━ GATE ━┛
```

Every stage that **promotes** a build — publishing the image to the registry, replacing the running
container (added in Task 12) — is placed *after* the UI Test stage. If a journey fails, that stage
fails, the pipeline stops, and the stages below are never entered. Nothing has to inspect a flag,
because a failing regression cannot physically reach them.

Three things are deliberately **not** in this stage:

| Not used | Why |
|---|---|
| `catchError` / `\|\| true` | Records the failure and lets the build continue. That is a gate that *reports*, not a gate that *stops* |
| `unstable` | A yellow build still promotes. "Mostly working" is not a deployment criterion for a portal that decides who gives blood |
| `retry` | A journey that passes on the second attempt is telling you the suite is flaky. Hiding that is how a suite stops being believed |

Reports and screenshots are published in an `always` block, because a red gate is exactly when they
are needed — and the next build overwrites the workspace.

## 2. The deliberate defect — and why the first attempt was the wrong defect

**First attempt (build #8).** The ADMIN-only guard was weakened in `RequestService` itself. The
build went red — at the **Unit Test** stage:

```
Unit Test                    FAILED       24.3s
Package                      FAILED        0.1s
...
UI Test (quality gate)       FAILED        0.1s
Promote                      FAILED        0.1s
```

That is correct behaviour and good defence in depth: three unit tests caught it, and nothing was
deployed. But it proved the *unit suite* works, not the *Selenium gate* — the build never reached
deployment, so the gate was never exercised. Kept as evidence in
[`stage-view-08-unit-test-red.txt`](../evidence/task-10-quality-gate/stage-view-08-unit-test-red.txt),
because it is a genuine result worth being able to explain.

**Second attempt (build #9).** The defect was moved to where only a browser can see it: the
`sec:authorize="hasRole('ADMIN')"` attribute was removed from the fulfil form on the request detail
page. The **service layer was untouched** and still refuses a STAFF caller with 403, so no data was
ever at risk — but the screen now advertises an action the user cannot take.

This is the defect shape the gate exists for, and it is a realistic mistake: a template tidy-up that
removes an attribute someone did not realise was load-bearing.

```
$ mvn test
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0      <- all green WITH the defect in place
```

## 3. Build #9 — the gate stopping a deployment

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
health check *all passed*. The regression was invisible to every one of them, and the browser
caught it.

The archived screenshot in `gate-failure-screenshots/` shows the request detail page as a STAFF
user, with the "Confirm donation (fulfil)" button that should not be there.

## 4. The correction, and a second defect the gate found on the way

| Build | Result | What happened |
|---|---|---|
| #7 | SUCCESS | Baseline: gate added, everything green |
| #8 | FAILURE | Service-layer defect → caught at Unit Test |
| **#9** | **FAILURE** | **UI-only defect → caught at the gate; Promote skipped** |
| #10 | FAILURE | Defect fixed, but journey J2 flaked — see below |
| **#11** | **SUCCESS** | All green, Promote reached |

Build #9 failed **two** journeys. J4 was the intended defect. J2 threw
`StaleElementReference` — a genuine flake in a journey whose subject is not timing at all. Build #10
then failed J2 again, differently: the blood-group badges read back empty even though the archived
page source showed six correctly labelled rows.

| Cause | Fix |
|---|---|
| Reading elements immediately after clicking *Search*, which is a full navigation, so the read sometimes landed while the old DOM was being replaced | Wait for the filtered table before reading from it |
| `getText()` returns only **rendered** text, so in headless Chrome on a loaded CI machine an element can be present and located while its text is still empty | Wait for **visibility**, not presence, and read `textContent`, which comes from the DOM regardless of paint state |

Both were fixed in commits of their own, separate from the defect fix. That separation is the
point: **a quality gate is only worth having if a red build means the application is broken.** Every
flake trains people to re-run the build instead of reading it, and a gate nobody believes is not a
gate. Two flakes in one journey is also a pattern worth naming — every assertion following a
navigation needs both a wait for the right condition and a read that does not depend on rendering.

## 5. Build #11 — green after the fix

```
build #11  status=SUCCESS  total=211.9s
  Checkout                     SUCCESS       2.5s
  Build                        SUCCESS      11.2s
  Unit Test                    SUCCESS      47.7s      178 tests
  Package                      SUCCESS      13.4s
  Deploy to Tomcat             SUCCESS      29.7s
  Health Check                 SUCCESS       4.9s
  UI Test (quality gate)       SUCCESS      98.1s      16 journeys
  Promote                      SUCCESS       0.1s      <- reached

Quality gate passed - build #11 is promotable.
SUCCESS - deployed and healthy at http://localhost:9090
```

## 6. Test reporting in Jenkins

- **Unit results** published from `target/surefire-reports/*.xml` after the Unit Test stage.
- **Journey results** published from `target/failsafe-reports/*.xml` after the gate.
- Both use `allowEmptyResults: false`, so a build whose tests silently did not run **cannot be
  green** — a subtle but important failure mode, because "0 tests, 0 failures" otherwise looks
  identical to success.
- Screenshots are archived with `onlyIfSuccessful: false`, since they only exist when the build
  failed.
- Jenkins renders the pass/fail trend across builds at the job page, so the red stretch (#8–#10) and
  the recovery at #11 are visible without opening a single log.

## 7. Verification

```bash
# the gate passing
bash scripts/jenkins-job.sh build bloodbank-pipeline --wait

# reproduce the gate stopping a deployment: remove sec:authorize="hasRole('ADMIN')"
# from the fulfil form in src/main/resources/templates/requests/detail.html, commit, then
bash scripts/jenkins-job.sh build bloodbank-pipeline --wait      # UI Test red, Promote skipped
mvn test                                                          # still 178 green - the point

open http://localhost:8090/job/bloodbank-pipeline/                # trend graph and history
open http://localhost:8090/job/bloodbank-pipeline/9/testReport/   # the failed journey
```
