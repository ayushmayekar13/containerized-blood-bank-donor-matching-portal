#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# release.sh - build a versioned image, publish it to the registry, and deploy
# a fresh container from the published image.
#
#   bash scripts/release.sh <version>        full release
#   bash scripts/release.sh <version> --no-deploy
#
# Called by the Jenkins pipeline's Promote stage with the build number, and
# runnable by hand with any version string. One script, so there is a single
# definition of what "release" means.
#
# The ordering is deliberate and is the whole point of the task:
#
#   build  ->  push  ->  deploy FROM THE REGISTRY
#
# The deployment pulls the image back out of the registry rather than using the
# local one that was just built. That is slower by a second, and it is what
# makes the release honest: if the push were broken or the image were
# unreachable, deploying the local copy would hide it until the first time
# someone tried to roll back - which is the worst possible moment to find out.
# ---------------------------------------------------------------------------
set -uo pipefail
source "$(dirname "$0")/env.sh"

VERSION="${1:?usage: release.sh <version> [--no-deploy]}"
DEPLOY=true
[ "${2:-}" = "--no-deploy" ] && DEPLOY=false

GIT_COMMIT="$(git -C "$PROJECT_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
LOCAL_IMAGE="${IMAGE_NAME}:${VERSION}"
REMOTE_IMAGE="${REGISTRY_HOST}/${IMAGE_NAME}:${VERSION}"
REMOTE_LATEST="${REGISTRY_HOST}/${IMAGE_NAME}:latest"

say "Releasing ${IMAGE_NAME} version ${VERSION} (commit ${GIT_COMMIT})"

# ---------------------------------------------------------------------------
# 0. the registry must be up before anything is built
# ---------------------------------------------------------------------------
if ! curl -fsS -o /dev/null "http://${REGISTRY_HOST}/v2/" 2>/dev/null; then
  warn "registry at ${REGISTRY_HOST} is not responding - starting it"
  docker rm -f "${REGISTRY_CONTAINER}" >/dev/null 2>&1
  docker run -d --name "${REGISTRY_CONTAINER}" \
      -p "${REGISTRY_PORT}:5000" \
      -v bloodbank-registry-data:/var/lib/registry \
      -e REGISTRY_STORAGE_DELETE_ENABLED=true \
      --restart unless-stopped registry:2 >/dev/null
  for _ in $(seq 1 20); do
    curl -fsS -o /dev/null "http://${REGISTRY_HOST}/v2/" 2>/dev/null && break
    sleep 2
  done
fi
ok "registry reachable at ${REGISTRY_HOST}"

# ---------------------------------------------------------------------------
# 1. build, tagged with the version AND latest
# ---------------------------------------------------------------------------
say "Building ${LOCAL_IMAGE}"
docker build \
    --build-arg BUILD_NUMBER="${VERSION}" \
    --build-arg GIT_COMMIT="${GIT_COMMIT}" \
    --build-arg BUILD_DATE="${BUILD_DATE}" \
    -t "${LOCAL_IMAGE}" \
    -t "${IMAGE_NAME}:latest" \
    "$PROJECT_ROOT" || { fail "image build failed"; exit 1; }
ok "built $(docker images --format '{{.Size}}' "${LOCAL_IMAGE}" | head -1)"

# ---------------------------------------------------------------------------
# 2. publish
#
# If this were Docker Hub instead of a local registry, the only differences
# would be the image prefix and a `docker login` using credentials injected by
# Jenkins' credentials-binding plugin - never written into the Jenkinsfile.
# See docs/12-jenkins-docker-cd.md.
# ---------------------------------------------------------------------------
say "Publishing to ${REGISTRY_HOST}"
docker tag "${LOCAL_IMAGE}" "${REMOTE_IMAGE}"
docker tag "${LOCAL_IMAGE}" "${REMOTE_LATEST}"
docker push -q "${REMOTE_IMAGE}" || { fail "push failed"; exit 1; }
docker push -q "${REMOTE_LATEST}" || { fail "push of latest failed"; exit 1; }
ok "published ${REMOTE_IMAGE}"

say "Registry now holds:"
curl -fsS "http://${REGISTRY_HOST}/v2/${IMAGE_NAME}/tags/list" \
  | python3 -c 'import json,sys; d=json.load(sys.stdin); print("    " + d["name"] + ": " + ", ".join(sorted(d["tags"])))'

if [ "$DEPLOY" = false ]; then
  ok "release complete (deployment skipped)"
  exit 0
fi

# ---------------------------------------------------------------------------
# 3. deploy a FRESH container, from the registry
# ---------------------------------------------------------------------------
say "Deploying a fresh container from ${REMOTE_IMAGE}"

# Remove the local copy of the remote tag first, so the run below genuinely
# pulls from the registry. Without this, Docker would use the local image and
# a broken registry would go unnoticed.
docker rmi "${REMOTE_IMAGE}" >/dev/null 2>&1 || true
docker pull -q "${REMOTE_IMAGE}" || { fail "could not pull back the image just pushed"; exit 1; }
ok "pulled ${REMOTE_IMAGE} back from the registry"

PREVIOUS="$(docker inspect -f '{{.Config.Image}}' "${APP_CONTAINER}" 2>/dev/null || echo none)"
say "replacing container (previously running: ${PREVIOUS})"

docker rm -f "${APP_CONTAINER}" >/dev/null 2>&1 || true
docker volume create "${APP_VOLUME}" >/dev/null

docker run -d \
    --name "${APP_CONTAINER}" \
    -p "${APP_PORT}:${APP_CONTAINER_PORT}" \
    -v "${APP_VOLUME}:/opt/bloodbank/data" \
    -e BLOODBANK_VERSION="${VERSION}" \
    --restart unless-stopped \
    --label "bloodbank.version=${VERSION}" \
    --label "bloodbank.commit=${GIT_COMMIT}" \
    "${REMOTE_IMAGE}" >/dev/null || { fail "could not start the new container"; exit 1; }

say "Waiting for ${HEALTH_URL}"
for attempt in $(seq 1 60); do
  if curl -fsS -o /dev/null "${HEALTH_URL}" 2>/dev/null; then
    ok "healthy after ~$((attempt * 3))s"
    docker ps --filter "name=^${APP_CONTAINER}$" \
        --format "    {{.Names}}  {{.Image}}  {{.Status}}  {{.Ports}}"
    say "Deployed version: $(curl -fsS -u "${DEMO_ADMIN_USER}:${DEMO_ADMIN_PASS}" "${APP_URL}/actuator/info" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)["app"]["version"])' 2>/dev/null || echo "${VERSION}")"
    exit 0
  fi
  sleep 3
done

fail "the new container did not become healthy within 180s"
docker logs --tail 40 "${APP_CONTAINER}"
exit 1
