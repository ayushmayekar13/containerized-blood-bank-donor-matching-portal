# Task 14 — Automated Provisioning and Reliability Validation

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 8 October 2026

| Required deliverable | Delivered as |
|---|---|
| Provisioned node | [`evidence/task-14-reliability/clean-provision.log`](../evidence/task-14-reliability/clean-provision.log) — container and local images destroyed, then provisioned from nothing |
| Idempotency evidence | [`idempotency-second-run.log`](../evidence/task-14-reliability/idempotency-second-run.log) — **`ok=29 changed=0`** |
| Health-check result | Behavioural assertions in every run: authentication, compatibility matrix, role guard, dashboard |
| Rollback / recovery demonstration | [`rollback-run.log`](../evidence/task-14-reliability/rollback-run.log) — **17 → 15**, healthy, all 39 donors intact |
| Release tag | `v1.2.0` |

---

## 1. Provisioning a clean environment

The container **and the local images** were destroyed, then the node was provisioned from nothing:

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

## 2. Idempotency — `changed=0`

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

**This is the task's central claim**, and three specific decisions are what produce it:

| Decision | Without it |
|---|---|
| `changed_when: false` on every task that only *checks* something | Every pre-flight check would report a change, and `changed` would stop meaning anything |
| `comparisons: '*': strict` on the `docker_container` tasks | The module compares *declared* state against *actual* state. Without it, Docker's own defaults differ from what was asked for and the container is recreated on every run |
| `force_source: false` on the image pull | Already have this exact immutable tag? Then there is nothing to do |

Idempotency is not a nicety. It is what makes a playbook safe to run on a schedule, safe to run when
nobody is sure whether it already ran, and safe to run during an incident. **A playbook that must
only be run once is a script with extra syntax.**

## 3. Health check — behavioural, not a ping

Both `site.yml` and `rollback.yml` include the same checks, so a provision and a recovery are held to
the same standard:

```
ok: FR-24  an unauthenticated API call is rejected
ok: FR-14  a B+ shortlist contains no incompatible donor
ok: NFR-07 role guard enforced (HTTP 403)
ok: FR-22  the dashboard reports all eight blood groups
```

A Tomcat that answers `200` while the matching engine is broken has failed in the only way that
matters to a blood bank.

## 4. Rollback

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

### Why a rollback is possible at all

Three properties, each established deliberately in an earlier task:

1. **Images are tagged with the build number and never overwritten** (Task 12) — so an earlier
   version still exists *and still has a name*. Deploying `latest` would make this impossible.
2. **The registry retains them on a volume** — so they survive a registry restart.
3. **All state lives on a separate volume** (Task 11) — so replacing the container does not touch the
   data.

### The rollback uses the same code path as a deployment

`rollback.yml` includes the **same `app` role**, with the tag overridden. A recovery path that
differs from the normal path is one that has never really been tested, because it is only exercised
during incidents.

### Two safety properties

- **The target must be named.** `rollback_to` has no default. Silently rolling back to "whatever
  seems reasonable" during an incident is how the wrong version reaches production.
- **The target is verified in the registry *before* the running container is touched.** Discovering
  that the tag is missing *after* removing the current container would turn a rollback into an
  outage.

## 5. The most instructive failure in this task

The first rollback attempt **failed its verification step, even though the rollback had worked
perfectly.**

The `app` role asked the application what version it was, via `/actuator/info`. Tag 15 predates the
Actuator security fix from Task 13, so on that image the endpoint redirected to the HTML login page —
and the check failed.

**The lesson is general and worth stating plainly: verify a deployment using facts the *deployer*
owns, not features of the artefact being deployed.** An older release cannot be expected to support a
newer introspection endpoint, and a rollback that can only verify versions *newer* than the bug it is
escaping is useless precisely when it is needed.

Verification now reads the container's image reference from the Docker API:

```yaml
- deployed.container.Config.Image == app_image
- deployed.container.State.Running
```

That works for every version, past and future. The rollback also now asserts that the data volume is
still attached — because "it came back up" and "it came back up with the data" are different claims.

## 6. The four reliability properties, and the evidence for each

| Property | Requirement | Evidence |
|---|---|---|
| Provisioning from nothing | US-15 | Container + local images destroyed → `changed=2`, healthy, 39 donors |
| **Idempotency** | AC-15.2 | Second identical run → **`changed=0`** |
| Behavioural health check | AC-15.3 | Four rule assertions in every run |
| **Rollback** | AC-15.4, NFR-05 | 17 → 15 in under a minute, healthy, data intact |
| Crash recovery | NFR-03 | `restart: unless-stopped` declared in `group_vars` and asserted on the container |

## 7. Verification

```bash
cd ansible

# provision, then prove idempotency
ansible-playbook site.yml
ansible-playbook site.yml                      # changed=0

# clean-environment provisioning
docker rm -f bloodbank-app
docker rmi -f localhost:5001/bloodbank:17
ansible-playbook site.yml                      # pulls from the registry, rebuilds the node

# rollback and recovery
curl -s http://localhost:5001/v2/bloodbank/tags/list      # what is available
ansible-playbook rollback.yml -e rollback_to=15
docker inspect bloodbank-app --format '{{.Config.Image}}'
curl -s -u staff:Staff@123 http://localhost:9090/api/donors | jq length   # data intact

# roll forward again
ansible-playbook site.yml
```
