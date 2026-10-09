# Task 6 — MVP Completion and Git Collaboration

**Deliverable:** functional MVP, resolved merge conflict, tagged version and updated backlog.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 2 October 2026 · **Tags:** `task-06`, `v1.0.0` · **Pull request:** [PR-02](pr/PR-02-request-workflow.md)

---

## 1. The MVP, against what the task sheet asked for

| Task-sheet requirement | Delivered |
|---|---|
| **Create, view, update and search** records | Donor registration, list, detail, edit, availability toggle, donation recording; request recording, worklist, detail with audit trail; search on both with filters combined by AND |
| **Role-based status workflow** | `NEW → MATCHED → FULFILLED / REJECTED`; STAFF may match, only ADMIN may fulfil or reject; illegal transitions refused with 409, wrong roles with 403 |
| **Summary dashboard** | Registered and callable donors per group, requests per status, shortage flags |

Beyond the minimum, because the DevOps tasks need it: authentication with three roles, and a REST
API mirroring every screen so the health check, the Selenium fixtures and the viva commands assert
against the same rules.

---

## 2. The role-based workflow

```
            Staff or Admin            Admin
  NEW ─────────────────────► MATCHED ──────► FULFILLED   (terminal)
   │                            │
   └──────────── Admin ─────────┴──────────► REJECTED    (terminal)
```

### 2.1 Two guards per transition, checked in a fixed order

| Guard | Question | Decided by | Failure |
|---|---|---|---|
| 1 | Is this role permitted? | `RequestService` | **403** |
| 2 | Is the transition legal? | `RequestStatus.canMoveTo` | **409** |

Staff attempting `NEW → FULFILLED` violates both. **The role is checked first**, because telling
someone a transition is impossible when they were never allowed to attempt it is misleading.

### 2.2 Why the two closed states are terminal

A `FULFILLED` or `REJECTED` request is never reopened; it is superseded by a new request. That keeps
the audit trail of what actually happened intact.

---

## 3. Git collaboration operations

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
git commit                           # the merge commit records the resolution

# review, then promote
git checkout develop
git merge --no-ff feature/request-workflow -m "Merge PR-02: ..."
git tag -a task-06 && git branch -d feature/request-workflow
git checkout main && git merge --no-ff develop -m "Release v1.0.0: functional MVP"
git tag -a v1.0.0 -m "Functional MVP: donor registry, matching engine, role-based workflow, dashboard"
```

---

## 4. The merge conflict

### 4.1 How it arose

Both branches edited **the last `<li>` of the same navigation list**:

| Branch | Change |
|---|---|
| `develop` | An unconditional **Health** link with a status dot, so operations can reach `/actuator/health` in one click |
| `feature/request-workflow` | A role-conditional **My profile** link for `DONOR`, beside the new Dashboard and Requests links |

Git merged every other hunk in the file automatically. It could not merge this one, because both
sides inserted different content at the same position, and nothing in the diff tells Git whether one
replaces the other or both belong.

### 4.2 As Git reported it

```
$ git merge develop
Auto-merging src/main/resources/templates/fragments/layout.html
CONFLICT (content): Merge conflict in src/main/resources/templates/fragments/layout.html
Automatic merge failed; fix conflicts and then commit the result.

$ git status --short
M  src/main/resources/static/css/app.css              <- merged automatically, different hunks
UU src/main/resources/templates/fragments/layout.html <- both modified, unmerged
```

`UU` means **both modified**. `app.css` shows plain `M` because the two branches touched different
parts of it.

```html
<<<<<<< HEAD
      <li class="nav-item" sec:authorize="hasRole('DONOR')">
        <a class="nav-link" th:href="@{/me}" data-testid="nav-me">My profile</a>
=======
      <li class="nav-item">
        <a class="nav-link bb-nav-health" th:href="@{/actuator/health}" target="_blank"
           data-testid="nav-health"><span class="bb-health-dot"></span> Health</a>
>>>>>>> develop
      </li>
