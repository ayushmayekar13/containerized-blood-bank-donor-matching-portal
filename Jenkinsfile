#!/usr/bin/env groovy
/*
 * Pipeline as code for the Containerized Blood Bank Donor Matching Portal.
 *   Ayush Mayekar · 23102A0018 · DevOps · Task 8
 *
 * This file is the pipeline. It lives in the repository, is reviewed in the same
 * pull request as the code it builds, and is restorable on a fresh controller -
 * which is the whole difference from the freestyle job in Task 7, where the
 * configuration lived in Jenkins' own database and existed only as UI state.
 *
 * Declarative rather than scripted, for three concrete reasons:
 *   - the stage view renders each stage separately, which is what makes the
 *     quality gate in Task 10 legible at a glance rather than buried in a log;
 *   - `post` blocks run whether the stage passed or failed, so test reports are
 *     published even when the build is red - exactly when they matter most;
 *   - `parameters` are declared in the file, so the parameterised environment
 *     required by this task is version-controlled with everything else.
 *
 * Stage growth across the tasks is deliberate and visible in the git history:
 *   Task  8  Checkout -> Build -> Unit Test -> Package -> Deploy -> Health Check
 *   Task 10  + UI Test (Selenium) as a BLOCKING gate before anything is promoted
 *   Task 12  + Docker Image -> Publish to Registry -> Deploy Fresh Container
 */

