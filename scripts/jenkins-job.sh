#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# jenkins-job.sh - create or update this project's Jenkins jobs from the
# version-controlled XML in jenkins/, and run them.
#
#   bash scripts/jenkins-job.sh create <job-name> <config.xml>
#   bash scripts/jenkins-job.sh build  <job-name> [--wait]
#   bash scripts/jenkins-job.sh log    <job-name> [build-number]
#   bash scripts/jenkins-job.sh status <job-name>
#
# Jobs are defined as files, not as UI state. Changing CI means editing the XML
# in jenkins/ and re-running this script, which is what makes the configuration
# reviewable in a diff and restorable on a fresh controller.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

JENKINS_URL="http://localhost:${JENKINS_PORT}"
JENKINS_ADMIN_USER="${JENKINS_ADMIN_USER:-admin}"
JENKINS_ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-bloodbank-admin}"
AUTH="${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}"
JENKINS_CLI_JAR="${JENKINS_CLI_JAR:-/opt/homebrew/opt/jenkins-lts/libexec/cli-2.568.2.jar}"

cli() {
  "${JAVA_HOME}/bin/java" -jar "$JENKINS_CLI_JAR" -s "${JENKINS_URL}/" -auth "$AUTH" "$@"
}

case "${1:-}" in

  create)
    job="${2:?job name required}"; config="${3:?config xml required}"
    tmp="$(mktemp)"
    # The repository URL is substituted at creation time: the repository is local
    # for now, and becomes a GitHub URL after the first push, with no edit to the
    # committed XML.
    sed "s|@@REPO_URL@@|${REPO_URL:-$PROJECT_ROOT}|g" "$config" > "$tmp"

    if cli get-job "$job" > /dev/null 2>&1; then
      say "Updating existing job '${job}'"
      cli update-job "$job" < "$tmp" && ok "job '${job}' updated"
    else
      say "Creating job '${job}'"
      cli create-job "$job" < "$tmp" && ok "job '${job}' created"
    fi
    rm -f "$tmp"
    ;;

  build)
    job="${2:?job name required}"
    say "Triggering build of '${job}'"
    if [ "${3:-}" = "--wait" ]; then
      # -s follows the build to completion and returns its exit status, -v
      # streams the console log, which is what gets captured as evidence.
      cli build "$job" -s -v
    else
      cli build "$job"
      ok "queued"
    fi
    ;;

  log)
    job="${2:?job name required}"; build="${3:-lastBuild}"
    curl -fsS -u "$AUTH" "${JENKINS_URL}/job/${job}/${build}/consoleText"
    ;;

  status)
    job="${2:?job name required}"
    curl -fsS -u "$AUTH" "${JENKINS_URL}/job/${job}/lastBuild/api/json" \
      | python3 -c '
import json, sys
b = json.load(sys.stdin)
print("build      #%s" % b["number"])
print("result     %s" % b["result"])
print("duration   %.1fs" % (b["duration"] / 1000))
print("url        %s" % b["url"])
for a in b.get("artifacts", []):
    print("artefact   %s" % a["fileName"])
'
    ;;

  artifacts)
    job="${2:?job name required}"; build="${3:-lastBuild}"
    curl -fsS -u "$AUTH" "${JENKINS_URL}/job/${job}/${build}/api/json" \
      | python3 -c 'import json,sys; [print(a["relativePath"]) for a in json.load(sys.stdin).get("artifacts",[])]'
    ;;

  *)
    fail "usage: $0 {create|build|log|status|artifacts} ..."
    exit 2
    ;;
esac
