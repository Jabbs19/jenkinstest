def label = "jenkins-slave-${UUID.randomUUID().toString()}"
def COMMIT_HEAD_TAG = ""
def DEVELOP_IMAGE_TAG = "test"
def RELEASE_IMAGE_TAG = "release"

podTemplate(
    label: label,
    cloud: "openshift",
    containers: [
        containerTemplate(name: 'maven', image: 'maven:alpine', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'golang', image: 'golang:alpine', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'helm', image: 'docker-registry.default.svc:5000/openshift/helm:2.14.0', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'openshift', image: 'docker-registry.default.svc:5000/openshift/openshift-cli', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'docker', image: 'docker', ttyEnabled: true, command: 'cat'),
		containerTemplate(name: 'ruby', image: 'ruby:2.5.5', ttyEnabled: true, command: 'cat')

    
    ]
) {
    node(label) {
        stage('Init') {
          sh """
            pwd
            env
            find ${HOME}
            df -h
            """
        }
        stage('Checkout') {
            container('jnlp') {
                checkout scm
                COMMIT_HEAD_TAG = sh(returnStdout: true, script: "git tag -l --points-at HEAD | tail -1").trim()
                echo 'Head Tag:' + COMMIT_HEAD_TAG
                echo 'Branch Name:' + env.BRANCH_NAME
                echo 'Check Branch:'
                if (env.BRANCH_NAME == 'master') {
                    echo 'Executing on Master'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'Execute on Develop'
                } else if (env.BRANCH_NAME == 'release'){
                    echo 'Execute on Release'
                } else if (env.BRANCH_NAME.contains('prod') && COMMIT_HEAD_TAG.contains('prod')){
                    echo 'Execute on "prod" specific tag.'
                }
                else {
                    echo 'Non Branch or Tag execution.'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        stage('Unit Test') {
            container('ruby') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'No Unit Test'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'No Unit Test'
                } else if (env.BRANCH_NAME == 'release'){
                    echo 'Execute Unit Tests'
                    sh 'ruby -v'
                } else if (env.BRANCH_NAME.contains('prod') && COMMIT_HEAD_TAG.contains('prod')){
                    echo 'No Unit Test'
                }
                else {
                    echo 'No Unit Test'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        stage('Checkmarx') {
            container('jnlp') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'No Checkmarx'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'No Checkmarx'
                } else if (env.BRANCH_NAME == 'release'){
                    echo 'Execute Checkmarx'
                } else if (env.BRANCH_NAME.contains('prod') && COMMIT_HEAD_TAG.contains('prod')){
                    echo 'No Checkmarx'
                }
                else {
                    echo 'No Checkmarx'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        stage('Secrets') {
            container('openshift') {
                echo 'Probably need to build secrets in all environments.'
            }
        }

        stage('Build Image') {
            container('openshift') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'No Build'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'Build on Develop'
                    echo 'Image Tag:' + DEVELOP_IMAGE_TAG
                    sh 'oc whoami'
                } else if (env.BRANCH_NAME == 'release'){
                    echo 'Build on Release'
                    echo 'Image Tag:' + RELEASE_IMAGE_TAG
                    sh 'oc whoami'
                } else if (env.BRANCH_NAME.contains('prod') && COMMIT_HEAD_TAG.contains('prod')){
                    echo 'No Build'
                }
                else {
                    echo 'No Build. Should we name feature, or leave open?'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        stage('Deploy Image') {
            container('helm') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'Master Deploy'

                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'Develop Deploy'
                    echo 'Image Used:' + DEVELOP_IMAGE_TAG
                    sh 'helm --help'
                } else if (env.BRANCH_NAME == 'release'){
                    //Image was just rebuilt again.
                    echo 'Release Deploy'
                    echo 'Image Tag:' + RELEASE_IMAGE_TAG
                    sh 'helm --help'
                } else if (env.BRANCH_NAME.contains('prod') && COMMIT_HEAD_TAG.contains('prod')){
                    echo 'Deploy on GitTag'
                    echo 'Image Tag:' + COMMIT_HEAD_TAG
                    sh 'helm --help'
                else {
                    echo 'No Deploy. Should we name feature, or leave open?'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        
    }
}
