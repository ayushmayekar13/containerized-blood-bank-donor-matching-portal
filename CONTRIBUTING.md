# Contributing Guide — Branch, Commit and Review Policy

Project: Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018

This file is the repository's working agreement. It is deliberately strict for a single-developer
project, because the branching, review and tagging discipline is itself part of what the subject
assesses (Tasks 4, 5 and 6).

---

## 1. Branch model

```
feature/<slug>  ──commits──►  pull request + review  ──merge --no-ff──►  develop  ──merge──►  main ──tag──► vX.Y.Z
```

| Branch | Purpose | Rules |
|---|---|---|
| `main` | Always releasable. Every commit on it is a state that builds, passes the pipeline and can be deployed. | No direct feature work. Receives merges from `develop` only. Tagged `vX.Y.Z` at each release. |
| `develop` | Integration branch where features are combined into the MVP. | Receives `feature/*` through pull requests, merged with `--no-ff`. |
| `feature/<slug>` | One branch per user story or defect. Short-lived (hours to a day). | Branched from `develop`. Deleted after merge, except `feature/export-report`, kept open on purpose so an unmerged branch and a live pull request can be demonstrated. |
| `hotfix/<slug>` | Urgent fix against a released state. | Branched from `main`, merged to both `main` and `develop`. Not used in this release. |

### 1.1 Branch naming rules

```
<type>/<short-kebab-case-description>
```

| `<type>` | Use for | Example |
|---|---|---|
| `feature` | A user story from the backlog | `feature/donor-registry` |
| `fix` | A defect with a `BUG-nn` id | `fix/bug-01-eligibility-boundary` |
| `chore` | Build, tooling, scripts, dependencies | `chore/jenkins-pipeline` |
| `docs` | Documentation only | `docs/architecture-diagrams` |
| `hotfix` | Production-urgent fix off `main` | `hotfix/health-endpoint-401` |

Rules: lowercase only · hyphens, never spaces or underscores · no personal names · no issue
numbers alone (`feature/12` says nothing) · maximum 40 characters after the type.

**Rejected examples:** `Ayush-work`, `feature/Donor_Registry`, `new-stuff`, `temp`, `test2`.

## 2. Commit message convention

```
<type>: <imperative summary, max 72 chars, no trailing full stop>

<body: why the change is needed and what approach was taken, wrapped at 72>

<footer: Story: US-nn / Refs: BUG-nn / Task: n>
```

`<type>` is one of `feat`, `fix`, `docs`, `test`, `chore`, `refactor`, `perf`, `ci`.
Task deliverable commits additionally begin with `Task N:` so that the examiner can read the
project plan straight out of `git log --oneline`.

**Rules**

1. Imperative mood: "add donor search", not "added" or "adds".
2. The body explains **why**, because the diff already shows what.
3. One logical change per commit. A commit that touches the matching engine and renames a template
   is two commits.
4. Never commit a failing build to `develop` or `main`.
5. Never commit secrets, `target/`, database files or IDE settings — `.gitignore` enforces this.
6. Reference the story (`US-nn`), the defect (`BUG-nn`) or the task number in the footer.

**Good**

```
feat: rank exact blood-group matches above compatible ones

A universal O- donor should not be spent on an O+ patient when an O+
donor is available, so ranking now prefers an exact group match before
same-city proximity. Preserves rare-group stock without reducing the
size of the shortlist.

Story: US-06
```

**Bad:** `update`, `fix stuff`, `wip`, `final version`, `asdf`, `minor changes`.

## 3. Pull request rules

Every feature reaches `develop` through a pull request, even in a single-developer project,
because the review record is a deliverable (Task 5).

1. The PR description uses `.github/PULL_REQUEST_TEMPLATE.md` in full.
2. The PR states the story it implements and lists each acceptance criterion with the test that
   proves it.
3. The branch must build and pass `mvn clean verify` before review.
4. Review comments are recorded, answered and resolved; the record is kept under `docs/pr/` so it
   survives independently of any hosting platform.
5. Merge with **`--no-ff`** so that the merge commit permanently records the feature boundary:
   ```bash
   git checkout develop
   git merge --no-ff feature/donor-registry -m "Merge PR-01: donor registry and matching engine"
   ```
6. Delete the feature branch after merge (except the intentionally open one).
7. A merge conflict is resolved **on the feature branch** by merging `develop` into it first, never
   by force-pushing over someone else's work.

### 3.1 Review checklist (applied to every PR)

- [ ] Every acceptance criterion of the story is met and has a test.
- [ ] Business rules are in the service layer or a domain enum, not in a controller or template.
- [ ] Boundary values are tested (for example eligibility at 89, 90 and 91 days).
- [ ] No hard-coded host, port, path or credential.
- [ ] Authorisation is enforced server-side, not by hiding a button.
- [ ] Error semantics follow `docs/API.md` §5 (400 / 403 / 404 / 409 / 422).
- [ ] Documentation updated in the same commit as the behaviour it describes.
- [ ] `mvn clean verify` green; Selenium journey added or updated if a user flow changed.
- [ ] Commit messages follow §2 and the history is readable.

## 4. Versioning

[Semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`.

| Tag | Meaning in this project | Created at |
|---|---|---|
| `task-01` … `task-15` | The exact state at which each subject task was completed | Every task |
| `v1.0.0` | Functional MVP: all user stories complete, release-ready source baseline | Task 6 |
| `v1.1.0` | Containerized continuous deployment to the registry | Task 12 |
| `v1.2.0` | Automated provisioning with idempotency and rollback | Task 14 |
| `v1.3.0` | Final documented release | Task 15 |

Tags are annotated, never lightweight:
```bash
git tag -a v1.0.0 -m "Functional MVP: donor registry, matching engine, role-based workflow, dashboard"
```

## 5. Issue policy

Issues are raised from the templates in `.github/ISSUE_TEMPLATE/`:

| Template | Use for | Labels |
|---|---|---|
| `task.md` | One of the 15 subject tasks | `task`, `taskN` |
| `feature_request.md` | A user story from the backlog | `story`, `epic:<a-d>`, MoSCoW label |
| `bug_report.md` | A defect, with steps to reproduce and the expected rule it violates | `bug`, severity |

Rules: one issue per deliverable · every issue links to the branch that implements it · an issue is
closed by the merge commit, not by hand · a defect is never closed without a test that would have
caught it.

## 6. Local verification before pushing

```bash
source scripts/env.sh
bash scripts/preflight.sh     # toolchain, daemon, ports
mvn clean verify              # unit tests + Selenium journeys
```

If `mvn clean verify` is not green, the commit does not leave the laptop.
