#!/usr/bin/env bash
# =============================================================================
#  Container entrypoint for the Blood Bank Donor Matching Portal.
#
#  Starts as root for exactly one reason - to make the mounted data volume
#  writable by the unprivileged application user - then drops privileges and
#  execs Tomcat. This is the same pattern the official postgres and mysql
#  images use, and it exists because of a real failure, not as ceremony.
#
#  The failure (BUG-11): a named volume keeps the ownership it was created
#  with. The volume here was first created by an earlier, root-running Tomcat
#  deployment, so when the hardened image mounted it as the unprivileged
#  `bloodbank` user, H2 could not open the database file:
#
#      java.nio.file.AccessDeniedException: /opt/bloodbank/data/bloodbank.mv.db
#
#  Docker only copies the image directory's ownership into a *fresh* volume, so
#  nothing in the Dockerfile can fix a volume that already exists. It has to be
#  corrected at start-up, which is what this script does - and doing it here
#  also means the image is self-healing on any host, rather than depending on
#  whoever created the volume having got it right.
# =============================================================================
set -euo pipefail

APP_USER="${APP_USER:-bloodbank}"
APP_UID="${APP_UID:-999}"
APP_GID="${APP_GID:-999}"
DATA_DIR="${DATA_DIR:-/opt/bloodbank/data}"

if [ "$(id -u)" = '0' ]; then
  mkdir -p "$DATA_DIR"

  # Only chown when it is actually wrong. On a large volume a blanket
  # `chown -R` on every start would add seconds to each container start for no
  # benefit, and would mask the fact that something else changed the ownership.
  current_owner="$(stat -c '%u' "$DATA_DIR")"
  if [ "$current_owner" != "$APP_UID" ]; then
    echo "entrypoint: data directory is owned by uid ${current_owner}, expected ${APP_UID} - correcting"
    chown -R "${APP_UID}:${APP_GID}" "$DATA_DIR"
  fi

  echo "entrypoint: dropping privileges to ${APP_USER} (${APP_UID}:${APP_GID})"
  # setpriv rather than su or sudo: it execs the target process directly,
  # so Tomcat remains PID 1 and receives SIGTERM from `docker stop` properly.
  # With `su`, the shell becomes PID 1, signals are not forwarded, and every
  # stop would hit the ten-second timeout and kill the JVM instead of letting
  # it shut down.
  exec setpriv --reuid="$APP_UID" --regid="$APP_GID" --clear-groups "$@"
fi

# Already unprivileged - a host may run this image with `--user`, which is
# fine as long as the volume is writable by that user.
echo "entrypoint: already running as uid $(id -u); starting directly"
exec "$@"
