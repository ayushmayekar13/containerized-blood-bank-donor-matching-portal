## What this pull request does
<!-- One paragraph. The reviewer should understand the change without reading the diff. -->

## Story / issue
- Story: US-nn
- Task: N
- Closes #

## Acceptance criteria and the tests that prove them

| Acceptance criterion | Proved by |
|---|---|
| AC-nn.1 — … | `SomeTest#method` |
| AC-nn.2 — … | Selenium journey J<n> |

## Approach and any design decision taken
<!-- Why this way and not another; anything a reviewer would otherwise question. -->

## How to verify locally

```bash
source scripts/env.sh
mvn clean verify
```

<!-- Add the specific manual check, e.g. "sign in as staff, open request 1, click Find matches". -->

## Review checklist

- [ ] Every acceptance criterion is met and has an automated test
- [ ] Business rules live in the service layer or a domain enum, not in a controller or template
- [ ] Boundary values tested (e.g. eligibility at 89 / 90 / 91 days)
- [ ] No hard-coded host, port, path or credential
- [ ] Authorisation enforced server-side, not by hiding a UI control
- [ ] Error semantics follow `docs/API.md` §5 (400 / 403 / 404 / 409 / 422)
- [ ] Documentation updated in the same commit as the behaviour
- [ ] `mvn clean verify` green locally and in Jenkins
- [ ] Commit messages follow `CONTRIBUTING.md` §2
- [ ] Branch is up to date with `develop`; conflicts resolved on this branch

## Evidence
<!-- Jenkins build number, test report summary, screenshot paths. -->

## Merge instructions
Merge with `--no-ff` so the feature boundary is preserved in history:

```bash
git checkout develop
git merge --no-ff <branch> -m "Merge PR-nn: <title>"
```
