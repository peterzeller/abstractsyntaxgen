pipeline {
    agent {
        dockerfile {
            filename 'ci/Dockerfile'
            args '-u root --entrypoint=""'
        }
    }
    options {
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30', artifactDaysToKeepStr: '14'))
    }
    stages {
        stage('Toolchain') {
            steps {
                sh '''
                    set -eu
                    java -version
                    git config --global --add safe.directory "$WORKSPACE"
                    git clean -fdx -q
                    chmod +x gradlew
                '''
            }
        }
        stage('Generate test ASTs') { steps { sh './gradlew generateTestAsts --no-daemon --stacktrace' } }
        stage('Build and test') { steps { sh './gradlew build --no-daemon --stacktrace' } }
    }
    post {
        always {
            junit allowEmptyResults: true, testResults: 'build/test-results/test/*.xml'
            archiveArtifacts allowEmptyArchive: true, artifacts: 'build/reports/tests/**'
        }
        cleanup { sh 'chmod -R a+rwX "$WORKSPACE" 2>/dev/null || true' }
    }
}
