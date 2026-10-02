# Task 6 — MVP Completion and Git Collaboration

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 2 October 2026 · **Branch:** `feature/request-workflow` → `develop` → `main` → `v1.0.0`

| Required deliverable | Delivered as |
|---|---|
| Functional MVP | All of create / view / update / search, the role-based status workflow, and the summary dashboard — feature table in §1 |
| Second feature branch | `feature/request-workflow` (plus `fix/navbar-health-indicator`, which created the conflict) |
| Resolved merge conflict | `layout.html`, created and resolved deliberately — §3 and [`evidence/task-06-conflict/`](../evidence/task-06-conflict/README.md) |
| Tagged version | `v1.0.0` on `main`, annotated |
| Release-ready source baseline | `main` builds clean, 178 tests green, WAR runs in all three modes |
| Updated backlog | [`docs/backlog.md`](backlog.md) — defects BUG-03, BUG-04, BUG-05 recorded with cause and fix |

---

## 1. The MVP, against what the task sheet asked for

| Task-sheet requirement | Delivered |
|---|---|
| **Create** records | Donor registration (FR-01); blood request recording (FR-10) |
| **View** records | Donor list and detail; request worklist and detail with the audit trail |
| **Update** records | Donor edit, availability toggle, donation recording; request transitions |
| **Search** records | Donors by group + city + availability; requests by status + group — filters combined with AND (FR-04, FR-12) |
| **Role-based status workflow** | `NEW → MATCHED → FULFILLED / REJECTED`; STAFF may match, only ADMIN may fulfil or reject; illegal transitions refused with 409, wrong roles with 403 (FR-17, FR-18) |
| **Summary dashboard** | Registered and callable donors per group, requests per status, shortage flags (FR-22, FR-23) |

Beyond the minimum, because the DevOps tasks need it: authentication with three roles (a pipeline
deploying an application with no authorisation rules cannot demonstrate a quality gate catching an
authorisation regression), and a REST API mirroring every screen so that the health check, the
Selenium fixtures and the viva commands can assert against the same rules.

## 2. Git collaboration operations demonstrated

```bash
# second feature branch
git checkout develop && git checkout -b feature/request-workflow
git commit            # 'feat: add blood requests with role-guarded status workflow'

# meanwhile develop moves - a small fix branch, merged --no-ff
git checkout develop && git checkout -b fix/navbar-health-indicator
git commit            # 'fix: surface the health endpoint in the navigation'
git checkout develop && git merge --no-ff fix/navbar-health-indicator
git branch -d fix/navbar-health-indicator

# back on the feature branch
git checkout feature/request-workflow
git commit            # 'feat: add authentication, three roles, request screens and dashboard'

# bring develop in - this is where the conflict happens
git merge develop                    # CONFLICT in fragments/layout.html
# ... resolve, then:
mvn clean verify                     # 178 tests green BEFORE committing the resolution
git add src/main/resources/templates/fragments/layout.html
git commit                           # merge commit records the resolution

# review, then promote
git checkout develop
git merge --no-ff feature/request-workflow -m "Merge PR-02: ..."
git tag -a task-06 -m "Task 6 - MVP Completion and Git Collaboration"
git branch -d feature/request-workflow

git checkout main
git merge --no-ff develop -m "Release v1.0.0: functional MVP"
git tag -a v1.0.0 -m "Functional MVP: donor registry, matching engine, role-based workflow, dashboard"
```

**Why the conflict was resolved on the feature branch, not on `develop`.** Merging `develop` *into*
the feature branch keeps the integration branch clean: if the resolution turns out to be wrong, the
feature branch is thrown away and `develop` is untouched. Resolving on `develop` would put a
possibly broken merge on the branch everything else builds from. This is the rule in
`CONTRIBUTING.md` §3 item 7, and it is the reason the conflict appears in the graph *below* the
`develop` line rather than on it.

## 3. The merge conflict — created, resolved, and why it happened

Both branches edited **the last `<li>` of the same navigation list**:

| Branch | Change |
|---|---|
| `develop` | An unconditional **Health** link with a status dot, so operations can reach `/actuator/health` in one click |
| `feature/request-workflow` | A role-conditional **My profile** link for `DONOR`, beside the new Dashboard and Requests links |

Git merged every other hunk in the file automatically. It could not merge this one, because both
sides inserted different content at the same position, and nothing in the diff tells Git whether
one replaces the other or both belong.

```
$ git merge develop
Auto-merging src/main/resources/templates/fragments/layout.html
CONFLICT (content): Merge conflict in src/main/resources/templates/fragments/layout.html
Automatic merge failed; fix conflicts and then commit the result.

$ git status --short
M  src/main/resources/static/css/app.css        <- merged automatically, different hunks
UU src/main/resources/templates/fragments/layout.html   <- both modified, unmerged
```

