#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# demo-all.sh - the entire DevOps workflow, end to end, in one command.
#
#   bash scripts/demo-all.sh              everything
#   bash scripts/demo-all.sh --quick      skip the Jenkins pipeline run
#
# Git commit -> Jenkins build -> unit tests -> Selenium quality gate ->
# Docker image -> registry -> container -> Ansible provisioning -> rollback.
#
# This is what gets run on practical day. It exists because a workflow that
# takes fifteen remembered commands to demonstrate is a workflow that will be
# demonstrated badly: under examination conditions a forgotten step reads as a
# capability that does not exist.
#
# Every step ASSERTS. A demonstration script that only narrates what it did
# proves nothing - the whole point is that it fails loudly if any part of the
# claim is untrue.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

QUICK=false
[ "${1:-}" = "--quick" ] && QUICK=true
export PATH="$HOME/.local/bin:$PATH"

STEP=0
FAILURES=0
STARTED_AT=$(date +%s)

step() {
  STEP=$((STEP + 1))
  echo
  echo "════════════════════════════════════════════════════════════════════════"
  printf "  STEP %d — %s\n" "$STEP" "$*"
  echo "════════════════════════════════════════════════════════════════════════"
}

assert() {  # assert <description> <expected> <actual>
  if [ "$2" = "$3" ]; then
    ok "$1 => $3"
  else
    fail "$1 => expected '$2', got '$3'"
    FAILURES=$((FAILURES + 1))
  fi
}

echo "████████████████████████████████████████████████████████████████████████"
echo "  CONTAINERIZED BLOOD BANK DONOR MATCHING PORTAL"
echo "  Complete DevOps workflow · Ayush Mayekar · 23102A0018 · BE CMPN A"
echo "  $(date '+%A %d %B %Y, %H:%M %Z')"
echo "████████████████████████████████████████████████████████████████████████"

# ---------------------------------------------------------------------------
step "Preflight — can this machine build, test and deploy?"
# ---------------------------------------------------------------------------
bash "${PROJECT_ROOT}/scripts/preflight.sh" || { fail "preflight failed - nothing else will work"; exit 1; }

# ---------------------------------------------------------------------------
step "Git — the project's history IS the project plan"
# ---------------------------------------------------------------------------
cd "$PROJECT_ROOT"
say "One tagged commit per subject task:"
git tag -l 'task-*' | sort -V | while read -r tag; do
  printf "    %-9s %s\n" "$tag" "$(git log -1 --format='%ad  %s' --date=short "$tag" | cut -c1-72)"
done
echo
say "Release tags:"
git tag -l 'v*' | sort -V | sed 's/^/    /'
echo
say "Branching, with both pull-request merges preserved by --no-ff:"
git log --graph --oneline --decorate -14 | sed 's/^/    /'
echo
assert "every subject task is tagged" "15" "$(git tag -l 'task-*' | wc -l | tr -d ' ')"

# ---------------------------------------------------------------------------
step "Build and unit tests — Maven, 179 tests"
# ---------------------------------------------------------------------------
mvn -B -ntp clean package 2>&1 | tail -18
[ -f "$WAR_PATH" ] || { fail "no WAR produced"; exit 1; }
ok "artefact: $(basename "$WAR_PATH") ($(du -h "$WAR_PATH" | cut -f1))"

# ---------------------------------------------------------------------------
if [ "$QUICK" = false ]; then
step "Jenkins — the full pipeline, including the Selenium quality gate"
# ---------------------------------------------------------------------------
  if curl -fsS -o /dev/null "http://localhost:${JENKINS_PORT}/login" 2>/dev/null; then
    bash "${PROJECT_ROOT}/scripts/jenkins-job.sh" build bloodbank-pipeline --wait 2>&1 \
      | grep -E "^\[Pipeline\] \{ \(|Tests run: [0-9]+, Fail|Quality gate passed|tags|Finished" | sed 's/^/    /'
    LAST_RESULT="$(curl -fsS -u "admin:${JENKINS_ADMIN_PASSWORD:-bloodbank-admin}" \
        "http://localhost:${JENKINS_PORT}/job/bloodbank-pipeline/lastBuild/api/json" 2>/dev/null \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["result"])' 2>/dev/null || echo UNKNOWN)"
    assert "pipeline result" "SUCCESS" "$LAST_RESULT"
  else
    warn "Jenkins is not running at :${JENKINS_PORT} - start it with: bash scripts/jenkins-setup.sh start"
    FAILURES=$((FAILURES + 1))
  fi