pipeline {

    // The build runs on the controller because this is a single-machine lab: the
    // Docker socket, the registry and Chrome all live here. On a real controller
    // this would be `agent { label 'docker' }`, which is the only line that would
    // need to change.
    agent any

    /* ----------------------------------------------------------------------
     * Parameterised environment (the task's explicit requirement).
     *
     * Every one of these is a value that genuinely differs between
     * environments, and each has a default that makes a plain "Build Now"
     * behave exactly like the committed configuration. A parameter with no
     * sensible default is a trap, not a feature.
     * -------------------------------------------------------------------- */
    parameters {
        string(
            name: 'DEPLOY_PORT',
            defaultValue: '9090',
            description: 'Host port to publish the application on. 8080 is already taken on this host, which is why the default is 9090. Change it to deploy a second instance side by side without touching the first.'
        )
        string(
            name: 'TOMCAT_IMAGE',
            defaultValue: 'tomcat:10.1-jdk21-temurin',
            description: 'Servlet container base image. Pinned to a minor version rather than :latest, so a rebuild six months from now produces the same runtime.'
        )
        choice(
            name: 'SPRING_PROFILE',
            choices: ['', 'local'],
            description: 'Spring profile to activate in the deployed container. Empty is the default production-like profile; "local" enables the H2 console and debug logging.'
        )
        booleanParam(
            name: 'PUBLISH_IMAGE',
            defaultValue: true,
            description: 'Build a versioned image, publish it to the registry and deploy a fresh container from it. Off gives a build-and-test-only run, which is what a pull-request build would want.'
        )
        booleanParam(
            name: 'RUN_UI_TESTS',
            defaultValue: true,
            description: 'Run the Selenium journeys as a blocking quality gate. This exists so a deployment can be inspected by hand without the browser suite running - NOT as a way to make a red build go green. Turning it off is recorded in the build parameters of every build that did so.'
        )
        booleanParam(
            name: 'RUN_HEALTH_CHECK',
            defaultValue: true,
            description: 'Run the behavioural health check after deployment. Leave on: a container that answers 200 while the matching engine is broken has failed in the only way that matters.'
        )
    }

    environment {
        JAVA_HOME     = '/opt/homebrew/opt/openjdk@21'
        MAVEN_HOME    = '/opt/homebrew/opt/maven/libexec'
        PATH          = "${JAVA_HOME}/bin:${MAVEN_HOME}/bin:/opt/homebrew/bin:/usr/local/bin:${env.PATH}"
        APP_PORT      = "${params.DEPLOY_PORT}"
        APP_CONTAINER = 'bloodbank-app'
        APP_VOLUME    = 'bloodbank-data'
        TOMCAT_IMAGE  = "${params.TOMCAT_IMAGE}"
        IMAGE_NAME    = 'bloodbank'
        REGISTRY_PORT = '5001'
        SPRING_PROFILE = "${params.SPRING_PROFILE}"
        // Maven in CI: batch mode and no transfer progress, so the captured
        // console log is readable evidence rather than pages of download bars.
        //
        // The local repository is deliberately NOT redirected into the
        // workspace. Doing that on the first run cost 597 seconds in the
        // Package stage, because every dependency was downloaded again - a
        // per-workspace cache is wiped with the workspace, so it is not a cache.
        // The shared ~/.m2 brings the stage under ten seconds and keeps the
        // pipeline inside the six-minute budget in NFR-13.
        MVN           = 'mvn -B -ntp'
    }

    options {
        timestamps()
        timeout(time: 25, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '5'))
        // A pipeline that re-uses a dirty workspace can pass for reasons that
        // have nothing to do with the commit under test.
        skipDefaultCheckout(false)
    }

    triggers {
        // Same reasoning as the freestyle job: a local repository has no server
        // able to call back into Jenkins, so polling stands in for a webhook
        // until the repository is pushed.
        pollSCM('H/2 * * * *')
    }

    stages {

        stage('Checkout') {
            steps {
                echo "Building ${env.JOB_NAME} #${env.BUILD_NUMBER}"
                checkout scm
                sh 'git --no-pager log -1 --pretty="commit %h by %an on %ad%n  %s"'
                sh 'java -version 2>&1; mvn -v | head -3'
            }
        }

        stage('Build') {
            steps {
                // compile only - a compile failure should be reported as a
                // compile failure, not hidden inside a test run
                sh "${MVN} clean compile"
            }
        }

        stage('Unit Test') {
            steps {
                // Surefire only. The Selenium suite is Failsafe and runs after
                // deployment, because it needs a deployed application to drive.
                sh "${MVN} test"
            }
            post {
                always {
                    // `always`, not `success`: a red build is exactly when the
                    // test report is needed. allowEmptyResults is false so a
                    // build whose tests silently did not run cannot be green.
                    junit allowEmptyResults: false, testResults: 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh "${MVN} package -DskipTests"
                sh 'ls -lh target/bloodbank.war'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/bloodbank.war',
                                     fingerprint: true,
                                     onlyIfSuccessful: true
                }
            }
        }

        stage('Deploy to Tomcat') {
            steps {
                // The parameters are passed as environment variables to the same
                // script a developer runs by hand, so there is exactly one
                // deployment procedure and no "but CI does it differently".
                sh '''
                    set -e
                    APP_PORT="${APP_PORT}" \
                    APP_CONTAINER="${APP_CONTAINER}" \
                    APP_VOLUME="${APP_VOLUME}" \
                    TOMCAT_IMAGE="${TOMCAT_IMAGE}" \
                    SPRING_PROFILE="${SPRING_PROFILE}" \
                    bash scripts/deploy-tomcat.sh target/bloodbank.war
                '''
            }
        }

        stage('Health Check') {
            when { expression { return params.RUN_HEALTH_CHECK } }
            steps {
                // Behavioural, not just a 200: it asserts the compatibility
                // matrix, the role guard and the state machine through the API.
                sh 'bash scripts/health-check.sh "http://localhost:${APP_PORT}"'
            }
        }

        /* ------------------------------------------------------------------
         * THE QUALITY GATE (Task 10).
         *
         * Everything that promotes a build - publishing the image to the
         * registry, deploying it as the running container - is placed AFTER
         * this stage. That ordering is the entire mechanism: if a journey
         * fails, this stage fails, the pipeline stops, and the stages below
         * are never entered. Nothing needs to check a flag, because a failing
         * regression cannot physically reach them.
         *
         * Two things are deliberately NOT done here:
         *
         *   - no `catchError`, no `|| true`, no `unstable`. Any of those would
         *     record the failure and let the build continue, which is a gate
         *     that reports rather than a gate that stops;
         *   - no retry. A journey that passes on the second attempt is telling
         *     you the suite is flaky, and hiding that is how a suite stops
         *     being believed.
         * ------------------------------------------------------------------ */
        stage('UI Test (quality gate)') {
            when { expression { return params.RUN_UI_TESTS } }
            steps {
                sh """
                    set -e
                    rm -rf target/screenshots
                    ${MVN} -P ui-tests verify \
                        -Dapp.base.url=http://localhost:${APP_PORT} \
                        -Dselenium.headless=true
                """
            }
            post {
                always {
                    // `always`: a red gate is exactly when the report and the
                    // screenshots are needed, and the next build overwrites the
                    // workspace.
                    junit allowEmptyResults: false, testResults: 'target/failsafe-reports/*.xml'
                    archiveArtifacts artifacts: 'target/screenshots/**',
                                     allowEmptyArchive: true,
                                     onlyIfSuccessful: false
                }
                failure {
                    echo 'QUALITY GATE FAILED - a user journey is broken.'
                    echo 'Nothing after this stage runs: no image is published and no container is replaced.'
                    sh 'ls -1 target/screenshots 2>/dev/null | sed "s/^/  screenshot: /" || echo "  (no screenshots captured)"'
                }
            }
        }

        /* ------------------------------------------------------------------
         * PROMOTION (Task 12) - unreachable while the gate above is red.
         *
         * Build a versioned image, publish it to the registry, and deploy a
         * fresh container FROM THE REGISTRY. The last part matters: deploying
         * the local image that was just built would hide a broken push until
         * the first attempted rollback, which is the worst possible moment to
         * discover it.
         * ------------------------------------------------------------------ */
        stage('Promote: image, registry, deploy') {
            when { expression { return params.PUBLISH_IMAGE } }
            steps {
                // The image is tagged with the Jenkins build number, which is
                // monotonic and unique. That tag is immutable and is what the
                // Task 14 rollback pins to; `latest` moves alongside it and is
                // a convenience only.
                sh '''
                    set -e
                    REGISTRY_PORT="${REGISTRY_PORT}" \
                    APP_PORT="${APP_PORT}" \
                    APP_CONTAINER="${APP_CONTAINER}" \
                    APP_VOLUME="${APP_VOLUME}" \
                    bash scripts/release.sh "${BUILD_NUMBER}"
                '''
            }
            post {
                success {
                    sh '''
                        echo "--- registry catalogue ---"
                        curl -fsS "http://localhost:${REGISTRY_PORT}/v2/_catalog"
                        echo
                        echo "--- published tags ---"
                        curl -fsS "http://localhost:${REGISTRY_PORT}/v2/${IMAGE_NAME}/tags/list"
                        echo
                        echo "--- running container ---"
                        docker ps --filter "name=${APP_CONTAINER}" --format "  {{.Names}}  {{.Image}}  {{.Status}}"
                    '''
                }
            }
        }

        stage('Post-deploy verification') {
            when { expression { return params.PUBLISH_IMAGE && params.RUN_HEALTH_CHECK } }
            steps {
                // The health check runs a second time, against the container
                // that was actually deployed from the registry. The earlier run
                // verified the build; this one verifies the release.
                sh 'bash scripts/health-check.sh "http://localhost:${APP_PORT}"'
            }
        }
    }

    post {
        success {
            echo "SUCCESS - deployed and healthy at http://localhost:${params.DEPLOY_PORT}"
            sh 'docker ps --filter "name=${APP_CONTAINER}" --format "  {{.Names}}  {{.Image}}  {{.Status}}  {{.Ports}}"'
        }
        failure {
            echo 'FAILURE - collecting diagnostics'
            // Container logs are the first thing anyone asks for after a failed
            // deployment, so they are captured automatically rather than
            // requiring someone to be at the machine before the container is
            // replaced by the next build.
            sh 'docker logs --tail 100 ${APP_CONTAINER} 2>&1 || echo "(no container to read logs from)"'
        }
        always {
            echo "Build #${env.BUILD_NUMBER} finished with status ${currentBuild.currentResult}"
        }
    }
}