**Resolution: keep both.** They serve different people and neither supersedes the other. The
role-conditional item comes first, the universal one last, and the reasoning is left as a comment
in the template so the next person to edit that list does not "tidy" one away.

**The judgement Git could not make:** should the Health link be restricted to STAFF and ADMIN? It
was left unconditional, because `/actuator/health` is deliberately unauthenticated for the
container probe and the Ansible health check (FR-26) — hiding the link would protect nothing while
removing useful information from a donor. That decision is the part of conflict resolution a tool
cannot do.

**The full suite was run on the resolved tree before committing.** A conflict resolution is the one
change no diff review can validate, because the result exists in neither parent and neither side's
tests were ever run against it. Committing an unverified resolution is how a merge breaks a build
that both branches passed.

Full evidence, including the conflicted file with its markers preserved, is in
[`evidence/task-06-conflict/README.md`](../evidence/task-06-conflict/README.md).

## 4. Three defects found, and what they say about the project

| ID | Symptom | Root cause | Fix | Regression test |
|---|---|---|---|---|
| **BUG-03** | Dashboard showed 8 callable donors with 1 registered | The total summed the per-group figures, which deliberately **overlap**: one O− donor is callable for all eight recipient groups | The distinct count is the shortlist for **AB+**, the universal recipient | `callableTotalDoesNotDoubleCount` |
| **BUG-04** | Request table sometimes empty at startup | Two separate `ApplicationRunner` beans have **no guaranteed order**; requests were seeded before donors and the guard skipped them silently | One ordered runner | covered by the seeded-data assertions |
| **BUG-05** | Worklist showed MEDIUM above HIGH above CRITICAL | `urgency` is `@Enumerated(STRING)`, so `order by r.urgency desc` sorted the stored **text** — alphabetical, and backwards for the two that matter | Ordering moved to the service using `Urgency.getWeight()` | `worklistIsOrderedBySeverity` |

All three were found by **exercising the running application over HTTP after the unit suite was
already green.** BUG-05 is the sharpest example: the unit tests asserted *which* requests came
back and never in what order, and because the ordering lived in SQL, a JPA test over H2 agreed with
production — both wrong in the same way.

That is the concrete argument for the next two tasks. Task 9 automates the manual HTTP checking
that found these three defects, and Task 10 makes it a gate that blocks deployment. Without them,
"the unit tests pass" would continue to mean less than it sounds.

## 5. Release baseline

```
$ git checkout main && mvn clean verify
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ ls -l target/bloodbank.war
-rw-r--r--  1 ayushmayekar  staff  ~57M  target/bloodbank.war

$ git tag -l
task-01 task-02 task-03 task-04 task-05 task-06 v1.0.0
```

| Tag | Marks |
|---|---|
| `task-06` | On `develop`, at the PR-02 merge — the state at which this task's deliverable was complete |
| `v1.0.0` | On `main` — the release-ready source baseline, annotated |

## 6. Verification

```bash
source scripts/env.sh
mvn clean verify                         # 178 tests
java -jar target/bloodbank.war

# sign in as each role and confirm the navigation differs
open http://localhost:9090/login         # accounts are listed on the page

# the headline workflow assertions
curl -s -o /dev/null -w '%{http_code}\n' -X POST -u staff:Staff@123 \
     http://localhost:9090/api/requests/1/fulfil                       # 403 wrong role
curl -s -X POST -u admin:Admin@123 http://localhost:9090/api/requests/2/fulfil \
     | jq '.status, .error'                                            # 409 illegal transition
curl -s -X POST -u staff:Staff@123 -H 'Content-Type: application/json' \
     -d '{"donorId":2}' http://localhost:9090/api/requests/1/match | jq '.status'   # MATCHED
curl -s -X POST -u admin:Admin@123 http://localhost:9090/api/requests/1/fulfil \
     | jq '.status'                                                    # FULFILLED
curl -s -u staff:Staff@123 http://localhost:9090/api/donors/2 | jq '.eligible'      # false

# the shortage, demonstrated live
curl -s -X PATCH -u staff:Staff@123 -H 'Content-Type: application/json' \
     -d '{"available":false}' http://localhost:9090/api/donors/1/availability >/dev/null
curl -s -u admin:Admin@123 http://localhost:9090/api/dashboard | jq '.shortages'    # ["O-"]

# the conflict, after the fact
git log --merges --oneline
grep -n 'data-testid="nav-me"'     src/main/resources/templates/fragments/layout.html
grep -n 'data-testid="nav-health"' src/main/resources/templates/fragments/layout.html
```

Every command above was run and produced the stated output.
