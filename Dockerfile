# =============================================================================
#  Containerized Blood Bank Donor Matching Portal
#  Ayush Mayekar · 23102A0018 · DevOps · Task 11
#
#  Multi-stage build: the toolchain used to produce the artefact never reaches
#  the image that runs in production.
#
#  Stage 1 (build)   maven:3.9-eclipse-temurin-21   ~ 800 MB, discarded
#  Stage 2 (runtime) tomcat:10.1-jdk21-temurin      ~ 850 MB, shipped
#
#  A single-stage build would ship Maven, the full JDK toolchain, the source
#  tree and every downloaded dependency - a larger image, a larger attack
#  surface, and the source code itself handed to anyone who can pull it.
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build the WAR
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# Dependencies first, in their own layer.
#
# pom.xml changes rarely; source changes constantly. Copying the pom alone and
# resolving dependencies before copying src/ means Docker reuses the cached
# dependency layer on every build where the pom has not changed, which is the
# difference between a ~10-second rebuild and a ~5-minute one.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src ./src

# -DskipTests is correct HERE and only here: the pipeline has already run all
# 178 unit tests and the 16 Selenium journeys before this image is built
# (Jenkinsfile stages Unit Test and UI Test). Re-running them inside the image
# build would double the pipeline time to prove exactly what was just proved -
# and would make the image build depend on a browser.
RUN mvn -B -ntp clean package -DskipTests

# -----------------------------------------------------------------------------
# Stage 2 — the runtime image
# -----------------------------------------------------------------------------
FROM tomcat:10.1-jdk21-temurin

LABEL org.opencontainers.image.title="Blood Bank Donor Matching Portal" \
      org.opencontainers.image.description="Donor registry, matching engine and role-based request workflow" \
      org.opencontainers.image.authors="Ayush Mayekar <ayushmayekar1333@gmail.com>" \
      org.opencontainers.image.source="https://github.com/ayushmayekar/devops-bloodbank-portal" \
      org.opencontainers.image.licenses="academic-coursework" \
      project.student="Ayush Mayekar" \
      project.roll="23102A0018" \
      project.subject="DevOps"

# Build metadata, passed in by the pipeline so an image can always be traced
# back to the commit and build that produced it.
ARG BUILD_NUMBER=local
ARG GIT_COMMIT=unknown
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.version="${BUILD_NUMBER}" \
      org.opencontainers.image.revision="${GIT_COMMIT}" \
      org.opencontainers.image.created="${BUILD_DATE}"
ENV BLOODBANK_VERSION="${BUILD_NUMBER}"

# Tomcat ships example applications that are not wanted anywhere near a
# deployment: the manager and host-manager consoles, the documentation and the
# examples webapp, which has historically carried its own vulnerabilities.
RUN rm -rf /usr/local/tomcat/webapps/*

# The database lives on a volume mounted here, so the container itself stays
# disposable. This is what makes "recreate the container" a safe deployment
# strategy and what makes the rollback in Task 14 a re-deploy rather than a
# restore from backup.
ENV BLOODBANK_DB_PATH=/opt/bloodbank/data/bloodbank
RUN mkdir -p /opt/bloodbank/data
VOLUME ["/opt/bloodbank/data"]

# Deployed as ROOT.war, so the context path is "/" in every environment and no
# URL in the Selenium suite, the health check or the documentation has to change
# between local, container and CI.
COPY --from=build /build/target/bloodbank.war /usr/local/tomcat/webapps/ROOT.war

# Run as an unprivileged user. The base image runs as root by default; a
# servlet container has no need for it, and a container escape from a root
# process is considerably more useful to an attacker than one from a user that
# owns only its own data directory.
#
# The uid and gid are pinned to 999 rather than left to whatever the next
# available number happens to be, because the entrypoint has to compare the
# volume's ownership against them.
RUN groupadd --system --gid 999 bloodbank \
 && useradd --system --uid 999 --gid 999 --home-dir /opt/bloodbank bloodbank \
 && chown -R bloodbank:bloodbank /opt/bloodbank /usr/local/tomcat

# The entrypoint starts as root ONLY to make a pre-existing data volume
# writable, then drops to uid 999 with setpriv and execs Tomcat. See
# docker/entrypoint.sh for the failure this exists to fix (BUG-11): a named
# volume keeps the ownership it was created with, and nothing in a Dockerfile
# can change a volume that already exists.
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

EXPOSE 8080

# The container reports its own health, using the same endpoint the Ansible
# check and the Jenkins post-deploy step use. start-period covers JVM and
# Spring context startup, so a slow start is not reported as a failure.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

CMD ["catalina.sh", "run"]
