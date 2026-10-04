#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# health-check.sh - assert that a deployed instance is not merely answering,
# but behaving correctly.
#
#   bash scripts/health-check.sh [base-url]
#
# The same script is used in four places, on purpose: by the Jenkins pipeline
# after deployment, by the Ansible health check in Task 14, by scripts/demo-all.sh,
# and by hand during the viva. One definition of "healthy" means those four can
# never disagree.
#
# "Answering" is not "healthy". A Tomcat that returns 200 on /actuator/health
# while the matching engine is broken has failed in the only way that matters, so
# these checks exercise the real business rules through the API.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

BASE="${1:-$APP_URL}"
STAFF="${DEMO_STAFF_USER}:${DEMO_STAFF_PASS}"
ADMIN="${DEMO_ADMIN_USER}:${DEMO_ADMIN_PASS}"
FAILURES=0

check() {  # check <description> <expected> <actual>
  local what="$1" expected="$2" actual="$3"
  if [ "$expected" = "$actual" ]; then
    ok "${what} => ${actual}"
  else
    fail "${what} => expected '${expected}', got '${actual}'"
    FAILURES=$((FAILURES + 1))
  fi
}

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }
json() { curl -s "$@"; }

say "Health checking ${BASE}"

# ---- 1. liveness: the probe the container and Ansible use -------------------
status="$(json "${BASE}/actuator/health" | python3 -c 'import json,sys; print(json.load(sys.stdin)["status"])' 2>/dev/null || echo UNREACHABLE)"
check "actuator health status" "UP" "$status"
if [ "$status" != "UP" ]; then
  fail "the application is not up; skipping the behavioural checks"
  exit 1
fi

# ---- 2. authentication is enforced (FR-24) ---------------------------------
check "unauthenticated API call is rejected" "401" "$(code "${BASE}/api/donors")"
check "unauthenticated portal page redirects" "302" "$(code "${BASE}/dashboard")"
check "login page is reachable"              "200" "$(code "${BASE}/login")"

# ---- 3. the data is actually there -----------------------------------------
donors="$(json -u "$STAFF" "${BASE}/api/donors" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)))' 2>/dev/null || echo 0)"
if [ "$donors" -ge 1 ]; then ok "donor registry reachable => ${donors} donors"
else fail "donor registry empty or unreachable"; FAILURES=$((FAILURES + 1)); fi

# ---- 4. the matching engine gives the right answer (FR-14) -----------------
# A B+ patient must draw only B+, B-, O+ and O- donors. If this is wrong, the
# application is broken in the one way that would actually matter clinically,
# however healthy the servlet container looks.
bad="$(json -u "$STAFF" "${BASE}/api/donors/eligible?group=B%2B" \
       | python3 -c '
import json, sys
allowed = {"B+", "B-", "O+", "O-"}
print(len([m for m in json.load(sys.stdin) if m["bloodGroup"] not in allowed]))
' 2>/dev/null || echo ERR)"
check "no incompatible donor in a B+ shortlist" "0" "$bad"

# ---- 5. the role guard is enforced server-side (NFR-07) --------------------
# The single most important assertion here: STAFF must not be able to fulfil.
check "STAFF fulfilling a request is refused" "403" \
      "$(code -X POST -u "$STAFF" "${BASE}/api/requests/1/fulfil")"

# ---- 6. the state machine is enforced (FR-18) ------------------------------
# ADMIN is permitted, but a NEW request cannot jump straight to FULFILLED.
new_id="$(json -u "$STAFF" "${BASE}/api/requests?status=NEW" \
          | python3 -c 'import json,sys; d=json.load(sys.stdin); print(d[0]["id"] if d else "")' 2>/dev/null || echo "")"
if [ -n "$new_id" ]; then
  check "ADMIN fulfilling a NEW request is refused" "409" \
        "$(code -X POST -u "$ADMIN" "${BASE}/api/requests/${new_id}/fulfil")"
else
  warn "no NEW request available, skipping the illegal-transition check"
fi

# ---- 7. the dashboard aggregates ------------------------------------------
groups="$(json -u "$ADMIN" "${BASE}/api/dashboard" \
          | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["callableByGroup"]))' 2>/dev/null || echo 0)"
check "dashboard reports all eight blood groups" "8" "$groups"

echo
if [ "$FAILURES" -eq 0 ]; then
  printf "${C_OK}HEALTH CHECK PASSED${C_RESET} - %s is up and behaving correctly.\n" "$BASE"
  exit 0
else
  printf "${C_ERR}HEALTH CHECK FAILED${C_RESET} - %d assertion(s) failed at %s.\n" "$FAILURES" "$BASE"
  exit 1
fi
