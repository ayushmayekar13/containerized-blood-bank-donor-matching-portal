#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# run-ui-tests.sh - build, deploy, and run the Selenium journeys against the
# deployed instance, the way the pipeline does it.
#
#   bash scripts/run-ui-tests.sh              build, deploy, run the journeys
#   bash scripts/run-ui-tests.sh --no-deploy  run against whatever is already up
#   bash scripts/run-ui-tests.sh --headed     watch the browser do it
#
# The journeys need a deployed application to drive, which is why they are in
# their own Maven profile and not in the default build: `mvn clean verify` has to
# stay green on a clean checkout with nothing running, or people stop trusting it.
# This script is the one command that sets up what they need.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

DEPLOY=true
HEADLESS=true
for arg in "$@"; do
  case "$arg" in
    --no-deploy) DEPLOY=false ;;
    --headed)    HEADLESS=false ;;
    *) fail "unknown option: $arg"; echo "usage: $0 [--no-deploy] [--headed]"; exit 2 ;;
  esac
done

cd "$PROJECT_ROOT"

if [ "$DEPLOY" = true ]; then
  say "Building and deploying before driving a browser at it"
  mvn -B -ntp clean package || { fail "build failed - not deploying"; exit 1; }
  bash scripts/deploy-tomcat.sh || { fail "deployment failed - not running the journeys"; exit 1; }
else
  say "Using the instance already running at ${APP_URL}"
  curl -fsS -o /dev/null "${HEALTH_URL}" || { fail "nothing healthy at ${HEALTH_URL}"; exit 1; }
fi

rm -rf target/screenshots

say "Running the Selenium journeys against ${APP_URL} (headless=${HEADLESS})"
if mvn -B -ntp -P ui-tests verify \
      -Dapp.base.url="${APP_URL}" \
      -Dselenium.headless="${HEADLESS}"; then
  ok "all journeys passed"
  exit 0
else
  fail "one or more journeys failed"
  if [ -d target/screenshots ] && [ -n "$(ls -A target/screenshots 2>/dev/null)" ]; then
    say "Failure evidence captured:"
    ls -1 target/screenshots | sed 's/^/    /'
  fi
  exit 1
fi
