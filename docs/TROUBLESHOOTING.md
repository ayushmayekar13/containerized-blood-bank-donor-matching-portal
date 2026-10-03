# Troubleshooting Log

Project: Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018

Every non-obvious problem hit while building this project, what actually caused it, and how it was
fixed. Entries are added in the same commit as the fix, which is item 6 of the
[Definition of Done](definition-of-done.md). The examiner's likely question for most of these is
"how did you work that out?", so each entry records the diagnosis, not only the remedy.

---

## T-01 — `/match` returned HTTP 500: `Property or field 'rhPositive' cannot be found on null`

**Task 5.** The match screen threw a 500 while the donor list, which renders the same blood-group
badge fragment, worked.

**Diagnosis.** The failing fragment was reached from two places. On the working page the
`th:each` was on a `<tr>` and the `th:replace` on a `<span>` inside it; on the failing page both
attributes were on the *same* element.

**Cause.** Thymeleaf attribute precedence. `th:replace` has a *higher* precedence (lower number)
than `th:each`, so on a single element the fragment substitution runs **before** the iteration
variable exists — hence a null `group`.

**Fix.** Put `th:each` on an outer element and `th:replace` on the inner one.

```html
<!-- wrong: th:replace runs first, g does not exist yet -->
<span th:each="g : ${groups}" th:replace="~{fragments/layout :: groupBadge(${g})}"></span>

<!-- right -->
<span th:each="g : ${groups}">
  <span th:replace="~{fragments/layout :: groupBadge(${g})}"></span>
</span>
```

**Lesson.** When two Thymeleaf attributes on one tag behave unexpectedly, check the precedence
table before anything else.

---

## T-02 — A duplicate phone number returned HTTP 500 instead of 409

**Task 5.** `POST /api/donors` with an already-registered phone number produced a 500.

**Cause.** `DuplicatePhoneException` was thrown but had no handler, so it reached the container as
an unhandled exception.

**Fix.** Mapped it to **409** in `GlobalExceptionHandler`, alongside 404 for unknown ids.

**Lesson.** A 500 for a *foreseeable* conflict is a contract violation, not just an untidy message:
a client cannot distinguish "you sent a duplicate" from "the database is down".

---

## T-03 — The navigation role badge broke every authenticated page

**Task 6.** After authentication was added, every signed-in screen failed with
`EL1008E: Property or field 'this' cannot be found on object of type 'java.lang.String'`, while the
same pages had rendered fine in Task 5.

**Diagnosis.** The expression only evaluated when a user was authenticated; in Task 5
`sec:authorize="isAuthenticated()"` was always false, so the broken expression was never reached.

**Cause.** A too-clever projection over the authorities collection,
`${#authentication.authorities.![authority].![#strings.replace(this,'ROLE_','')][0]}` — the second
projection has no `this` to refer to.

**Fix.** Three explicit `sec:authorize` blocks, one per role.

**Lesson.** Code behind a flag that is always false is untested code. Clever one-liners in a
template cost more than three plain lines.

---

## T-04 — The dashboard reported 8 callable donors with 1 donor registered (BUG-03)

**Task 6.** Found by reading `/api/dashboard` after the unit suite was green.

**Cause.** The total was the **sum of the per-group figures**, and those figures deliberately
overlap: a single O− donor is callable for all eight recipient groups, so it was counted eight
times.

**Fix.** The distinct number of people who could be phoned today is exactly the shortlist for
**AB+**, because AB+ is the universal recipient and therefore accepts every group. Regression test
asserts the total is strictly *less than* the sum whenever more than one group is represented.

**Lesson.** When a figure is derived from overlapping sets, state which set it counts. The fix is
one line; recognising that the sets overlap is the whole problem.

---

## T-05 — The request table was sometimes empty at startup (BUG-04)

**Task 6.** The seeded requests appeared on some runs and not others, with no error logged.

**Cause.** Two separate `ApplicationRunner` beans. Spring gives them **no guaranteed order**, so
`seedRequests` sometimes ran before `seedDonors`, saw an empty donor table, and skipped silently by
design.

**Fix.** One runner that calls the two seeding methods in order.

**Lesson.** "Sometimes" in a startup sequence almost always means ordering. Two `ApplicationRunner`
beans with a data dependency between them is a race, not a layout choice.

---

## T-06 — The worklist showed MEDIUM above HIGH above CRITICAL (BUG-05)

**Task 6.** The request list claimed to be ordered most-urgent-first and visibly was not, while the
unit tests were green.

**Cause.** `urgency` is `@Enumerated(STRING)`, so `order by r.urgency desc` sorted the **stored
text**: `MEDIUM`, `HIGH`, `CRITICAL` — alphabetical, and exactly backwards for the two that matter.

**Why the tests missed it.** They asserted *which* requests came back and never in what order. The
ordering lived in SQL, so a JPA test over H2 agreed with production — both wrong in the same way.

**Fix.** Ordering moved to the service using `Urgency.getWeight()`, with the reason recorded on both
the query and the comparator so nobody "optimises" it back into SQL. Regression test asserts the
exact sequence.

**Lesson.** Ordering an `@Enumerated(STRING)` column in SQL sorts the label, not the meaning. Either
store a weight column or sort in code — and assert the sequence, not just the set.

---

## T-07 — Jenkins refused to clone the repository: "references a local directory, which may be insecure"

**Task 7.** The first build failed immediately:

```
ERROR: Checkout of Git remote '/Users/ayushmayekar/code/College/devops_project' aborted because it
references a local directory, which may be insecure. You can allow local checkouts anyway by setting
the system property 'hudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT' to true.
```

**Cause.** The Git plugin blocks cloning from a local filesystem path by default. On a shared
controller, a job defined by an untrusted user could otherwise read any directory the Jenkins
process can see. This repository has no remote yet, so the job's URL *is* a local path.

**Fix.** `-Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true` on the Jenkins JVM, added to
`scripts/jenkins-setup.sh` with the reasoning in a comment.

**Why that is acceptable here, and when it would not be.** This is a single-user controller cloning
its own developer's working copy; there is no untrusted job author to defend against. On a shared
controller the right answer is a real remote, never this flag. It stops being needed the moment the
repository is pushed and `REPO_URL` becomes a GitHub URL.

---

## T-08 — `mapfile: command not found` in the Jenkins setup script

**Task 7.** `scripts/jenkins-setup.sh plugins` failed on the plugin list.

**Cause.** macOS ships **bash 3.2** (2007), which has no `mapfile`/`readarray`.

**Fix.** Read the list into a plain space-separated word list with `sed`/`awk`/`tr`.

**Lesson.** A script that has to run on the machine the project is demonstrated on is constrained by
*that* machine's shell. `#!/usr/bin/env bash` on macOS means bash 3.2 unless a newer bash is
explicitly installed and first on `PATH`.
