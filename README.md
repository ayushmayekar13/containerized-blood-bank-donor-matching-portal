# Containerized Blood Bank Donor Matching Portal

> **DevOps Project** · Department of Computer Engineering · Academic Year 2026-27, ODD Semester
> **Student:** Ayush Mayekar · **Roll No.** 23102A0018 · **Class:** BE Computer Engineering, Division A

A web portal that maintains a searchable blood-donor registry, records blood requests, and
automatically ranks the donors who are **compatible, eligible and available** for each request,
under a role-based request workflow — built, tested, containerized and deployed through a complete
automated DevOps pipeline.

| | |
|---|---|
| **Repository** | `<add the GitHub URL here after the first push>` |
| **Application** | http://localhost:9090 |
| **Health endpoint** | http://localhost:9090/actuator/health |
| **Jenkins** | http://localhost:8090 |
| **Private registry** | http://localhost:5001/v2/_catalog |
| **Current release** | `v1.3.0` |
| **One-command demonstration** | `bash scripts/demo-all.sh` |
| **Practical-day guide** | [`docs/VIVA_GUIDE.md`](docs/VIVA_GUIDE.md) |
| **Submission report** | [`DevOps_Project_Report_23102A0018.docx`](DevOps_Project_Report_23102A0018.docx) — 45 pages, all 15 tasks |
| **Per-task reports** | [`task-reports/`](task-reports) — one standalone document per task |

---

## Why this project exists

Blood cannot be manufactured, a donor is ineligible for 90 days after donating, and red-cell
compatibility is far wider than "same group only" — yet most blood banks still match requests to
donors by phone call and paper register. The result is that **time-to-match is measured in hours**
and compatible donors in the same city go uncontacted. This portal makes the match a query instead
of a phone tree, and makes every release of it reproducible.

Full problem analysis: [`docs/01-problem-definition.md`](docs/01-problem-definition.md).

## What it does

| Capability | Detail |
|---|---|
| Donor registry | Create, view, update and **search** donors by blood group, city and availability |
| Blood requests | Record patient, blood group, units, hospital, city and urgency; search by status and group |
| **Matching engine** | Applies the 8 × 8 red-cell compatibility matrix, the 90-day eligibility rule and the availability flag, then ranks: exact group → same city → longest since last donation |
| **Role-based workflow** | `NEW → MATCHED → FULFILLED / REJECTED`; STAFF may match, only ADMIN may fulfil or reject; illegal transitions are refused with HTTP 409, wrong roles with 403 |
| Summary dashboard | Donors per blood group, requests per status, and a shortage flag for any group with open requests but zero matching donors |
| REST API | `/api/**` mirrors every screen; `/actuator/health` is open for automated probes |

## Technology stack

| Layer | Choice |
|---|---|
| Language / framework | Java 21 · Spring Boot 3.3 · Spring MVC + Thymeleaf · Spring Data JPA · Spring Security |
| Build | **Maven 3.10** → single artefact `target/bloodbank.war` |
| Database | H2 2.x in file mode, on the Docker named volume `bloodbank-data` |
| Deployment target | **Tomcat 10.1** (`tomcat:10.1-jdk21-temurin`), deployed as `ROOT.war` |
| Containerisation | Docker (multi-stage build) · private registry `registry:2` on port 5001 |
| CI/CD | **Jenkins LTS** — freestyle CI job + declarative `Jenkinsfile` pipeline |
| Testing | JUnit 5 (Surefire) · **Selenium 4** WebDriver, headless Chrome (Failsafe) |
| Configuration management | **Ansible** with the `community.docker` collection |

The reasoning behind each choice — and the alternatives that were rejected — is in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) §1.

## Quick start

```bash
# 0. verify the machine can build, test and deploy (run this first, always)
source scripts/env.sh
bash scripts/preflight.sh

# 1. build and run every test
mvn clean verify

# 2. run it, three ways — the same artefact, no rebuild
mvn spring-boot:run                      # fast development loop
java -jar target/bloodbank.war           # executable WAR
bash scripts/docker-lifecycle.sh         # Tomcat inside a container on :9090

# 3. check it
curl -s http://localhost:9090/actuator/health      # {"status":"UP"}
bash scripts/health-check.sh                       # full API assertions

# 4. the whole pipeline, end to end, in one command
bash scripts/demo-all.sh
```

