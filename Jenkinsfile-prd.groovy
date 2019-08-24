def label = "jenkins-slave-${UUID.randomUUID().toString()}"
def COMMIT_HEAD_TAG = ""
def DEVELOP_IMAGE_TAG = "test"
def RELEASE_IMAGE_TAG = "release"
def TAG_FILTER="prod"

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
                } 
                else {
                    echo 'Non Branch or Tag execution.'
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
                }  else {
                    echo 'No Deploy. Should we name feature, or leave open?'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        
    }
}
