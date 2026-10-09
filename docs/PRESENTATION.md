# Presentation Outline — 10 slides, ~12 minutes

**Containerized Blood Bank Donor Matching Portal**
Ayush Mayekar · 23102A0018 · BE CMPN A · DevOps · AY 2026-27

Each slide lists what goes **on** it and what you **say**. Keep slides sparse — the demonstration is
the evidence, the slides are the argument.

---

## Slide 1 — Title

**On the slide**
Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
One line: *"Commit to running container in 101 seconds, with a browser standing in the way."*

**Say:** Name, roll number, and that the subject of the project is the pipeline, not the web app.

---

## Slide 2 — The problem

**On the slide**
- Blood cannot be manufactured; whole blood lasts ~35–42 days
- A donor is ineligible for **90 days** after donating
- Compatibility is wider than same-group: B+ accepts **B+, B−, O+, O−**
- Still matched by phone call and paper register

**Say:** Three facts make this a real operational problem, and the third is the one the manual
process gets wrong — compatible donors in the same city go uncontacted because nobody does the matrix
in their head. Time-to-match is hours when it should be seconds.

---

## Slide 3 — What the portal does

**On the slide** — one screenshot of the ranked shortlist for a B+ patient in Thane, with the
"Exact group" and "Same city" badges visible.

**Say:** Searchable registry, recorded requests, and a shortlist of donors who are compatible **and**
eligible **and** available — ranked exact-group first to preserve O− stock, then same city, then
longest rested so the load rotates across the register.

---

## Slide 4 — Role-based workflow

**On the slide** — the state machine:
```
NEW ──Staff──► MATCHED ──Admin──► FULFILLED
 └────────── Admin ──────────► REJECTED
```
Two guards: **403** wrong role · **409** illegal transition

**Say:** Only an administrator may confirm a donation. The two guards are separate and the role is
checked first, because telling someone a transition is impossible when they were never allowed to
attempt it is misleading. Fulfilling also stamps the donor's donation date in the same transaction.

---

## Slide 5 — The DevOps pipeline

**On the slide** — the stage diagram with the gate marked:
```
Checkout → Build → Unit Test → Package → Deploy → Health ─┨ UI TEST ┠─► Promote → Verify
                                                            GATE
```
`101 s` · `179 unit tests` · `16 journeys` · `11 stages`

**Say:** Walk it left to right in one sentence. Then: everything that promotes a build sits **after**
the Selenium stage, so a failing journey cannot physically reach it.

---

## Slide 6 — ⭐ The gate stopping a real deployment

**On the slide** — the build #9 stage view, verbatim:
```
Unit Test                SUCCESS   12.2s     179 tests
Package                  SUCCESS    5.3s
Deploy to Tomcat         SUCCESS   10.8s
Health Check             SUCCESS    1.4s
UI Test (quality gate)   FAILED    58.1s   ← journey J4
Promote                  FAILED     0.1s   ← never entered
```

**Say:** *This is the slide that matters.* One `sec:authorize` attribute removed, so the page offered
a STAFF user an ADMIN-only action. The service layer still refused with 403, so no data was at risk —
and every unit test passed. Only a browser could see it, and nothing was promoted.

---

## Slide 7 — Containerization

**On the slide**
- Multi-stage: Maven stage **discarded**, only Tomcat ships
- Runs as **uid 999** and as **PID 1** → stops gracefully in <1 s
- State on a named volume → **29 donors before and after** the container is destroyed
- Build-number tag is immutable; `latest` is never deployed

**Say:** The container is disposable and the data is not — which is exactly what makes
recreate-deployment and rollback safe. Deploying `latest` would make rollback impossible, because
nothing would name the version that was running before.

---

## Slide 8 — Configuration management and recovery

**On the slide**
```
$ ansible-playbook site.yml      ok=29  changed=2
$ ansible-playbook site.yml      ok=29  changed=0   ← idempotent
$ ansible-playbook rollback.yml -e rollback_to=15
  was: bloodbank:17 | now: bloodbank:15 | data volume untouched
```

**Say:** `changed=0` is the claim, demonstrated rather than asserted. Rollback re-deploys an earlier
immutable tag using the **same role** as a deployment — a recovery path that differs from the normal
path is one that has never really been tested.

---

## Slide 9 — What the testing actually caught

**On the slide**

| Defect | Found by | Why it was missed |
|---|---|---|
| Worklist ordered MEDIUM > HIGH > CRITICAL | Manual HTTP | Tests asserted *which* requests, never the order |
| Dashboard showed 8 callable donors for 1 donor | Manual HTTP | Summed deliberately overlapping sets |
| DONOR login landed on HTTP 403 | Selenium | No test walked the login form |
| STAFF offered an ADMIN-only button | Selenium | Service layer still refused it |

**Say:** Eleven defects found and fixed, each with its cause recorded. Four were found by a browser
*after* the unit suite was green — that is the return on building the Selenium suite, stated in
defects rather than in adjectives.

---

## Slide 10 — Summary and limitations

**On the slide**
- 15 tasks, each a tagged commit · `v1.3.0`
- 179 unit tests + 16 journeys · 101 s commit-to-container · rollback <1 min
- **Known limits:** H2 is single-node · donor data unencrypted at rest · only the 90-day rule is
  modelled · "same city" is a string match
- **Next:** PostgreSQL, static analysis in the pipeline, notifications

**Say:** Finish on the limitations deliberately — they are documented in the SRS and tracked in the
backlog, which is the difference between a project that knows what it is not and one that has not
looked.

---

## Timing

| Slides | Minutes |
|---|---|
| 1–4 problem and application | 4 |
| 5–6 pipeline and the gate | 4 |
| 7–8 Docker and Ansible | 3 |
| 9–10 results and limits | 1 |
| **Live demonstration** | **+8–18** (see `VIVA_GUIDE.md` §4) |

## If you only get five minutes

Slides **2, 5, 6, 8, 10**, then run `bash scripts/demo-all.sh --quick`.
