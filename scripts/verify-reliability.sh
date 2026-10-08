#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# verify-reliability.sh - run the whole Task 14 demonstration in one command.
#
#   bash scripts/verify-reliability.sh
#
# Proves, in order, the four properties the task asks for:
#
#   1. provisioning a clean environment    (container and local image destroyed)
#   2. idempotency                         (an immediate second run: changed=0)
#   3. a behavioural health check          (rules, not a ping)
#   4. rollback and recovery               (previous tag, data intact)
#
# Written as one command because that is how it will be demonstrated. A
# reliability claim that takes eight remembered commands to show is a claim
# that will not be shown.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

ANSIBLE_DIR="${PROJECT_ROOT}/ansible"
export PATH="$HOME/.local/bin:$PATH"

CURRENT_TAG="$(grep -E '^app_image_tag:' "${ANSIBLE_DIR}/group_vars/all.yml" | sed 's/.*"\(.*\)".*/\1/')"
ROLLBACK_TAG="${ROLLBACK_TAG:-}"

banner() {
  echo
  echo "########################################################################"
  echo "#  $*"
  echo "########################################################################"
}

donor_count() {
  curl -fsS -u "${DEMO_STAFF_USER}:${DEMO_STAFF_PASS}" "${APP_URL}/api/donors" 2>/dev/null \
    | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo "?"
}

running_image() {
  docker inspect "${APP_CONTAINER}" --format '{{.Config.Image}}' 2>/dev/null || echo "none"
}

# Pick a rollback target automatically: the newest published tag that is not
# the one currently deployed and is not 'latest'.
if [ -z "$ROLLBACK_TAG" ]; then
  ROLLBACK_TAG="$(curl -fsS "http://${REGISTRY_HOST}/v2/${IMAGE_NAME}/tags/list" 2>/dev/null \
    | python3 -c "
import json, sys
tags = [t for t in json.load(sys.stdin).get('tags', []) if t != 'latest' and t != '${CURRENT_TAG}']
numeric = sorted((int(t) for t in tags if t.isdigit()), reverse=True)
print(numeric[0] if numeric else '')
" 2>/dev/null || echo "")"
fi

if [ -z "$ROLLBACK_TAG" ]; then
  fail "no previous tag is published, so a rollback cannot be demonstrated"
  say "publish another version first:  bash scripts/release.sh <n>"
  exit 1
fi

say "current tag: ${CURRENT_TAG}   rollback target: ${ROLLBACK_TAG}"

# ---------------------------------------------------------------------------
banner "1 / 4  PROVISION A CLEAN ENVIRONMENT"
# ---------------------------------------------------------------------------
say "destroying the container and the local image - the registry and the data volume stay"
docker rm -f "${APP_CONTAINER}" >/dev/null 2>&1 || true
docker rmi -f "${REGISTRY_HOST}/${IMAGE_NAME}:${CURRENT_TAG}" >/dev/null 2>&1 || true

echo "  application containers: $(docker ps -aq -f "name=^${APP_CONTAINER}$" | wc -l | tr -d ' ')"
echo "  local images:           $(docker images -q "${REGISTRY_HOST}/${IMAGE_NAME}" | wc -l | tr -d ' ')"
echo "  registry still holds:   $(curl -fsS "http://${REGISTRY_HOST}/v2/${IMAGE_NAME}/tags/list")"
echo "  data volume:            $(docker volume ls -q -f "name=${APP_VOLUME}")"

( cd "$ANSIBLE_DIR" && ansible-playbook site.yml ) || { fail "clean provisioning failed"; exit 1; }
PROVISIONED_DONORS="$(donor_count)"
ok "provisioned from nothing: $(running_image), ${PROVISIONED_DONORS} donors (data survived)"

# ---------------------------------------------------------------------------
banner "2 / 4  IDEMPOTENCY - the same run again must change nothing"
# ---------------------------------------------------------------------------
SECOND_RUN="$( cd "$ANSIBLE_DIR" && ansible-playbook site.yml 2>&1 )"
echo "$SECOND_RUN" | tail -3

if echo "$SECOND_RUN" | grep -qE 'changed=0 '; then
  ok "IDEMPOTENT - the second run reported changed=0"
else
  fail "NOT idempotent - the second run reported a change. The task(s) responsible:"
  # Name the offending task rather than only the count. A failing idempotency
  # check that says "changed=1" and nothing else sends you back to run the
  # playbook by hand to find out which task it was; printing the task name
  # makes the failure diagnosable from the captured log alone.
  echo "$SECOND_RUN" | awk '
    /^TASK \[/ { task = $0 }
    /^changed:/ { print "    " task }
  '
  echo "$SECOND_RUN" | grep -E 'changed=' | tail -1
  exit 1
fi

# ---------------------------------------------------------------------------
banner "3 / 4  BEHAVIOURAL HEALTH CHECK"
# ---------------------------------------------------------------------------
bash "${PROJECT_ROOT}/scripts/health-check.sh" || { fail "health check failed"; exit 1; }

# ---------------------------------------------------------------------------
banner "4 / 4  ROLLBACK AND RECOVERY"
# ---------------------------------------------------------------------------
BEFORE_IMAGE="$(running_image)"
BEFORE_DONORS="$(donor_count)"
say "before: ${BEFORE_IMAGE} with ${BEFORE_DONORS} donors"

( cd "$ANSIBLE_DIR" && ansible-playbook rollback.yml -e "rollback_to=${ROLLBACK_TAG}" ) \
  || { fail "rollback failed"; exit 1; }

AFTER_IMAGE="$(running_image)"
AFTER_DONORS="$(donor_count)"
say "after:  ${AFTER_IMAGE} with ${AFTER_DONORS} donors"

if [ "$BEFORE_DONORS" = "$AFTER_DONORS" ] && [ "$BEFORE_IMAGE" != "$AFTER_IMAGE" ]; then
  ok "ROLLED BACK to a different image with the data intact"
else
  fail "rollback did not behave as expected (image ${BEFORE_IMAGE} -> ${AFTER_IMAGE}, donors ${BEFORE_DONORS} -> ${AFTER_DONORS})"
  exit 1
fi

say "rolling forward again to ${CURRENT_TAG}, so the environment is left as it was found"
( cd "$ANSIBLE_DIR" && ansible-playbook site.yml ) >/dev/null || { fail "roll-forward failed"; exit 1; }
ok "back on $(running_image) with $(donor_count) donors"

banner "ALL FOUR RELIABILITY PROPERTIES VERIFIED"
cat <<SUMMARY
  1. clean provisioning   container and local image destroyed, rebuilt from the registry
  2. idempotency          second identical run reported changed=0
  3. health check         authentication, compatibility matrix, role guard, dashboard
  4. rollback             ${CURRENT_TAG} -> ${ROLLBACK_TAG} -> ${CURRENT_TAG}, data intact throughout
SUMMARY
