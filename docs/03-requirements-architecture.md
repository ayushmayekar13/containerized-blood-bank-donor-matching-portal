# Task 3 — Requirements, Architecture and Technology Setup

**Project:** Containerized Blood Bank Donor Matching Portal · Ayush Mayekar · 23102A0018
**Date:** 30 September 2026

Deliverables required by the task sheet, and where each one lives:

| Required deliverable | Delivered in |
|---|---|
| SRS summary | [SRS.md](SRS.md) — 26 functional + 15 non-functional requirements, traceability matrix |
| Use-case diagram | [ARCHITECTURE.md §6](ARCHITECTURE.md) — 12 use cases, 4 actors, include/extend relations |
| Architecture diagram | [ARCHITECTURE.md §2 and §3](ARCHITECTURE.md) — layered view and deployment view |
| Data model / API list | [API.md](API.md) — ER model, enumerations, 20 UI endpoints, 17 REST endpoints, payloads, status-code contract |
| Working local setup | `scripts/env.sh`, `scripts/preflight.sh` and §3 of this document |

---

## 1. Minimum application architecture (MVP)

The MVP covers exactly what the task sheet requires, and nothing else:

| Required capability | How the architecture delivers it |
|---|---|
| **Create, view, update and search records** | `DonorService` and `RequestService` over Spring Data JPA repositories; search is a parameterised query combining blood group, city and availability with logical AND |
| **Role-based status workflow** | `RequestStatus.allowedNext()` defines the legal state machine; `RequestService` additionally checks the acting user's role before every transition, so the rule is enforced in one place for both the UI and the API |
| **Summary dashboard** | `DashboardService` aggregates donors per blood group, requests per status, and flags any group with open requests but zero matching donors |

The one piece of genuine domain logic — the **matching engine** — sits in `MatchingService` and is
built from three independent predicates (compatibility, eligibility, availability) plus a ranking
rule. Keeping it out of the controllers is what makes the 64-case compatibility matrix testable by
a plain JUnit test in milliseconds, with no browser and no servlet container
(see [ARCHITECTURE.md §5](ARCHITECTURE.md)).

## 2. Technology selections, as the task sheet asks

| Decision point in the task sheet | Selection | One-line justification |
|---|---|---|
| Programming stack | Java 21 + Spring Boot 3.3 + Thymeleaf | One artefact serves both UI and REST API; Actuator gives the health endpoint the pipeline and Ansible need |
| Build tool (Maven / Gradle / Ant) | **Maven 3.10** | Named in the task sheet; Surefire/Failsafe split keeps fast unit tests separate from slow Selenium tests |
| Database (if required) | **H2 2.x in file mode**, on a Docker named volume | Real SQL persistence with no external service, so the whole pipeline runs on one laptop and offline; the volume is what proves data survives container replacement in Task 12 |
| Deployment target (Tomcat / Nginx) | **Tomcat 10.1** (`tomcat:10.1-jdk21-temurin`) | Nginx cannot host a WAR; Tomcat 10.1 is the Jakarta EE 10 container that matches Spring Boot 3 |
| Packaging | **WAR** (`bloodbank.war`) | Archivable CI artefact *and* a Tomcat webapp, while remaining executable via `java -jar` for fast local runs — one build, three ways to run it |
| Local development setup | `scripts/env.sh` + `scripts/preflight.sh` | Every host-specific value (JDK path, ports, container and volume names) is defined in one file that all later scripts, the Jenkinsfile and the Ansible playbooks source |

The full comparison table, including the alternatives that were rejected and why, is in
[ARCHITECTURE.md §1](ARCHITECTURE.md).

## 3. Working local development setup

### 3.1 Prerequisites on this host

| Tool | Version verified | Install command used |
|---|---|---|
| JDK 21 | 21.0.12 (Homebrew) | `brew install openjdk@21` |
| Maven | 3.10.0 | `brew install maven` |
| Git | 2.46.0 | pre-installed |
| Docker Engine + Compose | 28.1.1 / v2.35.1 | Docker Desktop |
| Ansible | core 2.21.5 | `pipx install --include-deps ansible` |
| `community.docker` collection | installed | `ansible-galaxy collection install community.docker` |
| Jenkins LTS | 2.568.2 | `brew install jenkins-lts` |
| Google Chrome | installed | used headless by Selenium Manager |

