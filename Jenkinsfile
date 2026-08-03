pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
    }

    parameters {
        booleanParam(name: 'SKIP_TESTS', defaultValue: false, description: 'Skip Maven tests during packaging')
        booleanParam(name: 'DEPLOY', defaultValue: true, description: 'Deploy with docker compose after build')
    }

    environment {
        COMPOSE_PROJECT_NAME = 'luntan'
        MAVEN_IMAGE = 'maven:3.9.9-eclipse-temurin-21'
        DEPLOY_ENV_FILE = '/opt/luntan/.env'
    }

    stages {
        stage('Verify Toolchain') {
            steps {
                sh '''
                    set -eu
                    docker version
                    docker compose version
                    docker info --format 'Docker server: {{.ServerVersion}}'
                '''
            }
        }

        stage('Build') {
            steps {
                sh '''
                    set -eu
                    mkdir -p "$WORKSPACE/.m2"
                    if [ "${SKIP_TESTS:-false}" = "true" ]; then
                      MAVEN_GOAL="clean package -DskipTests"
                    else
                      MAVEN_GOAL="clean verify"
                    fi
                    docker run --rm --pull=missing \\
                      --user "$(id -u):$(id -g)" \\
                      -v "$WORKSPACE:/workspace" \\
                      -v "$WORKSPACE/.m2:/m2" \\
                      -w /workspace \\
                      -e MAVEN_OPTS=-Xmx768m \\
                      "$MAVEN_IMAGE" \\
                      sh -c "mvn -B -Dmaven.repo.local=/m2/repository $MAVEN_GOAL"
                '''
            }
        }

        stage('Validate Compose') {
            steps {
                sh '''
                    set -eu
                    test -f "$DEPLOY_ENV_FILE" || {
                      echo "Missing $DEPLOY_ENV_FILE. Create it on the deployment host before deploying."
                      exit 1
                    }
                    set +x
                    for variable in MYSQL_ROOT_PASSWORD IDENTITY_DB_PASSWORD FORUM_DB_PASSWORD REDIS_PASSWORD JWT_SECRET INTERNAL_SERVICE_TOKEN; do
                      value="$(awk -F= -v key="$variable" '$1 == key {sub(/^[^=]*=/, ""); print; exit}' "$DEPLOY_ENV_FILE")"
                      case "$value" in
                        ""|replace-with-*|change-this-*)
                          echo "Missing production value for $variable"
                          exit 1
                          ;;
                      esac
                    done
                    unset value
                    set -x
                    docker compose --env-file "$DEPLOY_ENV_FILE" config --quiet
                '''
            }
        }

        stage('Deploy') {
            when {
                expression { return params.DEPLOY }
            }
            steps {
                sh '''
                    set -eu
                    if ! docker compose --env-file "$DEPLOY_ENV_FILE" up --build -d --remove-orphans; then
                      docker compose --env-file "$DEPLOY_ENV_FILE" ps || true
                      docker compose --env-file "$DEPLOY_ENV_FILE" logs --tail=200 mysql redis nacos identity-service forum-service api-gateway || true
                      exit 1
                    fi
                    docker compose --env-file "$DEPLOY_ENV_FILE" ps
                '''
            }
        }

        stage('Health Check') {
            when {
                expression { return params.DEPLOY }
            }
            steps {
                sh '''
                    set -eu
                    for i in $(seq 1 60); do
                      container_id="$(docker compose --env-file "$DEPLOY_ENV_FILE" ps -q api-gateway)"
                      if [ -n "$container_id" ]; then
                        status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' "$container_id")"
                        echo "api-gateway health: $status"
                        if [ "$status" = "healthy" ]; then
                          docker compose --env-file "$DEPLOY_ENV_FILE" ps
                          exit 0
                        fi
                      fi
                      sleep 3
                    done
                    docker compose --env-file "$DEPLOY_ENV_FILE" ps
                    docker compose --env-file "$DEPLOY_ENV_FILE" logs --tail=200 api-gateway identity-service forum-service
                    exit 1
                '''
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: true
            junit testResults: '**/target/surefire-reports/*.xml', allowEmptyResults: true
        }
        failure {
            sh 'docker compose --env-file "$DEPLOY_ENV_FILE" ps || true'
        }
    }
}