### Demonstration accounts

| Username | Password | Role | Can do |
|---|---|---|---|
| `admin` | `Admin@123` | `ADMIN` | Everything, including fulfil and reject |
| `staff` | `Staff@123` | `STAFF` | Donor and request management, assign a matched donor |
| `donor` | `Donor@123` | `DONOR` | Own profile and availability only |

Local demonstration credentials only; they come from configuration, are BCrypt-hashed at startup,
and are not valid against anything outside this local deployment.

## The 15 tasks and where to find each one

| # | Task | Document | Evidence |
|---|---|---|---|
| 1 | Problem definition and scope | [01](docs/01-problem-definition.md) | — |
| 2 | Agile planning and DevOps workflow | [02](docs/02-agile-planning.md) · [backlog](docs/backlog.md) · [board](docs/kanban.md) · [DoD](docs/definition-of-done.md) | — |
| 3 | Requirements, architecture, technology setup | [03](docs/03-requirements-architecture.md) · [SRS](docs/SRS.md) · [architecture](docs/ARCHITECTURE.md) · [API](docs/API.md) | `scripts/preflight.sh` |
| 4 | Git repository initialization | [04](docs/04-repository-setup.md) · [CONTRIBUTING](CONTRIBUTING.md) | `git log`, `.github/` templates |
| 5 | Feature development with branching | [05](docs/05-feature-branching.md) · [PR-01](docs/pr/PR-01-donor-registry.md) | `git log --graph` |
| 6 | MVP completion and Git collaboration | [06](docs/06-mvp-and-conflict.md) · [PR-02](docs/pr/PR-02-request-workflow.md) | conflict resolution commit, tag `v1.0.0` |
| 7 | Jenkins installation and CI job | [07](docs/07-jenkins-ci.md) · [Jenkins setup](jenkins/SETUP.md) | `evidence/task-07-jenkins/` |
| 8 | Pipeline as code and server deployment | [08](docs/08-pipeline-as-code.md) · [`Jenkinsfile`](Jenkinsfile) | `evidence/task-08-pipeline/` |
| 9 | Selenium test design and local execution | [09](docs/09-selenium-tests.md) | `evidence/task-09-selenium/` |
| 10 | Continuous testing in Jenkins | [10](docs/10-continuous-testing.md) | `evidence/task-10-quality-gate/` |
| 11 | Docker image and container lifecycle | [11](docs/11-docker-lifecycle.md) · [`Dockerfile`](Dockerfile) | `evidence/task-11-docker/` |
| 12 | Jenkins-Docker continuous deployment | [12](docs/12-jenkins-docker-cd.md) | `evidence/task-12-registry/` |
| 13 | Configuration management script | [13](docs/13-ansible-configuration.md) · [`ansible/`](ansible/) | `evidence/task-13-ansible/` |
| 14 | Automated provisioning and reliability | [14](docs/14-provisioning-rollback.md) | `evidence/task-14-reliability/` |
| 15 | Final end-to-end release and viva | [15](docs/15-final-release.md) · **[VIVA GUIDE](docs/VIVA_GUIDE.md)** · [report](docs/REPORT.md) · [runbook](docs/RUNBOOK.md) · [slides](docs/PRESENTATION.md) | `scripts/demo-all.sh` |

## What it actually proves

The result worth pointing at is **pipeline build #9**. Checkout, Build, **Unit Test (179 green)**,
Package, Deploy and the behavioural Health Check all passed — and the build still went red, because a
browser found the request page offering an ADMIN-only action to a STAFF user. The `Promote` stage was
never entered: no image was published and no container was replaced.

That is the difference between *having tests* and *having a gate*. Evidence:
[`evidence/task-10-quality-gate/`](evidence/task-10-quality-gate).

| | |
|---|---|
| Tests | 179 unit + 16 Selenium journeys |
| Commit → running, health-checked container | **~101 s** |
| Rollback to any previous release | **< 1 min**, data intact |
| Ansible second run | **`changed=0`** |
| Defects found and fixed | 11, each with its cause recorded in [`docs/TROUBLESHOOTING.md`](docs/TROUBLESHOOTING.md) |

