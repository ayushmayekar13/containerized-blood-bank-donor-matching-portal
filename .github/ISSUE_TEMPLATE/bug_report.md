---
name: Bug report
about: A defect in the Blood Bank Donor Matching Portal
title: "[BUG-nn] <short description of the wrong behaviour>"
labels: bug
assignees: ayushmayekar
---

## Summary
<!-- One sentence: what is wrong. -->

## Rule that is being violated
<!-- Point at the requirement, e.g. "FR-08: a donor is eligible only when the last donation is at
     least 90 days ago" or "AC-07.4: Staff must not be able to fulfil a NEW request". -->

- Requirement:
- Story / acceptance criterion:

## Steps to reproduce
1.
2.
3.

## Expected behaviour

## Actual behaviour

## Environment
| | |
|---|---|
| Found in | `main` / `develop` / `feature/...` @ commit |
| Where | local `mvn spring-boot:run` / container on :9090 / Jenkins build # |
| Role used | ADMIN / STAFF / DONOR / anonymous |
| Browser | Chrome (headless?) |

## Evidence
<!-- Selenium failure screenshot path, Jenkins build URL, log excerpt, curl output. -->

## Severity
- [ ] Blocker — the MVP cannot be demonstrated
- [ ] Major — a requirement is not met
- [ ] Minor — cosmetic or wording

## Definition of fixed
- [ ] A test that fails because of this defect is written **first**
- [ ] The fix makes that test pass and the whole suite stays green
- [ ] `docs/TROUBLESHOOTING.md` records the cause if it was non-obvious
- [ ] Fix merged through a pull request referencing this issue
