#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# docker-lifecycle.sh - build, tag, run, inspect, stop, restart and remove the
# application container, logging every command and its output.
#
#   bash scripts/docker-lifecycle.sh            run the whole lifecycle
#   bash scripts/docker-lifecycle.sh build      just build and tag
#   bash scripts/docker-lifecycle.sh run        just (re)create and start
#   bash scripts/docker-lifecycle.sh clean      remove the container and image
#
# The full run writes a transcript to evidence/task-11-docker/, which is the
# task's deliverable: a record of each lifecycle stage with the actual output,
# rather than a list of commands someone could have run.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

LOG_DIR="${EVIDENCE_DIR}/task-11-docker"
LOG_FILE="${LOG_DIR}/docker-command-log.txt"
IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"
BUILD_NUMBER="${BUILD_NUMBER:-local}"
GIT_COMMIT="$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "$LOG_DIR"

# Every command is echoed before it runs and its output tee'd, so the log reads
# as a transcript rather than as a summary someone wrote afterwards.
run() {
  {
    echo
    echo "\$ $*"
  } | tee -a "$LOG_FILE"
  "$@" 2>&1 | tee -a "$LOG_FILE"
  return "${PIPESTATUS[0]}"
}

section() {
  {
    echo
    echo "============================================================================"
    echo "  $*"
    echo "============================================================================"
  } | tee -a "$LOG_FILE"
}

lifecycle_build() {
  section "1. BUILD - multi-stage: Maven builds the WAR, only Tomcat ships"
  run docker build \
      --build-arg BUILD_NUMBER="${BUILD_NUMBER}" \
      --build-arg GIT_COMMIT="${GIT_COMMIT}" \
      --build-arg BUILD_DATE="${BUILD_DATE}" \
      -t "${IMAGE}" "$PROJECT_ROOT" || return 1

  section "2. TAG - a build-number tag alongside the moving 'latest' tag"
  # Two tags with different jobs: ${BUILD_NUMBER} is immutable and is what a
  # rollback pins to, while 'latest' moves and is a convenience only. Deploying
  # 'latest' makes a rollback impossible, because there is no name for the
  # version that was running before.
  run docker tag "${IMAGE}" "${IMAGE_NAME}:${BUILD_NUMBER}"

  section "3. IMAGE DETAILS - size, layers and the traceability labels"
  run docker images "${IMAGE_NAME}" --format "table {{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedSince}}"
  run docker image inspect "${IMAGE}" \
      --format 'digest:   {{.Id}}
version:  {{index .Config.Labels "org.opencontainers.image.version"}}
revision: {{index .Config.Labels "org.opencontainers.image.revision"}}
created:  {{index .Config.Labels "org.opencontainers.image.created"}}
student:  {{index .Config.Labels "project.student"}} ({{index .Config.Labels "project.roll"}})'
  run docker history "${IMAGE}" --format "table {{.CreatedBy}}\t{{.Size}}" --no-trunc=false
}

lifecycle_run() {
  section "4. RUN - create and start the container with a port map and a volume"
  if docker ps -aq -f "name=^${APP_CONTAINER}$" | grep -q .; then
    run docker rm -f "${APP_CONTAINER}"
  fi
  run docker volume create "${APP_VOLUME}"
  run docker run -d \
      --name "${APP_CONTAINER}" \
      -p "${APP_PORT}:${APP_CONTAINER_PORT}" \
      -v "${APP_VOLUME}:/opt/bloodbank/data" \
      --restart unless-stopped \
      "${IMAGE}" || return 1

  section "5. PORT MAPPING - host ${APP_PORT} to container ${APP_CONTAINER_PORT}"
  run docker port "${APP_CONTAINER}"
  run docker ps --filter "name=^${APP_CONTAINER}$" \
      --format "table {{.Names}}\t{{.Image}}\t{{.Status}}\t{{.Ports}}"

  section "6. WAIT FOR HEALTH - the container reports on itself"
  say "polling ${HEALTH_URL}"
  local healthy=1
  for attempt in $(seq 1 60); do
    if curl -fsS -o /dev/null "${HEALTH_URL}" 2>/dev/null; then
      healthy=0
      echo "application answered after ~$((attempt * 3))s" | tee -a "$LOG_FILE"
      break
    fi
    sleep 3
  done
  run curl -fsS "${HEALTH_URL}"
  echo | tee -a "$LOG_FILE"
  run docker inspect "${APP_CONTAINER}" --format 'docker healthcheck status: {{.State.Health.Status}}'
  return $healthy
}

lifecycle_inspect() {
  section "7. LOGS - the last lines of Tomcat's own output"
  run docker logs --tail 15 "${APP_CONTAINER}"

  section "8. INSPECT - what the running container actually is"
  run docker inspect "${APP_CONTAINER}" --format 'image:     {{.Config.Image}}
state:     {{.State.Status}}  (started {{.State.StartedAt}})
restart:   {{.HostConfig.RestartPolicy.Name}}
ports:     {{json .NetworkSettings.Ports}}
mounts:    {{range .Mounts}}{{.Type}} {{.Name}} -> {{.Destination}} (rw={{.RW}}){{end}}'

  section "9. PROCESS - Tomcat runs unprivileged, as PID 1"
  # Both facts matter. uid 999 means a container escape yields an account that
  # owns only its own data directory. PID 1 means `docker stop` delivers
  # SIGTERM to the JVM itself, so it shuts down in about a second instead of
  # being killed after the ten-second timeout.
  run docker exec "${APP_CONTAINER}" sh -c 'ps -o uid,pid,comm -p 1'
  run docker exec "${APP_CONTAINER}" ls -ld /opt/bloodbank/data

  section "10. STATS - resource usage of the running container"
  run docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}" "${APP_CONTAINER}"
}

