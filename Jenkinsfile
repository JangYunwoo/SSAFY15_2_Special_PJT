pipeline {
    agent { label 'gongguhan-ci' }
    options {
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '15', artifactNumToKeepStr: '8'))
        timestamps()
        skipDefaultCheckout(true)
    }
    stages {
        stage('Checkout') {
            steps {
                deleteDir()
                script {
                    // Explicit checkout returns SCM variables; skipDefaultCheckout does not
                    // automatically export them into the Declarative environment.
                    def checkedOut = checkout scm
                    env.GIT_BRANCH = checkedOut.GIT_BRANCH ?: ''
                    env.RELEASE_SHA = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    if (env.JOB_NAME == 'gongguhan-DevelopV2' && env.GIT_BRANCH != 'origin/DevelopV2') {
                        error('Production checkout must explicitly identify origin/DevelopV2')
                    }
                }
            }
        }
        stage('Frontend test and build') {
            steps { sh 'bash deploy/jenkins/ci.sh frontend' }
        }
        stage('Deployment guards') {
            steps { sh 'python3 -m unittest discover -s deploy/jenkins -p "test_*.py" -v' }
        }
        stage('Backend regression') {
            steps { sh 'bash deploy/jenkins/ci.sh backend' }
            post { always { junit testResults: 'backend/build/test-results/test/*.xml', allowEmptyResults: false } }
        }
        stage('AI regression') {
            steps { sh 'bash deploy/jenkins/ci.sh ai' }
            post { always { junit testResults: 'ci-results/ai.xml', allowEmptyResults: false } }
        }
        stage('Build release images') {
            steps { sh 'bash deploy/jenkins/ci.sh images' }
        }
        stage('Deploy DevelopV2') {
            // A missing/wrong branch is a failure (checked above and in deploy.py),
            // not a silently skipped "successful" production deployment.
            when { expression { env.JOB_NAME == 'gongguhan-DevelopV2' } }
            steps { sh 'python3 deploy/jenkins/deploy.py "$RELEASE_SHA"' }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'ci-results/*.json,ci-results/*.xml,frontend/dist/index.html', allowEmptyArchive: true
        }
        cleanup {
            script {
                if (fileExists('deploy/jenkins/ci.sh')) {
                    sh 'bash deploy/jenkins/ci.sh cleanup'
                }
            }
        }
    }
}
