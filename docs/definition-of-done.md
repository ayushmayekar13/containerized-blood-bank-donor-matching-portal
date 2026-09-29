# Definition of Done

Project: Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
Agreed: 29 September 2026 · Applies to every one of the 15 tasks

A task or user story is **Done** only when *all* applicable checks below pass. "Applicable" is
judged by the column headings: a documentation-only task is not expected to move the pipeline, but
every task that touches code is expected to satisfy the whole list.

## 1. Functional

- [ ] Every acceptance criterion of the story is demonstrably satisfied, by a test or a live demo.
- [ ] The happy path and at least one failure/validation path are both exercised.
- [ ] Role-based rules are honoured: an action forbidden to a role is rejected server-side, not
      merely hidden in the user interface.

## 2. Code quality

- [ ] Business rules live in the service layer, not in controllers or templates, so that they are
      unit-testable without a browser.
- [ ] No commented-out code, no debug printing, no hard-coded host names, ports or paths outside
      configuration.
- [ ] Configuration comes from `application.yml` overridable by environment variables, so the same
      artefact runs locally, in a container and in CI.
- [ ] No new compiler warnings and no new dependency-resolution warnings.

## 3. Testing

- [ ] Unit tests exist for every new business rule, including boundary values
      (e.g. eligibility at 89 / 90 / 91 days).
- [ ] A Selenium journey is added or updated whenever a user-visible flow changes.
- [ ] `mvn clean verify` is green from a clean `target/` directory.
- [ ] A test that would have caught the defect is written **before** fixing any reported defect.
- [ ] Tests are independent and repeatable: no reliance on execution order or on data left behind
      by a previous test.

## 4. Build and pipeline

- [ ] The build is reproducible from a clean checkout with no manual steps beyond `JAVA_HOME`.
- [ ] `mvn clean package` produces the archivable artefact `target/bloodbank.war`.
- [ ] The Jenkins pipeline is green end to end for the commit being called done.
- [ ] The Selenium quality gate is active: red UI tests stop the deployment stage.
- [ ] The build artefact is archived and the test reports are published in Jenkins.

## 5. Deployment and operations

- [ ] A Docker image is built and tagged with the build number (not only `latest`).
- [ ] A fresh container starts from that image and the application answers on its mapped port.
- [ ] `/actuator/health` returns `UP`.
- [ ] Data survives container replacement, because the database lives on a named volume.
- [ ] The Ansible playbook is idempotent: an immediate second run reports `changed=0`.
- [ ] A rollback to the previous image tag has been executed and health-checked at least once for
      any task that changes deployment.

## 6. Documentation and traceability

- [ ] Documentation is updated in the **same commit** as the change it describes.
- [ ] The task's own document (`docs/NN-*.md`) states what was done, how to reproduce it and what
      the expected output is.
- [ ] Evidence (build log, test report, command log or screenshot) is captured under
      `evidence/task-NN-*/`.
- [ ] `docs/TROUBLESHOOTING.md` gains an entry for every non-obvious problem solved.

## 7. Version control

- [ ] Work happened on the correct branch per the branching model in `CONTRIBUTING.md`.
- [ ] Feature work reached `develop` through a reviewed pull request, merged with `--no-ff`.
- [ ] The commit message explains **why**, not only what, and references the story or defect ID.
- [ ] Exactly one commit carries the task's deliverable, and it is tagged `task-NN`.
- [ ] A release-worthy state is additionally tagged `vX.Y.Z`.

## 8. Demonstrability (specific to this subject)

- [ ] The task can be demonstrated live in under three minutes from a cold start.
- [ ] A fallback exists in `evidence/` in case a live step fails during the practical examination.
- [ ] The viva questions for the task are answerable from `docs/VIVA_GUIDE.md`.

---

**Not Done** means: "it works on my machine", a passing test suite with the quality gate disabled,
documentation promised for later, or a deployment that cannot be rolled back.