```

`<<<<<<< HEAD` to `=======` is **our** side — the branch being merged *into*. `=======` to
`>>>>>>> develop` is **their** side.

### 4.3 The resolution

**Both changes were kept.** They serve different people and neither replaces the other: the profile
link is for a donor, the health link is for whoever operates the deployment. The role-conditional
item comes first, the universal one last, and the reasoning is recorded as a comment in the template
so the next person to touch that list does not tidy one away.

**The judgement Git could not make:** should the Health link be restricted to STAFF and ADMIN? It
was left unconditional, because `/actuator/health` is deliberately unauthenticated for the container
probe and the Ansible check (FR-26) — hiding the link would protect nothing while removing useful
information from a donor.

### 4.4 Why it was resolved on the feature branch

Merging `develop` **into** the feature branch keeps the integration branch clean: if the resolution
turns out to be wrong, the feature branch is thrown away and `develop` is untouched. Resolving on
`develop` would put a possibly broken merge on the branch everything else builds from.

### 4.5 Why the full suite ran before committing the resolution

A conflict resolution is the one kind of change that no diff review can fully validate: **the result
exists in neither parent**, so neither side's tests were ever run against it. Committing an
unverified resolution is how a merge breaks a build that both branches passed. 178 tests were run on
the resolved working tree first, and only then committed.

---

## 5. Problems met and solved

Three real defects were found while verifying this task — all by **exercising the running
application over HTTP after the unit suite was already green**.

### 5.1 BUG-03 — the dashboard reported 8 callable donors with 1 registered

**Cause.** The total was the **sum of the per-group figures**, and those figures deliberately
overlap: a single O− donor is callable for all eight recipient groups, so it was counted eight
times.

**Fix.** The distinct number of people who could be phoned today is exactly the shortlist for
**AB+**, because AB+ is the universal recipient and therefore accepts every group. A regression test
asserts the total is strictly *less than* the sum whenever more than one group is represented.

### 5.2 BUG-04 — the request table was sometimes empty at startup

**Cause.** Two separate `ApplicationRunner` beans. Spring gives them **no guaranteed order**, so
`seedRequests` sometimes ran before `seedDonors`, saw an empty donor table, and skipped silently by
design.

**Fix.** One runner that calls the two seeding methods in order.

**Lesson.** "Sometimes" in a startup sequence almost always means ordering.

### 5.3 BUG-05 — the worklist showed MEDIUM above HIGH above CRITICAL

**Cause.** `urgency` is `@Enumerated(STRING)`, so `order by r.urgency desc` sorted the **stored
text**: `MEDIUM`, `HIGH`, `CRITICAL` — alphabetical, and exactly backwards for the two that matter.

**Why the tests missed it.** They asserted *which* requests came back and never in what order. The
ordering lived in SQL, so a JPA test over H2 agreed with production — both wrong in the same way.

**Fix.** Ordering moved to the service using `Urgency.getWeight()`, with the reason recorded on both
the query and the comparator so nobody optimises it back into SQL. A regression test asserts the
exact sequence.

These three are the concrete argument for the next two tasks: Task 9 automates the manual HTTP
checking that found them, and Task 10 makes it a gate that blocks deployment.

---

## 6. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Two separate guards per transition, role checked first | "Is it legal?" is the state machine (409); "may this role?" is authorisation (403). Checking the role first avoids a misleading message |
| Services take an `Actor` record, not `SecurityContextHolder` | The workflow rules become unit-testable with no mock authentication, and authorisation sits where the REST API cannot bypass it. Hiding a button is presentation, not security |
| Two security filter chains rather than one | `/api/**` is Basic and stateless, so an unauthenticated call returns 401 — something a script can act on. Everything else is form login, so a browser is redirected. One chain with Basic made every screen answer 401 with a pop-up |
| CSRF disabled on the API chain only | CSRF defends against a request riding on an **ambient session**. The API chain is stateless and credential-per-request, so there is none. The browser chain keeps it enabled |
| Fulfilling stamps the donation in the same transaction | A donation can never be recorded without the request advancing, nor the request advance without the donor becoming ineligible |
| The conflict was resolved on the feature branch | A wrong resolution then costs the feature branch, not the branch everything else builds from |

---

## 7. Release baseline

```
$ git checkout main && mvn clean verify
Tests run: 178, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS

$ git tag -l
task-01 task-02 task-03 task-04 task-05 task-06 v1.0.0
```

| Tag | Marks |
|---|---|
| `task-06` | On `develop`, at the PR-02 merge |
| `v1.0.0` | On `main` — the release-ready source baseline |

---

## 8. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-06-conflict/README.md`](../evidence/task-06-conflict/README.md) | How the conflict arose, how it was resolved, and the commands to verify it afterwards |
| [`evidence/task-06-conflict/layout.html.conflicted`](../evidence/task-06-conflict/layout.html.conflicted) | The file exactly as Git left it, markers preserved |
| [`docs/pr/PR-02-request-workflow.md`](pr/PR-02-request-workflow.md) | Eleven design decisions, seven review comments |
| `git log --merges --oneline` | Both `--no-ff` merges and the conflict resolution |

---

## 9. Task 6 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D1 | Every acceptance criterion met and demonstrable | AC table in PR-02 |
| D2 | Every business rule has a passing test | 178 tests; every transition and role combination |
| D3 | `mvn clean verify` green, including on the conflict resolution | `BUILD SUCCESS` |
| D4 | Business rules in the service layer or a domain enum | `RequestStatus`, `RequestService` |
| D7 | No hard-coded credential | Accounts moved to configuration, BCrypt-hashed |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-06-conflict/` |
| D11 | Merged `--no-ff`; conflict resolved on the feature branch | PR-02 → `develop` → `main` |
| D12 | Backlog updated with BUG-03, BUG-04, BUG-05 | `docs/backlog.md` |

**Outcome:** the functional MVP is complete and tagged `v1.0.0`, a genuine merge conflict was
created, resolved on the feature branch and verified with the full suite before committing, and
three real defects were found and fixed — every one of them by exercising the running application
after the unit tests were already green, which is the argument for everything in Tasks 9 and 10.
