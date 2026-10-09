# Task 14 — Automated Provisioning and Reliability Validation

**Deliverable:** provisioned node, idempotency evidence, health-check result and rollback/recovery
demonstration.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 8 October 2026 · **Tags:** `task-14`, `v1.2.0`

---

## 1. Provisioning a clean environment

The container **and the local images** were destroyed, then the node was provisioned from nothing.

```
state after destruction:
  containers:           0 application container(s)
  local images:         0
  registry still holds: {"name":"bloodbank","tags":["17","latest","1","15"]}
  data volume:          bloodbank-data

###  PROVISION FROM NOTHING  ###
TASK [app : Report the current deployment]
  "no application container present - this is a clean provision"
TASK [app : Pull the pinned image from the registry]            changed
TASK [app : Ensure the application container is running ...]    changed
TASK [Health check summary]
  "http://localhost:9090 is healthy AND behaving correctly: authentication enforced,
   18 compatible donors shortlisted for B+, role guard returns 403, dashboard reports
   8 blood groups, 39 donors registered."

PLAY RECAP   ok=29  changed=2  failed=0

state after provisioning from nothing:
  running image: localhost:5001/bloodbank:17
  donors:        39   (data survived the container and image being destroyed)
  health:        {"status":"UP","groups":["liveness","readiness"]}
```

Two things are worth pointing at. The **registry** is what made recovery possible — the image was
gone locally and was pulled back by name. The **volume** is what made it painless: 39 donors were
there before and after, because the container holds no state.

---

## 2. Idempotency

```
$ ansible-playbook site.yml          # first run
PLAY RECAP   ok=29  changed=2  failed=0

$ ansible-playbook site.yml          # immediately again
TASK [app : Report the current deployment]
  "currently running localhost:5001/bloodbank:17"
TASK [app : Report whether the container was replaced]
  "container already matches the declared state - nothing to do"

PLAY RECAP   ok=29  changed=0  failed=0
```

**This is the task's central claim**, and three specific decisions produce it.

| Decision | Without it |
|---|---|
| `changed_when: false` on every task that only *checks* something | Every pre-flight check would report a change, and `changed` would stop meaning anything |
| `comparisons: '*': strict` on the `docker_container` tasks | The module compares *declared* state against *actual* state. Without it, Docker's own defaults differ from what was asked for and the container is recreated on every run |
| `force_source: false` on the image pull | Already have this exact immutable tag? Then there is nothing to do |

### 2.1 Why it matters

Idempotency is not a nicety. It is what makes a playbook safe to run on a schedule, safe to run when
nobody is sure whether it already ran, and safe to run during an incident. **A playbook that must
only be run once is a script with extra syntax.**

---

## 3. Health check — behavioural, not a ping

Both `site.yml` and `rollback.yml` include the same checks, so a provision and a recovery are held
to the same standard:

```
ok: FR-24  an unauthenticated API call is rejected
ok: FR-14  a B+ shortlist contains no incompatible donor
ok: NFR-07 role guard enforced (HTTP 403)
ok: FR-22  the dashboard reports all eight blood groups
```

A Tomcat that answers `200` while the matching engine is broken has failed in the only way that
matters to a blood bank.

---

## 4. Rollback and recovery

```
$ ansible-playbook rollback.yml -e rollback_to=15

STATE BEFORE
  running image: localhost:5001/bloodbank:17
  donors:        39

TASK [Confirm the rollback target actually exists in the registry]   ok
TASK [Announce the rollback]
  "Rolling bloodbank-node-01 back from 'localhost:5001/bloodbank:17' to tag '15'.
   Data on volume bloodbank-data is untouched."
TASK [app : Ensure the application container is running the pinned image]  changed
TASK [Health check summary]                                           ok
TASK [Confirm the data volume is still attached and untouched]
  "bloodbank-data still attached - no data was touched by the rollback"
TASK [Rollback summary]
  "was: localhost:5001/bloodbank:17 | now: localhost:5001/bloodbank:15 |
   running: True | data volume bloodbank-data still attached and untouched."

PLAY RECAP   ok=23  changed=1  failed=0

STATE AFTER
  running image: localhost:5001/bloodbank:15
  donors:        39
  health:        {"status":"UP"}
```

**Under a minute, no rebuild, no data loss.**

### 4.1 Why a rollback is possible at all

Three properties, each established deliberately in an earlier task:

1. **Images are tagged with the build number and never overwritten** (Task 12) — so an earlier
   version still exists *and still has a name*. Deploying `latest` would make this impossible.
2. **The registry retains them on a volume** — so they survive a registry restart.
3. **All state lives on a separate volume** (Task 11) — so replacing the container does not touch the
   data.

### 4.2 The rollback uses the same code path as a deployment

`rollback.yml` includes the **same `app` role**, with the tag overridden. A recovery path that
differs from the normal path is one that has never really been tested, because it is only exercised
during incidents.

### 4.3 Two safety properties

- **The target must be named.** `rollback_to` has no default. Silently rolling back to "whatever
  seems reasonable" during an incident is how the wrong version reaches production.
- **The target is verified in the registry *before* the running container is touched.** Discovering
  that the tag is missing *after* removing the current container would turn a rollback into an
  outage.

---

## 5. Problems met and solved

