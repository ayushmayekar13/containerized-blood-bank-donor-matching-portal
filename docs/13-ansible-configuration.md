# Task 13 — Configuration Management Script

**Deliverable:** configuration specification, Puppet manifest or Ansible playbook and first
execution log.

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018 · BE CMPN A
**Completed:** 8 October 2026 · **Tag:** `task-13` · **Tool:** Ansible core 2.21.5 with `community.docker`

---

## 1. Why Ansible rather than Puppet

Both were allowed by the task sheet. Ansible was chosen for three reasons that matter to *this*
project.

| | Ansible | Puppet |
|---|---|---|
| Agent on the target | None — talks to the Docker API directly | Needs an agent installed and running |
| Idempotency | A property of each module, so it falls out of writing ordinary tasks | Also idempotent, via a different resource/catalog model |
| On Apple Silicon macOS | `pipx install ansible` and it works | Open-source Puppet on macOS/ARM is awkward and would have to run inside a Linux container — a moving part between the thing *managing* the deployment and the thing *being* deployed |

---

## 2. The configuration specification

The task asks for "all server prerequisites: packages, users, folders, files, ports and services".
[`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml) states each of them **once, as data**.

**No task in any role contains a literal port, path or image name** — they all read these variables,
which is what makes an override a one-flag change:

```bash
ansible-playbook site.yml -e app_image_tag=14 -e app_host_port=9095
```

| Category | Specified | Note |
|---|---|---|
| **Packages** | `docker`, `curl` | Deliberately short. The application ships as a container, so the node needs a runtime and a way to probe it — **not** a JDK, Maven or Tomcat. That shrinkage is the practical payoff of containerising |
| **Users** | uid/gid `999` | Pinned, because the container entrypoint compares the data volume's ownership against it (BUG-11) |
| **Folders** | volume `bloodbank-data` → `/opt/bloodbank/data` | The database must outlive every container |
| **Files** | the image itself, by immutable tag | The artefact *is* the image; there is no file to copy and no config to template on the node |
| **Ports** | app `9090→8080`, registry `5001→5000` | Neither default is used: 8080 is occupied and macOS reserves 5000 |
| **Services** | app + registry containers, `restart: unless-stopped` | NFR-03 — survive a crash and a reboot |

---

## 3. The target node

```ini
[bloodbank_nodes]
bloodbank-node-01 ansible_connection=local
```

The target is this machine, managed through its Docker daemon. Making it a real remote host is
**one line**:

```ini
[bloodbank_nodes]
prod-01 ansible_host=10.0.0.21 ansible_user=deploy
```

Nothing in `site.yml` or in any role refers to `localhost`; every task uses the inventory hostname
and the group variables. That property is the reason for writing it this way rather than hard-coding
connection details into the plays — and it is the honest answer to "this is just running on your
laptop": **the playbook does not know or care**.

---

## 4. The playbook

### 4.1 Three roles in a dependency chain

```
prereqs  →  registry  →  app
```

The ordering is a **dependency chain, not a preference**: the application's image is identified by a
registry URL, so a node whose registry is not running cannot deploy at all.

| Role | Does |
|---|---|
| **prereqs** | Asserts every required command exists; asserts the Docker **daemon responds**, not merely that the CLI is installed; reports what the node is; ensures both data volumes exist |
| **registry** | Ensures the registry container is running, waits for its API, reports its catalogue |
| **app** | Records what is deployed now, pulls the pinned image, ensures the container matches the declared state, waits for health, asserts the running image is the one requested |

### 4.2 Why `prereqs` checks rather than installs

Installing Docker from a playbook on macOS would mean driving a GUI installer, which cannot be done
honestly. The role fails with an actionable message instead of pretending:

```
'docker' is not available on bloodbank-node-01. Install it before provisioning:
on macOS use Docker Desktop or 'brew install docker'; on Debian/Ubuntu use
'apt-get install -y docker'.
```

That is also the right behaviour in general: **a provisioning run that silently installs a container
runtime on a machine that did not have one is not provisioning, it is a surprise.** On a Linux fleet
these `assert` tasks become `package:` tasks and **nothing else in the playbook changes**.

Checking the daemon separately from the binary is not pedantry — during this project the daemon
stopped responding while containers kept running, and "docker is installed" would have been true and
useless.

---

## 5. Health checking is behavioural

[`ansible/roles/app/tasks/health.yml`](../ansible/roles/app/tasks/health.yml) asserts the same rules
`scripts/health-check.sh` does, expressed as Ansible tasks so a run is self-contained.

| Check | Requirement |
|---|---|
| An unauthenticated API call is rejected | FR-24 |
| A B+ shortlist contains no incompatible donor | FR-14 |
| A STAFF user cannot fulfil a request | NFR-07 |
| The dashboard reports all eight blood groups | FR-22 |

```
TASK [Health check summary]
  "http://localhost:9090 is healthy AND behaving correctly: authentication enforced,
   18 compatible donors shortlisted for B+, role guard returns 403, dashboard reports
   8 blood groups, 39 donors registered."
```

"The container is up" and "the application is correct" are different claims, and only the second one
is worth provisioning for.

---

## 6. First execution log

```
$ ansible-playbook site.yml

TASK [prereqs : Check that the Docker daemon is responding, not merely installed]  ok
TASK [prereqs : Report what the node is]
  "bloodbank-node-01: docker 28.1.1, Docker Desktop, 10 CPU, 7.7 GiB RAM"
TASK [registry : Ensure the registry container is running]                        changed
TASK [registry : Show the catalogue]
  "registry at localhost:5001 holds: bloodbank"
TASK [app : Report the current deployment]
  "currently running tomcat:10.1-jdk21-temurin"
TASK [app : Ensure the application container is running the pinned image]          changed
TASK [app : Assert that the application reports UP]                               ok
TASK [Health check summary]                                                        ok

PLAY RECAP
bloodbank-node-01 : ok=28  changed=1  unreachable=0  failed=0
```

---

## 7. Problems met and solved

### 7.1 `The module interpreter '/usr/bin/env python3' was not found`

**Cause.** Ansible `exec()`s the interpreter directly rather than through a shell, so an `env`-style
path is taken **literally** and does not resolve.

**Fix.** An absolute interpreter path in the inventory.

### 7.2 `Non-string value found for env option. Key: SERVER_PORT`

**Cause.** `community.docker` refuses a templated environment value that resolves to a number.

**Fix.** `| string` on each. The module is right to refuse: YAML would otherwise turn `8080` into an
integer and `true` into a boolean depending only on how the value happened to be written.

### 7.3 BUG-12 — `/actuator/info` returned the HTML login page with HTTP 200

**Symptom.** An authenticated `curl` to `/actuator/info` came back as a login page, status 200.

**Cause.** Actuator was matched by the **browser** filter chain, so it expected form login. A script,
a container probe and an Ansible `uri` task cannot follow a redirect to a login form or scrape a CSRF
token.

**Why it is worse than merely unusable.** It answered **200**, so a naive check would have called
that success.

**Fix.** Actuator moved onto the API chain: Basic, stateless, `/actuator/health` open for probes and
everything else ADMIN-only. This is the right split on its own terms — Actuator is machine-facing,
and the chain it belongs on is the one built for machines.

This was a real **application** defect found by writing the configuration management, not by the
application's own tests — a reminder that the operational surface needs the same scrutiny as the
functional one.

---

## 8. Decisions taken, and why

| Decision | Reasoning |
|---|---|
| Ansible rather than Puppet | Agentless and push-based; Puppet on Apple Silicon would have had to run inside a container, adding a moving part between the thing managing the deployment and the thing being deployed |
| No task contains a literal port, path or image name | Every value is read from `group_vars`, which makes an override a one-flag change rather than an edit |
| The package list is two entries long | The application ships as a container, so the node needs a runtime and a way to probe it. That shrinkage is the practical payoff of containerising |
| `prereqs` checks rather than installs | A run that silently installs a container runtime on a machine that did not have one is a surprise, not provisioning |
| The role checks that the daemon **responds**, not that the CLI exists | During this project the daemon stopped answering while containers kept running |
| Health checking is behavioural, not a ping | "The container is up" and "the application is correct" are different claims |

---

## 9. Evidence index

| File | Shows |
|---|---|
| [`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml) | The configuration specification — every prerequisite as data |
| [`ansible/inventory.ini`](../ansible/inventory.ini) | The target node, and the one-line change to a remote host |
| [`ansible/site.yml`](../ansible/site.yml) | The provisioning playbook |
| [`ansible/roles/`](../ansible/roles) | `prereqs`, `registry`, `app`, and the behavioural health check |
| [`evidence/task-13-ansible/first-run.log`](../evidence/task-13-ansible/first-run.log) | First execution: `ok=28 changed=1 failed=0` |

---

## 10. Task 13 Definition of Done

| # | Criterion | Status |
|---|---|---|
| D7 | No hard-coded port, path or image name in any role | All in `group_vars/all.yml` |
| D8 | Task documentation written and committed | This document |
| D9 | Evidence captured | `evidence/task-13-ansible/first-run.log` |
| D10 | The provisioned container answers its health check | Behavioural check, four assertions |
| D11 | Committed on `main` per the branching model | `task-13` |
| D12 | Backlog updated with BUG-12 | `docs/backlog.md` |

**Outcome:** every prerequisite the node needs is stated once as data, and three roles in a
dependency chain provision from it — asserting that the Docker daemon responds rather than that the
CLI exists, and verifying behaviour rather than liveness. Writing the health check surfaced a real
application defect that the application's own tests could not see.
