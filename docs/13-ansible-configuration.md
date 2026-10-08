# Task 13 — Configuration Management Script

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 8 October 2026 · **Tool:** Ansible (core 2.21.5) with `community.docker`

| Required deliverable | Delivered as |
|---|---|
| Configuration specification | [`ansible/group_vars/all.yml`](../ansible/group_vars/all.yml) — every prerequisite as data, in one place |
| Ansible inventory and playbook | [`ansible/inventory.ini`](../ansible/inventory.ini), [`ansible/site.yml`](../ansible/site.yml), three roles under [`ansible/roles/`](../ansible/roles) |
| First execution log | [`evidence/task-13-ansible/first-run.log`](../evidence/task-13-ansible/first-run.log) — `ok=28 changed=1 failed=0` |

---

## 1. Why Ansible rather than Puppet

Both were allowed. Ansible was chosen for three reasons that matter to *this* project:

| | Ansible | Puppet |
|---|---|---|
| Agent on the target | None — talks to the Docker API directly | Needs an agent installed and running |
| Idempotency | A property of each module, so it falls out of writing ordinary tasks | Also idempotent, via a different (resource/catalog) model |
| On Apple Silicon macOS | `pipx install ansible` and it works | Open-source Puppet on macOS/ARM is awkward and would have to run inside a Linux container — a moving part between the thing managing the deployment and the thing being deployed |

## 2. The configuration specification

The task asks for "all server prerequisites: packages, users, folders, files, ports and services".
`group_vars/all.yml` states each of them **once, as data**. No task in any role contains a literal
port, path or image name — they all read these variables, which is what makes an override a
one-flag change:

```bash
ansible-playbook site.yml -e app_image_tag=14 -e app_host_port=9095
```

| Category | Specified | Note |
|---|---|---|
| **Packages** | `docker`, `curl` | Deliberately short. The application ships as a container, so the node needs a runtime and a way to probe it — **not** a JDK, Maven or Tomcat. That shrinkage is the practical payoff of containerising |
| **Users** | uid/gid `999` | Pinned, because the container entrypoint compares the data volume's ownership against it (BUG-11) |
| **Folders** | volume `bloodbank-data` → `/opt/bloodbank/data` | The database must outlive every container |
| **Files** | the image itself, by immutable tag | The artefact is the image; there is no file to copy and no config to template on the node |
| **Ports** | app `9090→8080`, registry `5001→5000` | Neither default is used: 8080 is occupied on this host and macOS reserves 5000 for AirPlay |
| **Services** | app + registry containers, `restart: unless-stopped` | NFR-03 — survive a crash and a reboot |

## 3. Inventory, and the one-line change to a real server

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
and the group variables. That property is the reason for writing it this way rather than
hard-coding connection details into the plays — and it is the honest answer to "this is just running
on your laptop": the playbook does not know or care.

## 4. The three roles, and why in that order

```
prereqs  →  registry  →  app
```

The ordering is a **dependency chain, not a preference**. The application's image is identified by a
registry URL, so a node whose registry is not running cannot deploy at all.

| Role | Does |
|---|---|
| **prereqs** | Asserts every required command exists; asserts the Docker **daemon responds**, not merely that the CLI is installed; reports what the node is; ensures both data volumes exist |
| **registry** | Ensures the registry container is running, waits for its API, reports its catalogue |
| **app** | Records what is deployed now, pulls the pinned image, ensures the container matches the declared state, waits for health, asserts the running image is the one requested |

### `prereqs` checks rather than installs — on purpose

Installing Docker from a playbook on macOS would mean driving a GUI installer, which cannot be done
honestly. The role fails with an actionable message instead of pretending:

```
'docker' is not available on bloodbank-node-01. Install it before provisioning:
on macOS use Docker Desktop or 'brew install docker'; on Debian/Ubuntu use
'apt-get install -y docker'.
```

That is also the right behaviour in general: a provisioning run that silently installs a container
runtime on a machine that did not have one is not provisioning, it is a surprise. On a Linux fleet
these `assert` tasks become `package:` tasks and **nothing else in the playbook changes**.

Checking the daemon separately from the binary is not pedantry — during this project the daemon
stopped responding while containers kept running, and "docker is installed" would have been true and
useless.

## 5. Health checking is behavioural

`roles/app/tasks/health.yml` asserts the same rules `scripts/health-check.sh` does, expressed as
Ansible tasks so a run is self-contained:

| Check | Requirement |
|---|---|
| An unauthenticated API call is rejected | FR-24 |
| A B+ shortlist contains no incompatible donor | FR-14 |
| A STAFF user cannot fulfil a request | NFR-07 |
| The dashboard reports all eight blood groups | FR-22 |

```
http://localhost:9090 is healthy AND behaving correctly: authentication enforced,
18 compatible donors shortlisted for B+, role guard returns 403, dashboard reports
8 blood groups, 39 donors registered.
```

"The container is up" and "the application is correct" are different claims, and only the second one
is worth provisioning for.

## 6. Three problems this task surfaced

| Problem | Cause | Fix |
|---|---|---|
| `The module interpreter '/usr/bin/env python3' was not found` | Ansible `exec()`s the interpreter directly rather than through a shell, so an `env`-style path is taken **literally** | An absolute interpreter path in the inventory |
| `Non-string value found for env option. Key: SERVER_PORT` | `community.docker` refuses a templated environment value that resolves to a number | `\| string` on each. The module is right to refuse: YAML would otherwise turn `8080` into an integer and `true` into a boolean depending only on how the value was written |
| **An authenticated `curl` to `/actuator/info` returned the HTML login page with HTTP 200** | Actuator was matched by the **browser** filter chain, so it expected form login — but a script, a container probe and an Ansible `uri` task cannot follow a redirect to a login form or scrape a CSRF token | Actuator moved onto the API chain: Basic, stateless, `/actuator/health` open for probes and everything else ADMIN-only. Worse than being unusable, it answered **200**, so a naive check would have called that success |

The third was a real application defect found by writing the configuration management, not by the
application's own tests — a reminder that the operational surface needs the same scrutiny as the
functional one.

## 7. Verification

```bash
cd ansible
ansible-playbook --syntax-check site.yml rollback.yml
ansible-playbook site.yml --check          # dry run, changes nothing
ansible-playbook site.yml                  # provision
ansible-playbook site.yml                  # again: changed=0  (Task 14)
ansible-playbook site.yml --tags health    # just the behavioural checks
ansible-playbook site.yml -e app_image_tag=15   # deploy a different version
```
