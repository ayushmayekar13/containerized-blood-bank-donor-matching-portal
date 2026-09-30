#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# preflight.sh - verify this machine can build, test, containerize and deploy
# the project. Run this FIRST on any new machine, and again before the
# practical demonstration. Exits non-zero if anything required is missing.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

FAILURES=0
need() {  # need <label> <command...>
  local label="$1"; shift
  if out="$("$@" 2>&1 | head -1)"; then ok "$label — $out"; else fail "$label — not found ($*)"; FAILURES=$((FAILURES+1)); fi
}

say "Preflight for ${PROJECT_TITLE}"
echo

say "1. Toolchain"
if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"21'; then
  ok "Java 21 — JAVA_HOME=$JAVA_HOME"
else
  fail "Java 21 not found. Install with: brew install openjdk@21"; FAILURES=$((FAILURES+1))
fi
need "Maven"   mvn -v
need "Git"     git --version
need "Docker"  docker --version
need "Ansible" ansible --version

if docker info >/dev/null 2>&1; then ok "Docker daemon is running"
else fail "Docker daemon is not running — start Docker Desktop"; FAILURES=$((FAILURES+1)); fi

if [ -d "/Applications/Google Chrome.app" ] || command -v google-chrome >/dev/null 2>&1; then
  ok "Google Chrome present (Selenium Manager will resolve chromedriver)"
else
  warn "Chrome not found — the Selenium suite will not run"
fi

if ansible-galaxy collection list community.docker 2>/dev/null | grep -q community.docker; then
  ok "Ansible collection community.docker installed"
else
  fail "Missing collection — run: ansible-galaxy collection install community.docker"; FAILURES=$((FAILURES+1))
fi

echo
say "2. Ports (host 8080 is deliberately avoided — it is already in use)"
port_state() {  # port_state <port> <expected owner substring or empty>
  local port="$1" expect="${2:-}"
  local line; line="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN 2>/dev/null | tail -n +2 | head -1)"
  if [ -z "$line" ]; then ok "port $port free"; return 0; fi
  if [ -n "$expect" ] && grep -qi "$expect" <<<"$line"; then
    ok "port $port in use by this project ($(awk '{print $1}' <<<"$line"))"
  else
    warn "port $port already in use by: $(awk '{print $1, "pid", $2}' <<<"$line")"
  fi
}
port_state "$JENKINS_PORT"  "java"
port_state "$APP_PORT"      "docker\|com.docke"
port_state "$REGISTRY_PORT" "docker\|com.docke"

echo
say "3. Docker base images"
for img in tomcat:10.1-jdk21-temurin registry:2; do
  if docker image inspect "$img" >/dev/null 2>&1; then ok "image present: $img"
  else warn "image missing: $img (will be pulled on first build: docker pull $img)"; fi
done

echo
say "4. Project layout"
for f in pom.xml Dockerfile Jenkinsfile ansible/site.yml; do
  if [ -f "$PROJECT_ROOT/$f" ]; then ok "found $f"; else warn "missing $f (expected once the relevant task is complete)"; fi
done

echo
if [ "$FAILURES" -eq 0 ]; then
  printf "${C_OK}PREFLIGHT PASSED${C_RESET} — this machine can build, test and deploy the project.\n"
  exit 0
else
  printf "${C_ERR}PREFLIGHT FAILED${C_RESET} — %d required item(s) missing. See messages above.\n" "$FAILURES"
  exit 1
fi