### 3.2 Port map (fixed now, because it affects every later task)

| Service | Host port | Note |
|---|---|---|
| Jenkins | **8090** | Default 8080 is already occupied on this host by an unrelated service |
| Application container | **9090** → 8080 | Tomcat listens on 8080 inside the container |
| Private Docker registry | **5001** | macOS reserves 5000 for AirPlay Receiver |

### 3.3 One-time setup, then one command to verify

```bash
git clone <repository-url> devops_project && cd devops_project
source scripts/env.sh        # pins JAVA_HOME to JDK 21 and exports every port and name
bash scripts/preflight.sh    # verifies toolchain, daemon, ports, images and layout
```

`preflight.sh` is written to be run repeatedly: it is the first thing to run on a new machine and
the first thing to run before the practical demonstration. Verified output on this host:

```
==> 1. Toolchain
  ok   Java 21 — JAVA_HOME=/opt/homebrew/opt/openjdk@21
  ok   Maven — Apache Maven 3.10.0
  ok   Git — git version 2.46.0
  ok   Docker — Docker version 28.1.1
  ok   Ansible — ansible [core 2.21.5]
  ok   Docker daemon is running
  ok   Google Chrome present (Selenium Manager will resolve chromedriver)
  ok   Ansible collection community.docker installed
==> 2. Ports
  ok   port 8090 free
  ok   port 9090 free
  ok   port 5001 free
==> 3. Docker base images
  ok   image present: tomcat:10.1-jdk21-temurin
  ok   image present: registry:2
PREFLIGHT PASSED
```

(The project-layout section warns about `pom.xml`, `Dockerfile`, `Jenkinsfile` and
`ansible/site.yml` until the tasks that create them — 4, 11, 8 and 13 — are complete. That is
intentional: preflight doubles as a progress check.)

### 3.4 Development loop once the application exists (Task 4 onwards)

```bash
source scripts/env.sh
mvn clean verify                      # unit tests + Selenium journeys
mvn spring-boot:run                   # fast loop, embedded Tomcat on $APP_PORT
java -jar target/bloodbank.war        # same artefact, executable WAR
bash scripts/docker-lifecycle.sh      # same artefact, inside Tomcat in a container
```

That the *same* `bloodbank.war` can be run three ways is the practical proof of NFR-10
(portability) and is worth showing in the viva.

## 4. Why this architecture suits a DevOps subject

| Pipeline requirement (Tasks 7–14) | Architectural decision that makes it possible |
|---|---|
| Archivable build artefact (Task 7) | Single WAR, deterministic name `bloodbank.war` |
| Pipeline-as-code with a parameterised environment (Task 8) | All environment values are variables in `env.sh` / build parameters, never literals in code |
| Selenium quality gate (Tasks 9–10) | Server-rendered pages with stable `data-testid` attributes, so journeys do not break on styling changes |
| Container lifecycle (Task 11) | Stateless container + named volume for all state |
| Versioned image CD (Task 12) | Image tagged with the build number; the WAR is the only thing that changes between tags |
| Configuration management (Task 13) | Prerequisites are few and declarative: Docker, a volume, a network, an image tag, a port, a restart policy |
| Idempotency and rollback (Task 14) | Immutable image tags retained in the registry mean rollback is a re-deploy, never a rebuild |
| Health checks (Tasks 12–14) | One `/actuator/health` endpoint consumed by the container, Ansible, Jenkins and the viva demonstration |

## 5. Risks identified at design time

| Risk | Mitigation built into the design |
|---|---|
| H2 file lock if two containers mount the same volume | Deployment always removes the old container before starting the new one (recreate strategy, Task 12) |
| Selenium flakiness from fixed sleeps | Explicit `WebDriverWait` on element conditions only; no `Thread.sleep` anywhere in the suite |
| Tomcat context path confusion (`/bloodbank` vs `/`) | The WAR is deployed as `ROOT.war` inside the image, so every URL is `http://host:9090/...` in every environment |
| JDK version drift between host, Jenkins and image | JDK 21 pinned in `env.sh`, in the Jenkins tool configuration and in the image base tag |
| Losing the demonstration database | `data.sql` reseeds any empty database, so deleting the volume is a recoverable, 5-second operation |