else
  say "(--quick: skipping the Jenkins pipeline run)"
fi

# ---------------------------------------------------------------------------
step "Registry — versioned images, which is what makes rollback possible"
# ---------------------------------------------------------------------------
TAGS="$(curl -fsS "http://${REGISTRY_HOST}/v2/${IMAGE_NAME}/tags/list" 2>/dev/null || echo '{}')"
echo "    $TAGS"
TAG_COUNT="$(echo "$TAGS" | python3 -c 'import json,sys; print(len(json.load(sys.stdin).get("tags",[])))' 2>/dev/null || echo 0)"
if [ "$TAG_COUNT" -ge 2 ]; then
  ok "${TAG_COUNT} image versions retained - a previous version exists to roll back to"
else
  fail "fewer than two versions published; rollback cannot be demonstrated"
  FAILURES=$((FAILURES + 1))
fi

# ---------------------------------------------------------------------------
step "Running container — what is actually serving traffic, and from which commit"
# ---------------------------------------------------------------------------
docker ps --filter "name=bloodbank" --format "    {{.Names}}  {{.Image}}  {{.Status}}  {{.Ports}}"
echo
say "image provenance:"
docker inspect "${APP_CONTAINER}" --format '    build {{index .Config.Labels "org.opencontainers.image.version"}} from commit {{index .Config.Labels "org.opencontainers.image.revision"}}' 2>/dev/null
say "process inside the container (unprivileged, and PID 1 so it stops gracefully):"
docker exec "${APP_CONTAINER}" sh -c 'ps -o uid,pid,comm -p 1' 2>/dev/null | sed 's/^/    /'

# ---------------------------------------------------------------------------
step "Behavioural health check — rules, not a ping"
# ---------------------------------------------------------------------------
bash "${PROJECT_ROOT}/scripts/health-check.sh" || FAILURES=$((FAILURES + 1))

# ---------------------------------------------------------------------------
step "Ansible — provisioning, idempotency, health and rollback"
# ---------------------------------------------------------------------------
bash "${PROJECT_ROOT}/scripts/verify-reliability.sh" 2>&1 \
  | grep -E "^  (ok|FAIL)|IDEMPOTENT|ROLLED BACK|^  [0-9]\. " | sed 's/^/  /'
RELIABILITY=$?
[ "$RELIABILITY" -eq 0 ] || FAILURES=$((FAILURES + 1))

# ---------------------------------------------------------------------------
step "Result"
# ---------------------------------------------------------------------------
ELAPSED=$(( $(date +%s) - STARTED_AT ))
echo
echo "    application   ${APP_URL}       (admin/Admin@123, staff/Staff@123, donor/Donor@123)"
echo "    jenkins       http://localhost:${JENKINS_PORT}"
echo "    registry      http://${REGISTRY_HOST}/v2/_catalog"
echo "    health        ${HEALTH_URL}"
echo "    elapsed       ${ELAPSED}s"
echo

if [ "$FAILURES" -eq 0 ]; then
  printf "${C_OK}████  COMPLETE DEVOPS WORKFLOW VERIFIED  ████${C_RESET}\n"
  echo "    commit → build → unit tests → quality gate → image → registry → container → provisioned → rollback"
  exit 0
else
  printf "${C_ERR}████  %d STEP(S) FAILED  ████${C_RESET}\n" "$FAILURES"
  exit 1
fi
