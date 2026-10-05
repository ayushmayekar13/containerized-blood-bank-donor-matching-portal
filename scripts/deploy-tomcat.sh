#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# deploy-tomcat.sh - deploy target/bloodbank.war into a Tomcat container.
#
#   bash scripts/deploy-tomcat.sh [war-path]
#
# Environment (all defaulted in scripts/env.sh, all overridable, which is what
# makes the pipeline's DEPLOY_PORT parameter work without a second code path):
#   APP_PORT            host port to publish          (default 9090)
#   APP_CONTAINER       container name                (default bloodbank-app)
#   APP_VOLUME          named volume for the database (default bloodbank-data)
#   TOMCAT_IMAGE        base image                    (default tomcat:10.1-jdk21-temurin)
#   SPRING_PROFILE      Spring profile to activate    (default empty)
#
# The WAR is mounted into Tomcat's webapps directory as ROOT.war, so the context
# path is "/" in every environment and no URL in the Selenium suite, the health
# check or the documentation has to change between local, container and CI.
#
# Task 11 replaces this volume mount with a proper image that bakes the WAR in.
# This script stays, because it is the fastest way to put a freshly built WAR in
# front of a browser without a rebuild - and the difference between the two is
# itself worth demonstrating.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

WAR="${1:-$WAR_PATH}"
TOMCAT_IMAGE="${TOMCAT_IMAGE:-tomcat:10.1-jdk21-temurin}"
SPRING_PROFILE="${SPRING_PROFILE:-}"

if [ ! -f "$WAR" ]; then
  fail "no WAR at ${WAR} - run 'mvn clean package' first"
  exit 1
fi

# Copy the WAR out of target/ before mounting it.
#
# Bind-mounting straight out of target/ ties the build directory to the
# container's lifetime: Docker holds the file open, so the next `mvn clean`
# fails with "Could not copy webapp classes". Staging it in .deploy/ decouples
# the two - the build directory is the build's to delete, and the deployed
# artefact is a snapshot that stays put until the next deployment replaces it.
STAGE_DIR="${PROJECT_ROOT}/.deploy"
mkdir -p "$STAGE_DIR"
STAGED_WAR="${STAGE_DIR}/ROOT.war"
cp "$WAR" "$STAGED_WAR"

say "Deploying $(basename "$WAR") ($(du -h "$WAR" | cut -f1)) to Tomcat"
say "  image      ${TOMCAT_IMAGE}"
say "  container  ${APP_CONTAINER}"
say "  port       ${APP_PORT} -> ${APP_CONTAINER_PORT}"
say "  volume     ${APP_VOLUME} -> /opt/bloodbank/data"

# Recreate rather than restart: the deployment strategy for this project is
# recreate-with-rollback, and an immutable replacement is what makes the rollback
# in Task 14 a re-deploy rather than a rebuild. The named volume is what makes
# that safe - the database outlives the container.
if docker ps -aq -f "name=^${APP_CONTAINER}$" | grep -q .; then
  say "Removing the existing container"
  docker rm -f "${APP_CONTAINER}" > /dev/null
fi

docker volume create "${APP_VOLUME}" > /dev/null

docker run -d \
  --name "${APP_CONTAINER}" \
  -p "${APP_PORT}:${APP_CONTAINER_PORT}" \
  -v "${APP_VOLUME}:/opt/bloodbank/data" \
  -v "${STAGED_WAR}":/usr/local/tomcat/webapps/ROOT.war:ro \
  -e BLOODBANK_DB_PATH=/opt/bloodbank/data/bloodbank \
  -e SERVER_PORT="${APP_CONTAINER_PORT}" \
  ${SPRING_PROFILE:+-e SPRING_PROFILES_ACTIVE=$SPRING_PROFILE} \
  --restart unless-stopped \
  "${TOMCAT_IMAGE}" > /dev/null

ok "container started: $(docker ps --format '{{.Names}} {{.Status}} {{.Ports}}' -f "name=^${APP_CONTAINER}$")"

say "Waiting for the application to answer on ${HEALTH_URL}"
for attempt in $(seq 1 60); do
  if curl -fsS -o /dev/null "${HEALTH_URL}" 2>/dev/null; then
    ok "healthy after ~$((attempt * 2))s: $(curl -fsS "${HEALTH_URL}")"
    exit 0
  fi
  sleep 2
done

fail "the application did not become healthy within 120s"
say "Last 40 lines of container log:"
docker logs --tail 40 "${APP_CONTAINER}"
exit 1