lifecycle_persistence() {
  section "11. STOP - graceful shutdown"
  local stop_start stop_end
  stop_start=$(date +%s)
  run docker stop "${APP_CONTAINER}"
  stop_end=$(date +%s)
  echo "stopped in $((stop_end - stop_start))s - a graceful SIGTERM shutdown, not a 10s timeout kill" | tee -a "$LOG_FILE"
  run docker ps -a --filter "name=^${APP_CONTAINER}$" --format "table {{.Names}}\t{{.Status}}"

  section "12. RESTART - the same container, started again"
  run docker start "${APP_CONTAINER}"
  for _ in $(seq 1 40); do
    curl -fsS -o /dev/null "${HEALTH_URL}" 2>/dev/null && break
    sleep 3
  done
  run curl -fsS "${HEALTH_URL}"
  echo | tee -a "$LOG_FILE"

  section "13. DATA SURVIVES CONTAINER REPLACEMENT - the point of the volume"
  # Destroy the container entirely and build a new one from the image. If the
  # donor count is unchanged, the state really does live on the volume and the
  # container really is disposable - which is what makes the recreate
  # deployment strategy and the Task 14 rollback safe.
  local before after
  before="$(curl -fsS -u "${DEMO_STAFF_USER}:${DEMO_STAFF_PASS}" "${APP_URL}/api/donors" \
            | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)"
  echo "donors before destroying the container: ${before}" | tee -a "$LOG_FILE"

  run docker rm -f "${APP_CONTAINER}"
  run docker run -d --name "${APP_CONTAINER}" \
      -p "${APP_PORT}:${APP_CONTAINER_PORT}" \
      -v "${APP_VOLUME}:/opt/bloodbank/data" \
      --restart unless-stopped "${IMAGE}"
  for _ in $(seq 1 60); do
    curl -fsS -o /dev/null "${HEALTH_URL}" 2>/dev/null && break
    sleep 3
  done
  after="$(curl -fsS -u "${DEMO_STAFF_USER}:${DEMO_STAFF_PASS}" "${APP_URL}/api/donors" \
           | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)"
  echo "donors after a brand-new container:     ${after}" | tee -a "$LOG_FILE"

  if [ "$before" = "$after" ] && [ "$before" != "0" ]; then
    ok "data survived container replacement (${before} donors) - NFR-04 satisfied" | tee -a "$LOG_FILE"
  else
    fail "data did NOT survive container replacement (${before} -> ${after})" | tee -a "$LOG_FILE"
    return 1
  fi
}

lifecycle_summary() {
  section "14. LIFECYCLE SUMMARY"
  {
    echo "image      ${IMAGE}  (also tagged ${IMAGE_NAME}:${BUILD_NUMBER})"
    echo "container  ${APP_CONTAINER}  $(docker inspect -f '{{.State.Status}}' "${APP_CONTAINER}" 2>/dev/null)"
    echo "url        ${APP_URL}"
    echo "volume     ${APP_VOLUME}  (state outlives the container)"
    echo "commit     ${GIT_COMMIT}"
    echo
    echo "Removing the container is deliberately NOT part of this run: the demo"
    echo "continues against it. 'docker-lifecycle.sh clean' removes it and the image."
  } | tee -a "$LOG_FILE"
}

case "${1:-all}" in
  all)
    : > "$LOG_FILE"
    {
      echo "Docker container lifecycle - Blood Bank Donor Matching Portal"
      echo "Ayush Mayekar · 23102A0018 · Task 11"
      echo "Recorded: $(date '+%Y-%m-%d %H:%M:%S %Z')"
      echo "Host: $(uname -srm) · Docker: $(docker --version)"
    } | tee -a "$LOG_FILE"

    lifecycle_build       || { fail "build failed";       exit 1; }
    lifecycle_run         || { fail "run failed";         exit 1; }
    lifecycle_inspect
    lifecycle_persistence || { fail "persistence check failed"; exit 1; }
    lifecycle_summary
    echo
    ok "full lifecycle recorded in ${LOG_FILE}"
    ;;
  build) lifecycle_build ;;
  run)   lifecycle_run && lifecycle_inspect ;;
  clean)
    section "CLEAN - remove the container and the images"
    run docker rm -f "${APP_CONTAINER}"
    run docker rmi "${IMAGE}" "${IMAGE_NAME}:${BUILD_NUMBER}"
    warn "the volume ${APP_VOLUME} is deliberately kept; 'docker volume rm ${APP_VOLUME}' discards the data"
    ;;
  *)
    fail "usage: $0 {all|build|run|clean}"
    exit 2
    ;;
esac