### 5.1 The first rollback failed its verification, although the rollback worked

The `app` role asked the application what version it was, via `/actuator/info`. Tag 15 predates the
Actuator security fix from Task 13, so on that image the endpoint redirected to the HTML login page —
and the check failed, even though the rollback itself had worked perfectly.

> **Verify a deployment using facts the *deployer* owns, not features of the artefact being
> deployed.** An older release cannot be expected to support a newer introspection endpoint, and a
> rollback that can only verify versions *newer* than the bug it is escaping is useless precisely
> when it is needed.

**Fix.** Verification now reads the container's image reference from the Docker API:

```yaml
- deployed.container.Config.Image == app_image
- deployed.container.State.Running
```

That works for every version, past and future. The rollback also now asserts that the data volume is
still attached — because "it came back up" and "it came back up with the data" are different claims.

### 5.2 A non-deterministic idempotency result

One capture of the reliability run reported `changed=1` where every other run reported `changed=0`,
and the log recorded only the count — so finding out which task was responsible meant running the
playbook by hand afterwards, by which time the environment had moved on.

**Fix.** The check now prints the **name of every task** that reported a change. A failing assertion
that says only `changed=1` is a failure you have to reproduce before you can read it; naming the task
makes it diagnosable from the captured log alone, which is the whole reason for capturing logs.

---

## 6. One command for all four properties

`bash scripts/verify-reliability.sh` performs the whole demonstration and **asserts** each step:

```
1 / 4  PROVISION A CLEAN ENVIRONMENT
  ok   provisioned from nothing: localhost:5001/bloodbank:17, 39 donors (data survived)

2 / 4  IDEMPOTENCY - the same run again must change nothing
  ok   IDEMPOTENT - the second run reported changed=0

3 / 4  BEHAVIOURAL HEALTH CHECK
  ok   STAFF fulfilling a request is refused => 403
  HEALTH CHECK PASSED

4 / 4  ROLLBACK AND RECOVERY
  ok   ROLLED BACK to a different image with the data intact
  ok   back on localhost:5001/bloodbank:17 with 39 donors

ALL FOUR RELIABILITY PROPERTIES VERIFIED
```

It is one command because a reliability claim that takes eight remembered commands to show is a
claim that will not be shown — and under examination conditions, a forgotten step reads as a
property that does not hold. It picks the rollback target automatically as the newest published tag
that is not the one deployed, so the demonstration does not depend on remembering which versions
exist.

---

## 7. The four reliability properties, and the evidence for each

| Property | Requirement | Evidence |
|---|---|---|
| Provisioning from nothing | US-15 | Container **and local images** destroyed → `changed=2`, healthy, 39 donors |
| **Idempotency** | AC-15.2 | Second identical run → **`changed=0`** |
| Behavioural health check | AC-15.3 | Four rule assertions in every run |
| **Rollback** | AC-15.4, NFR-05 | 17 → 15 in under a minute, healthy, data intact |
| Crash recovery | NFR-03 | `restart: unless-stopped` declared and asserted on the container |

---

## 8. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| `changed_when: false` on every check-only task | Otherwise every pre-flight check reports a change and "changed" stops meaning anything |
| `comparisons: strict` on the container tasks | The module then compares declared state against actual state rather than against Docker's defaults, which differ and would recreate the container every run |
| The rollback uses the same `app` role as a deployment | A recovery path that differs from the normal path is only exercised during incidents — the worst time to find out it is broken |
| `rollback_to` has no default | Silently rolling back to "whatever seems reasonable" is how the wrong version reaches production |
| The target is verified in the registry **before** the container is touched | Finding it missing afterwards would turn a rollback into an outage |
| Verification reads the container's image reference, not the application's self-report | An older release cannot be expected to support a newer introspection endpoint |

---

## 9. Evidence index

| File | Shows |
|---|---|
| [`evidence/task-14-reliability/clean-provision.log`](../evidence/task-14-reliability/clean-provision.log) | Provisioning after destroying the container and the local images |
| [`evidence/task-14-reliability/idempotency-second-run.log`](../evidence/task-14-reliability/idempotency-second-run.log) | **`ok=29 changed=0`** |
| [`evidence/task-14-reliability/rollback-run.log`](../evidence/task-14-reliability/rollback-run.log) | 17 → 15, health-checked, 39 donors intact |
| [`evidence/task-14-reliability/verify-reliability-full-run.log`](../evidence/task-14-reliability/verify-reliability-full-run.log) | All four properties in one asserting run |
| [`ansible/rollback.yml`](../ansible/rollback.yml) | The rollback playbook and its safety checks |

---

## 10. Task 14 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D7 | No hard-coded value; the image tag is a variable | `-e app_image_tag=…` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-14-reliability/` |
| D10 | The provisioned and rolled-back container both answer the health check | Behavioural check in both playbooks |
| D11 | Committed on `main` and tagged | `task-14`, `v1.2.0` |
| D12 | Backlog updated | US-15 Done |

**Outcome:** all four reliability properties demonstrated rather than asserted — provisioning from
nothing after the container and local images were destroyed, `changed=0` on an immediate rerun,
behavioural health assertions in every run, and a rollback from image 17 to 15 in under a minute with
the data untouched. The rollback uses the same role as a deployment, so the recovery path is
exercised every day rather than only during incidents.
