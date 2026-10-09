# Runbook — operating the Blood Bank Donor Matching Portal

Ayush Mayekar · 23102A0018 · Every routine operation, with the command that performs it.

| Service | URL | Port |
|---|---|---|
| Application | http://localhost:9090 | 9090 → container 8080 |
| Jenkins | http://localhost:8090 | 8090 |
| Private registry | http://localhost:5001/v2/_catalog | 5001 |
| Health probe | http://localhost:9090/actuator/health | — |

Ports 8080 and 5000 are deliberately unused: 8080 is occupied on this host and macOS reserves 5000
for AirPlay Receiver. Every port is defined once in `scripts/env.sh`.

---

## Everyday

```bash
source scripts/env.sh                     # JAVA_HOME, ports, container and volume names
bash scripts/preflight.sh                 # can this machine build, test and deploy?
mvn clean verify                          # 179 unit tests
bash scripts/run-ui-tests.sh              # build, deploy, run the 16 Selenium journeys
bash scripts/health-check.sh              # behavioural assertions against a running instance
bash scripts/demo-all.sh                  # the entire workflow, end to end
```

## Running the application — three ways, one artefact

```bash
mvn spring-boot:run                       # fastest development loop
java -jar target/bloodbank.war            # the executable WAR
bash scripts/deploy-tomcat.sh             # that WAR inside a Tomcat container
bash scripts/release.sh 99                # a versioned image, via the registry
```

## Jenkins

```bash
bash scripts/jenkins-setup.sh start|stop|status|plugins
bash scripts/jenkins-setup.sh cli <any jenkins-cli command>

bash scripts/jenkins-job.sh create bloodbank-pipeline jenkins/pipeline-config.xml
bash scripts/jenkins-job.sh build  bloodbank-pipeline --wait
bash scripts/jenkins-job.sh log    bloodbank-pipeline [build]
bash scripts/jenkins-job.sh status bloodbank-pipeline
```

Rebuild the controller from nothing — this is the reproducibility claim, verified rather than
asserted:

```bash
rm -rf ~/.jenkins-bloodbank
bash scripts/jenkins-setup.sh start && bash scripts/jenkins-setup.sh plugins
bash scripts/jenkins-job.sh create bloodbank-ci       jenkins/ci-freestyle-config.xml
bash scripts/jenkins-job.sh create bloodbank-pipeline jenkins/pipeline-config.xml
```

## Docker

```bash
bash scripts/docker-lifecycle.sh all      # build→tag→run→inspect→stop→restart→replace, logged
docker ps --filter name=bloodbank --format 'table {{.Names}}\t{{.Image}}\t{{.Status}}'
docker logs --tail 50 bloodbank-app
docker exec bloodbank-app sh -c 'ps -o uid,pid,comm -p 1'     # expect: 999  1  java
docker inspect bloodbank-app --format '{{index .Config.Labels "org.opencontainers.image.revision"}}'
```

## Registry

```bash
curl -s http://localhost:5001/v2/_catalog
curl -s http://localhost:5001/v2/bloodbank/tags/list
bash scripts/release.sh <version>         # build, publish, deploy from the registry
```

## Ansible

```bash
cd ansible
ansible-playbook --syntax-check site.yml rollback.yml
ansible-playbook site.yml --check                  # dry run
ansible-playbook site.yml                          # provision
ansible-playbook site.yml                          # again → changed=0
ansible-playbook site.yml --tags health            # behavioural checks only
ansible-playbook site.yml -e app_image_tag=15      # deploy a specific version
ansible-playbook rollback.yml -e rollback_to=15    # roll back
```

---

## Incident procedures

### The application is down

```bash
curl -s http://localhost:9090/actuator/health
docker ps -a --filter name=bloodbank-app --format '{{.Names}} {{.Status}}'
docker logs --tail 100 bloodbank-app
```

| Finding | Action |
|---|---|
| Container is gone | `cd ansible && ansible-playbook site.yml` — provisions from the registry |
| Container restarting | Read the logs. Database permission errors mean the volume ownership is wrong; the entrypoint corrects it on restart |
| Healthy but behaving wrongly | `bash scripts/health-check.sh` names the failing rule |
| A recent release broke it | Roll back — see below |

### Roll back a bad release

```bash
curl -s http://localhost:5001/v2/bloodbank/tags/list          # what is available
docker inspect bloodbank-app --format '{{.Config.Image}}'     # what is running
cd ansible && ansible-playbook rollback.yml -e rollback_to=<previous>
```

Takes under a minute. The data volume is not touched, and the playbook verifies the target exists in
the registry **before** removing the running container.

### The pipeline is red

1. Which stage? `http://localhost:8090/job/bloodbank-pipeline/lastBuild/`
2. **Unit Test** — a business rule broke. Open the test report.
3. **UI Test** — a user journey broke. The screenshot, page source and URL are archived on the build.
4. **Promote** — the registry or the Docker daemon. `curl -s http://localhost:5001/v2/` and
   `docker info`.

Nothing is deployed while the pipeline is red; the running container is whatever last passed.

### Reset the demonstration data

```bash
docker rm -f bloodbank-app
docker volume rm bloodbank-data
bash scripts/release.sh 1
```

Reseeds 16 donors and 3 requests in about fifteen seconds. Deleting the volume is a recoverable
operation **by design** — `DataSeeder` repopulates any empty database, with donation dates relative
to today so the eligibility boundary is always visible.

### Docker stops responding

`docker ps` hangs while containers keep serving traffic. Restart Docker Desktop from the menu bar,
wait for the whale icon to settle, then:

```bash
bash scripts/preflight.sh
docker ps -a --filter name=bloodbank
```

Containers with `restart: unless-stopped` come back by themselves. This happened during development
and is recorded here because the symptom — a dead CLI next to a working application — is confusing
the first time.

---

## Routine maintenance

| Task | Command |
|---|---|
| Free disk used by old images | `docker image prune -a --filter 'until=168h'` (keeps the registry contents) |
| Check what the registry holds | `curl -s http://localhost:5001/v2/bloodbank/tags/list` |
| Trim Jenkins build history | Already automatic: 20 builds, 5 artefacts |
| Back up the data volume | `docker run --rm -v bloodbank-data:/d -v "$PWD":/b alpine tar czf /b/bloodbank-data.tgz -C /d .` |
| Restore it | `docker run --rm -v bloodbank-data:/d -v "$PWD":/b alpine tar xzf /b/bloodbank-data.tgz -C /d` |

## Upgrading to a new version

```bash
git commit ...                                      # the pipeline does the rest
# or by hand:
bash scripts/release.sh <version>
cd ansible && ansible-playbook site.yml -e app_image_tag=<version>
```

The previous image stays in the registry, which is what makes the upgrade reversible.
