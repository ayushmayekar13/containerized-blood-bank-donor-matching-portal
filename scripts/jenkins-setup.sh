#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# jenkins-setup.sh - bring up a Jenkins controller for this project from
# nothing, reproducibly, with no wizard and no manual clicking.
#
#   bash scripts/jenkins-setup.sh start     start (and bootstrap on first run)
#   bash scripts/jenkins-setup.sh plugins   install the plugins in jenkins/plugins.txt
#   bash scripts/jenkins-setup.sh stop      stop the controller
#   bash scripts/jenkins-setup.sh status    is it up, and which plugins are present
#
# JENKINS_HOME is deliberately project-specific (~/.jenkins-bloodbank) so that
# this never touches an existing Jenkins on the machine, and so the whole
# controller can be deleted and rebuilt in one command - which is how the
# reproducibility claim in Task 7 is verified.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

export JENKINS_HOME="${JENKINS_HOME:-$HOME/.jenkins-bloodbank}"
JENKINS_WAR="${JENKINS_WAR:-/opt/homebrew/opt/jenkins-lts/libexec/jenkins.war}"
JENKINS_CLI_JAR="${JENKINS_CLI_JAR:-/opt/homebrew/opt/jenkins-lts/libexec/cli-2.568.2.jar}"
JENKINS_URL="http://localhost:${JENKINS_PORT}/"
JENKINS_ADMIN_USER="${JENKINS_ADMIN_USER:-admin}"
JENKINS_ADMIN_PASSWORD="${JENKINS_ADMIN_PASSWORD:-bloodbank-admin}"
LOG_FILE="${JENKINS_HOME}/jenkins-console.log"
PID_FILE="${JENKINS_HOME}/jenkins.pid"

export JENKINS_ADMIN_USER JENKINS_ADMIN_PASSWORD JENKINS_URL
export BUILD_JAVA_HOME="${JAVA_HOME}"
export BUILD_MAVEN_HOME="${BUILD_MAVEN_HOME:-/opt/homebrew/opt/maven/libexec}"

cli() {
  "${JAVA_HOME}/bin/java" -jar "$JENKINS_CLI_JAR" -s "$JENKINS_URL" \
       -auth "${JENKINS_ADMIN_USER}:${JENKINS_ADMIN_PASSWORD}" "$@"
}

wait_until_up() {
  local attempts="${1:-90}"
  for _ in $(seq 1 "$attempts"); do
    if curl -fsS -o /dev/null "${JENKINS_URL}login"; then return 0; fi
    sleep 2
  done
  return 1
}

case "${1:-start}" in

  start)
    if curl -fsS -o /dev/null "${JENKINS_URL}login" 2>/dev/null; then
      ok "Jenkins is already up at ${JENKINS_URL}"
      exit 0
    fi
    say "Starting Jenkins on port ${JENKINS_PORT} (JENKINS_HOME=${JENKINS_HOME})"
    mkdir -p "${JENKINS_HOME}/init.groovy.d"

    # The bootstrap scripts are version-controlled in jenkins/init.groovy.d and
    # copied in on every start, so editing them in the repository is what
    # changes the controller - never the other way round.
    cp "${PROJECT_ROOT}"/jenkins/init.groovy.d/*.groovy "${JENKINS_HOME}/init.groovy.d/"

    # ALLOW_LOCAL_CHECKOUT is required because this repository has no remote
    # yet: the Git plugin refuses to clone from a local directory by default,
    # since on a shared controller an untrusted job could read any path on the
    # filesystem. That reasoning does not apply to a single-user controller
    # cloning this developer's own working copy, and the flag is removed the
    # moment the repository is pushed and REPO_URL becomes a GitHub URL.
    nohup "${JAVA_HOME}/bin/java" \
        -Djenkins.install.runSetupWizard=false \
        -Dhudson.model.DownloadService.noSignatureCheck=true \
        -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true \
        -jar "$JENKINS_WAR" --httpPort="${JENKINS_PORT}" \
        > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"

    if wait_until_up; then
      ok "Jenkins is up at ${JENKINS_URL} (pid $(cat "$PID_FILE"))"
      ok "Administrator: ${JENKINS_ADMIN_USER}"
    else
      fail "Jenkins did not come up within 180s - last 30 log lines:"
      tail -30 "$LOG_FILE"
      exit 1
    fi
    ;;

  plugins)
    say "Installing plugins from jenkins/plugins.txt"
    # Read into a plain word list rather than using `mapfile`: macOS ships
    # bash 3.2, where mapfile does not exist, and this script has to run on the
    # machine the project is actually demonstrated on.
    plugins="$(sed 's/#.*//' "${PROJECT_ROOT}/jenkins/plugins.txt" | awk 'NF {print $1}' | tr '\n' ' ')"
    if [ -z "${plugins// /}" ]; then fail "no plugins listed"; exit 1; fi
    for p in $plugins; do echo "    $p"; done
    # The CLI resolves each plugin's own dependency tree from the update centre,
    # which is why only the seven directly-needed plugins are listed.
    if cli install-plugin $plugins; then
      say "Restarting Jenkins so the plugins load"
      cli safe-restart || true
      sleep 10
      wait_until_up && ok "Jenkins restarted with plugins loaded"
    else
      fail "plugin installation failed - see the output above"
      exit 1
    fi
    ;;

  stop)
    if [ -f "$PID_FILE" ]; then
      say "Stopping Jenkins (pid $(cat "$PID_FILE"))"
      kill "$(cat "$PID_FILE")" 2>/dev/null || true
      rm -f "$PID_FILE"
      ok "stopped"
    else
      warn "no pid file at ${PID_FILE}; nothing to stop"
    fi
    ;;

  status)
    if curl -fsS -o /dev/null "${JENKINS_URL}login" 2>/dev/null; then
      ok "Jenkins is up at ${JENKINS_URL}"
      say "Version: $(cli version 2>/dev/null || echo unknown)"
      say "Installed plugins:"
      cli list-plugins 2>/dev/null | sort | sed 's/^/    /'
    else
      warn "Jenkins is not responding at ${JENKINS_URL}"
      exit 1
    fi
    ;;

  cli)
    shift
    cli "$@"
    ;;

  *)
    fail "unknown command: $1"
    echo "usage: $0 {start|plugins|stop|status|cli <jenkins-cli args>}"
    exit 2
    ;;
esac
