#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# Shared environment for every script in this project.
# Source it, do not execute it:   source scripts/env.sh
#
# Everything environment-specific lives here and nowhere else, so that the
# same scripts work on a laptop, inside Jenkins and from an Ansible task.
# ---------------------------------------------------------------------------

# --- project identity ------------------------------------------------------
export PROJECT_NAME="bloodbank"
export PROJECT_TITLE="Containerized Blood Bank Donor Matching Portal"
export PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# --- toolchain -------------------------------------------------------------
# The system default `java` on this host is 11; Spring Boot 3 needs 17+, so
# JDK 21 is pinned explicitly rather than relying on PATH order.
if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME}/bin/java" -version 2>&1 | grep -q '"21'; then
  for candidate in \
      /opt/homebrew/opt/openjdk@21 \
      /usr/local/opt/openjdk@21 \
      /Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home ; do
    if [ -x "$candidate/bin/java" ]; then export JAVA_HOME="$candidate"; break; fi
  done
fi
export PATH="${JAVA_HOME:+$JAVA_HOME/bin:}$HOME/.local/bin:$PATH"

# --- ports -----------------------------------------------------------------
# Host port 8080 is already taken by an unrelated service on this machine,
# so Jenkins and the application are both re-mapped. Change them here only.
export JENKINS_PORT="${JENKINS_PORT:-8090}"      # Jenkins web UI
export APP_PORT="${APP_PORT:-9090}"              # application, published on the host
export APP_CONTAINER_PORT="${APP_CONTAINER_PORT:-8080}"   # Tomcat inside the container
export REGISTRY_PORT="${REGISTRY_PORT:-5001}"    # private Docker registry

# --- docker names ----------------------------------------------------------
export IMAGE_NAME="${IMAGE_NAME:-bloodbank}"
export IMAGE_TAG="${IMAGE_TAG:-latest}"
export REGISTRY_HOST="${REGISTRY_HOST:-localhost:${REGISTRY_PORT}}"
export REGISTRY_CONTAINER="${REGISTRY_CONTAINER:-bloodbank-registry}"
export APP_CONTAINER="${APP_CONTAINER:-bloodbank-app}"
export APP_VOLUME="${APP_VOLUME:-bloodbank-data}"

# --- application -----------------------------------------------------------
export APP_URL="http://localhost:${APP_PORT}"
export HEALTH_URL="${APP_URL}/actuator/health"
export WAR_NAME="${PROJECT_NAME}.war"
export WAR_PATH="${PROJECT_ROOT}/target/${WAR_NAME}"

# demonstration credentials (local deployment only, see docs/API.md section 1.4)
export DEMO_ADMIN_USER="${DEMO_ADMIN_USER:-admin}"
export DEMO_ADMIN_PASS="${DEMO_ADMIN_PASS:-Admin@123}"
export DEMO_STAFF_USER="${DEMO_STAFF_USER:-staff}"
export DEMO_STAFF_PASS="${DEMO_STAFF_PASS:-Staff@123}"

# --- evidence --------------------------------------------------------------
export EVIDENCE_DIR="${PROJECT_ROOT}/evidence"

# --- pretty output helpers -------------------------------------------------
if [ -t 1 ]; then
  C_RESET='\033[0m'; C_OK='\033[0;32m'; C_WARN='\033[0;33m'; C_ERR='\033[0;31m'; C_INFO='\033[0;36m'
else
  C_RESET=''; C_OK=''; C_WARN=''; C_ERR=''; C_INFO=''
fi
say()  { printf "${C_INFO}==>${C_RESET} %s\n" "$*"; }
ok()   { printf "${C_OK}  ok${C_RESET}   %s\n" "$*"; }
warn() { printf "${C_WARN}  warn${C_RESET} %s\n" "$*"; }
fail() { printf "${C_ERR}  FAIL${C_RESET} %s\n" "$*"; }