## Repository layout

```
.
├── pom.xml                  Maven build → target/bloodbank.war
├── Dockerfile               multi-stage: Maven build → Tomcat 10.1 runtime
├── docker-compose.yml       application + private registry
├── Jenkinsfile              declarative CI/CD pipeline (pipeline as code)
├── CONTRIBUTING.md          branch, commit, pull-request and tagging policy
├── .github/                 issue templates and the pull-request template
├── src/main/java/com/bloodbank/
│   ├── config/              security, data seeding
│   ├── domain/              Donor, BloodRequest, BloodGroup, RequestStatus, Urgency
│   ├── repo/                Spring Data JPA repositories
│   ├── service/             DonorService, MatchingService, RequestService, DashboardService
│   └── web/                 controllers + web/api (JSON)
├── src/main/resources/      application.yml, Thymeleaf templates, static assets, data.sql
├── src/test/java/           JUnit 5 unit tests  (Surefire — fast)
├── src/it/java/             Selenium journeys    (Failsafe — quality gate)
├── jenkins/                 job configuration, plugin list, setup guide
├── ansible/                 inventory, site.yml, rollback.yml, roles
├── docker/entrypoint.sh     drops privileges after fixing volume ownership
├── scripts/                 env.sh · preflight.sh · build and deploy · docker-lifecycle.sh
│                            health-check.sh · run-ui-tests.sh · release.sh
│                            verify-reliability.sh · demo-all.sh · jenkins-*.sh
├── docs/                    all 15 task documents, SRS, architecture, API, runbook, viva guide
└── evidence/                real build logs, test reports, command logs and screenshots
```

## Branching model

```
feature/<slug> ──commits──► pull request + review ──merge --no-ff──► develop ──merge──► main ──tag──► vX.Y.Z
```

`main` is always releasable · `develop` integrates features · one short-lived `feature/*` branch per
user story, merged with `--no-ff` so the feature boundary is preserved in history. Naming rules,
commit conventions and the review checklist are in [`CONTRIBUTING.md`](CONTRIBUTING.md).

| Tag | Marks |
|---|---|
| `task-01` … `task-15` | The exact state at which each subject task was completed |
| `v1.0.0` | Functional MVP (Task 6) |
| `v1.1.0` | Containerized continuous deployment (Task 12) |
| `v1.2.0` | Automated provisioning with rollback (Task 14) |
| `v1.3.0` | Final documented release (Task 15) |

`feature/export-report` is intentionally left **unmerged** so that an open branch and a live pull
request can be demonstrated.

## Ports used

| Service | Port | Why not the default |
|---|---|---|
| Application (host → container) | **9090** → 8080 | Host 8080 is already occupied by an unrelated service |
| Jenkins | **8090** | Same reason |
| Private Docker registry | **5001** | macOS reserves port 5000 for AirPlay Receiver |

## Pushing this repository to GitHub

The repository is currently local only. To publish it:

```bash
gh repo create devops-bloodbank-portal --private --source=. --remote=origin   # or create it in the web UI
git remote add origin https://github.com/<your-username>/devops-bloodbank-portal.git
git push -u origin main
git push origin develop
git push origin feature/export-report     # kept open so a live pull request can be opened
git push origin --tags
```

Then update the **Repository** row at the top of this file with the URL.

## Known limitations

1. Only the 90-day donation interval is modelled as eligibility; real medical deferral criteria are
   much wider.
2. Donor personal data is stored unencrypted on a local volume; encryption at rest and an access
   audit log are prerequisites for any real deployment.
3. H2 is single-node — PostgreSQL would be the first change for a multi-user deployment.
4. "Same city" is a string comparison, not a distance calculation.
5. Ansible manages a Docker node on this machine; switching to a remote host over SSH is a
   one-line inventory change, documented in [`docs/13-ansible-configuration.md`](docs/13-ansible-configuration.md).

Planned enhancements are tracked in [`docs/backlog.md`](docs/backlog.md).

## Licence

Submitted as academic coursework. Not licensed for production clinical use.
