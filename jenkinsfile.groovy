def label = "jenkins-slave-${UUID.randomUUID().toString()}"
//Production Variables
def OS_PRD_PROJECT = "psd-helpline-prd"
def PRD_IMAGE_TAG = "production"
def PRD_HELM_VALUES_LOC = "helm/helpline/values-prd.yaml"

//Release Variables
def OS_REL_PROJECT = "psd-helpline-rel"
def RELEASE_IMAGE_TAG = "release"
def RELEASE_HELM_VALUES_LOC = "helm/helpline/values-rel.yaml"

//Develop Variables
def OS_DEV_PROJECT = "psd-helpline-tst"
def DEVELOP_IMAGE_TAG = "test"
def DEVELOP_HELM_VALUES_LOC = "helm/helpline/values-tst.yaml"

//Stage Variables
def OS_STG_PROJECT = "psd-helpline-stg"
def STAGE_IMAGE_TAG = "feature"
def STAGE_HELM_VALUES_LOC = "helm/helpline/values.yaml"

//HotFix Variables
def OS_HOTFIX_PROJECT = "psd-helpline-stg"
def HOTFIX_IMAGE_TAG = "hotfix"
//Uses same helm as PRD.

//Git Variables
def TAG_FILTER="prod-"
def HOTFIX_TAG_FILTER = "hotfix-"

//Image Variables
def IMAGE_NAME = "helpline"

//Helm and Tiller Stuff
def TILLER_NAMESPACE = 'ocp-tiller'
def CHARTNAME_PREFIX = 'helpline'
def CHART_LOCATION = 'helm/helpline'
def TGZ_DEST_DIR = 'helm'

//Global Variables
def BUILD_IMAGE_TAG = ""
def BUILD_IMAGE_NAME = ""
def DEPLOY_IMAGE_TAG = ""
def DEPLOY_IMAGE_NAME = ""
def COMMIT_HEAD_TAG = ""
def RELEASE_NAME_FINAL = ""
def VALUES_YAML_FINAL = ""
def APP_VERSION = ""
def OS_BUILD_PROJECT = ""
def OS_DEPLOY_PROJECT = ""

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
        container('jnlp') {
            stage('SCM Checkout') {
                checkout scm
                COMMIT_HEAD_TAG = sh(returnStdout: true, script: "git tag -l --points-at HEAD | tail -1").trim()
                echo 'Commit Head Tag:' + COMMIT_HEAD_TAG
                echo 'Current Branch Name:' + env.BRANCH_NAME
            }
            stage('Set Branch Variables') {
                echo 'Set Branch Variables Branch:'
                if (env.BRANCH_NAME == 'master') {
                    echo 'Executing on Master'
                    OS_DEPLOY_PROJECT = OS_PRD_PROJECT
                    DEPLOY_IMAGE_TAG = COMMIT_HEAD_TAG
                    DEPLOY_IMAGE_NAME = OS_DEPLOY_PROJECT + '/' + IMAGE_NAME + ':' + COMMIT_HEAD_TAG
                    RELEASE_NAME_FINAL = CHARTNAME_PREFIX + '-prd'
                    VALUES_YAML_FINAL = PRD_HELM_VALUES_LOC
                    //Tags for Master Release Checks.  Could add more filters, sig checks, etc.
                    if (COMMIT_HEAD_TAG == "") {
                        error "No Git Version at Master Head"
                    } else {
                        echo COMMIT_HEAD_TAG 
                        //Get App Version for Regular Production Release Filter
                        if (COMMIT_HEAD_TAG.substring(0,TAG_FILTER.length()) == TAG_FILTER) {
                            APP_VERSION = COMMIT_HEAD_TAG.substring(TAG_FILTER.length(),COMMIT_HEAD_TAG.length())
                        } else if (COMMIT_HEAD_TAG.substring(0,HOTFIX_TAG_FILTER.length()) == HOTFIX_TAG_FILTER) {
                            APP_VERSION = COMMIT_HEAD_TAG.substring(HOTFIX_TAG_FILTER.length(),COMMIT_HEAD_TAG.length())
                        } else {
                            APP_VERSION = COMMIT_HEAD_TAG
                        }
                    }
                    echo 'App Version: ' + APP_VERSION
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'Execute on Develop'
                    OS_BUILD_PROJECT = OS_DEV_PROJECT
                    BUILD_IMAGE_TAG = DEVELOP_IMAGE_TAG
                    BUILD_IMAGE_NAME = OS_BUILD_PROJECT + '/' + IMAGE_NAME + ':' + BUILD_IMAGE_TAG
                    OS_DEPLOY_PROJECT = OS_DEV_PROJECT
                    DEPLOY_IMAGE_TAG = DEVELOP_IMAGE_TAG
                    DEPLOY_IMAGE_NAME = OS_DEPLOY_PROJECT + '/' + IMAGE_NAME + ':' + DEPLOY_IMAGE_TAG
                    VALUES_YAML_FINAL = DEVELOP_HELM_VALUES_LOC
                    RELEASE_NAME_FINAL = CHARTNAME_PREFIX + '-dev'

                } else if (env.BRANCH_NAME == 'stage'){
                    echo 'Execute on Stage'
                    OS_BUILD_PROJECT = OS_STG_PROJECT
                    BUILD_IMAGE_TAG = STAGE_IMAGE_TAG
                    BUILD_IMAGE_NAME = OS_BUILD_PROJECT + '/' + IMAGE_NAME + ':' + BUILD_IMAGE_TAG
                    OS_DEPLOY_PROJECT = OS_STG_PROJECT
                    DEPLOY_IMAGE_TAG = STAGE_IMAGE_TAG
                    DEPLOY_IMAGE_NAME = OS_DEPLOY_PROJECT + '/' + IMAGE_NAME + ':' + DEPLOY_IMAGE_TAG
                    VALUES_YAML_FINAL = STAGE_HELM_VALUES_LOC
                    RELEASE_NAME_FINAL = CHARTNAME_PREFIX + '-stg'


                } else if (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER)){
                    echo 'Execute on specific "Release" Tag of:' + TAG_FILTER
                    OS_BUILD_PROJECT = OS_REL_PROJECT
                    BUILD_IMAGE_TAG = RELEASE_IMAGE_TAG
                    BUILD_IMAGE_NAME = OS_BUILD_PROJECT + '/' + IMAGE_NAME + ':' + BUILD_IMAGE_TAG
                    OS_DEPLOY_PROJECT = OS_REL_PROJECT
                    DEPLOY_IMAGE_TAG = COMMIT_HEAD_TAG
                    DEPLOY_IMAGE_NAME = OS_DEPLOY_PROJECT + '/' + IMAGE_NAME + ':' + DEPLOY_IMAGE_TAG
                    VALUES_YAML_FINAL = RELEASE_HELM_VALUES_LOC
                    RELEASE_NAME_FINAL = CHARTNAME_PREFIX + '-rel'

                } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)){
                    echo 'Execute on specific "HotFix" Tag of:' + HOTFIX_TAG_FILTER
                    OS_BUILD_PROJECT = OS_REL_PROJECT
                    BUILD_IMAGE_TAG = HOTFIX_IMAGE_TAG
                    BUILD_IMAGE_NAME = OS_BUILD_PROJECT + '/' + IMAGE_NAME + ':' + BUILD_IMAGE_TAG
                    OS_DEPLOY_PROJECT = OS_PRD_PROJECT
                    DEPLOY_IMAGE_TAG = COMMIT_HEAD_TAG
                    DEPLOY_IMAGE_NAME = OS_DEPLOY_PROJECT + '/' + IMAGE_NAME + ':' + DEPLOY_IMAGE_TAG
                    VALUES_YAML_FINAL = PRD_HELM_VALUES_LOC
                    RELEASE_NAME_FINAL = CHARTNAME_PREFIX + '-prd'

                }
                else {
                    echo 'Non Branch or Tag execution.'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
                echo 'OpenShift Project Name: ' + OS_DEPLOY_PROJECT
                sh """
                echo 'Here are some variables:'
                echo '${OS_DEPLOY_PROJECT}'
                """
            }
        }
        stage('Unit Test') {
            container('ruby') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'No Unit Test'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'No Unit Test'
                } else if (env.BRANCH_NAME == 'stage'){
                    echo 'No Unit Test'
                } else if (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER)){
                    echo 'Execute Unit Tests'
                    sh """
                    ruby -v
                    """
                    /* Real Code
                    withCredentials([string(credentialsId: 'psd-rails-master-key', variable: 'RAILS_MASTER_KEY')]) {
                        container('ruby') {
                          sh """
                            bundle install
                    printenv
                            rake db:create RAILS_ENV=test
                            rake db:migrate RAILS_ENV=test
                    printenv
                            RAILS_MASTER_KEY=${RAILS_MASTER_KEY} rake db:test:prepare
                            RAILS_MASTER_KEY=${RAILS_MASTER_KEY} rspec
                            """
                        }
                    }
                    */
                } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)){
                    echo 'No Unit Test'
                } else {
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
                } else if (env.BRANCH_NAME == 'stage'){
                    echo 'No Checkmarx'
                } else if (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER)){
                    echo 'Execute Checkmarx'
                } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)){
                    echo 'No Checkmarx'
                } else {
                    echo 'No Checkmarx'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
        }
        /*
        container('openshift') {
            stage('Create Secrets') {
                if (env.BRANCH_NAME == 'master') {
                    echo '1. Delete All Secrets in PRD. 2. Create Application Secrets in PRD.'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo '1. Delete All Secrets in TST. 2. Create Image build secretsin TST. 3. Create Application Secrets in TST.'
                } else if (env.BRANCH_NAME == 'stage'){
                    echo '1. Delete All Secrets in STG. 2. Create Image build secretsin TST. 3. Create Application Secrets in TST.'
                } else if (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER)){
                    echo '1. Delete All Secrets. 2. Create Image build secrets. 3. Create Application Secrets.'
                } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)){
                    echo 'No Checkmarx'
                } else {
                    echo 'No Checkmarx'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
                echo 'Probably need to build secrets in all environments.'
            /* Real Code Below:
            //Could parameterize secrets to Project Name

            withCredentials([
            string(credentialsId: 'psd-rails-master-key', variable: 'RAILS_MASTER_KEY'),
            string(credentialsId: 'psd-helpline-database-password-tst', variable: 'DATABASE_PWD'),
            string(credentialsId: 'psd-helpline-keybase', variable: 'KEYBASE')
            ]) {
            container('jnlp') {
                sh """
                oc project ${OS_PROJECT_FINAL}
                oc delete secret "bt-helpline-secret-rails"
                oc create secret generic bt-helpline-secret-rails --from-literal=RAILS_MASTER_KEY="${RAILS_MASTER_KEY}"
                oc delete secret "bt-helpline-secret"
                oc create secret generic bt-helpline-secret --from-literal=database-password="${DATABASE_PWD}" --from-literal=keybase="${KEYBASE}"
                """
            }
            
            }
            */
            stage('Build Image') {
                if (env.BRANCH_NAME == 'master') {
                    echo 'No Build'
                } else if (env.BRANCH_NAME == 'develop'){
                    echo 'Build on Develop'
                    //Builds as :test or :develop
                    echo 'Image Tag:' + BUILD_IMAGE_TAG
                    echo 'Full Image Name:' + BUILD_IMAGE_NAME

                    /* Real Code Below
                    // Image tag for "build" will already be set in BuildConfig
                    oc start-build "helpline-build" --from-repo="." --wait=true --follow -n ${OS_BUILD_PROJECT}
                    */
                    sh 'oc whoami'
                } else if (env.BRANCH_NAME == 'stage'){
                    echo 'Build on Feature'
                    //Builds as :feature or :stage
                    echo 'Image Tag:' + BUILD_IMAGE_TAG
                    echo 'Full Image Name: ' + BUILD_IMAGE_NAME
                    /* Real Code Below
                    // Image tag for "build" will already be set in BuildConfig
                    oc start-build "helpline-build" --from-repo="." --wait=true --follow -n ${OS_BUILD_PROJECT}
                    */
                    sh 'oc whoami'
                } else if (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER)){
                    echo 'Build on Release'
                    //Builds as :release 
                    echo 'Image Tag:' + BUILD_IMAGE_TAG
                    echo 'Full Image Name: ' + BUILD_IMAGE_NAME
                    /* Real Code Below
                    // Image tag for "build" will already be set in BuildConfig
                    oc start-build "helpline-build" --from-repo="." --wait=true --follow -n ${OS_BUILD_PROJECT}
                    */
                    sh 'oc whoami'

                    //Tag as :gittag for deployment
                    echo 'Tag Image as COMMIT_HEAD_TAG also'
                    sh "echo 'oc tag ${BUILD_IMAGE_NAME} ${DEPLOY_IMAGE_NAME}'"
                    echo 'Deploy Image Name (Different than Build): ' + DEPLOY_IMAGE_NAME

                    //If we want to "build" as GitTag, we'll need to create a new Build-Config each time, with that GitTag version. Not sure it makes difference. Future idea?
                } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)){
                    echo 'Build on HotFix'
                    //Builds as :hotfix 
                    echo 'Image Tag:' + BUILD_IMAGE_TAG
                    //Will need to either create a build-config on the fly, or create one ahead of time specifically for hotfix.
                    echo 'Full Image Name: ' + BUILD_IMAGE_NAME
                    /* Real Code Below
                    // Image tag for "build" will already be set in BuildConfig
                    oc start-build "helpline-build-hotfix" --from-repo="." --wait=true --follow -n ${OS_BUILD_PROJECT}
                    */
                    sh 'oc whoami'

                    //Tag as :gittag for deployment
                    echo 'Tag Image as Hot Fix App Version also'
                    sh "echo 'oc tag ${BUILD_IMAGE_NAME} ${DEPLOY_IMAGE_NAME}'"
                    echo 'No Deployment to Release environment, just a build of image.'

                    //If we want to "build" as GitTag, we'll need to create a new Build-Config each time, with that GitTag version. Not sure it makes difference. Future idea?
                } else {
                    echo 'No Build.'
                    echo 'Head Tag:' + COMMIT_HEAD_TAG
                    echo 'Branch Name:' + env.BRANCH_NAME
                }
            }
            stage ('Secrets Cleanup') {
                echo 'Delete any secrets that are not needed (maybe build secrets)'

            }
        }
        stage('Deploy Application') {
            container('helm') {
                stage('Initialize Helm') {
                    echo 'Initialize Helm Client and Check tiller access'
                    //sh 'helm init --client-only'
                    //sh 'helm version --tiller-namespace $TILLER_NAMESPACE'
                    }
                stage('Lint Tests') {
                    echo 'Run Helm Lint Test'
                    //sh 'helm lint ${CHART_LOCATION}'
                }
                if (env.BRANCH_NAME == 'master' || (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER))){
                    stage('Get Chartname') {
                        // Read from Chart values.  Get chartVersion
                        //def chartYaml = readYaml file: 'helm/helpline/Chart.yaml'
                        //def chartVersion = chartYaml.version.toString()
                        //if (chartVersion == "") {
                            //Failed to parse chartVersion. Helm package will fail.
                        //    echo 'error: chartversion not found'
                        //}
                        echo 'Get ChartName from Chart.yaml'
                    }
                    stage('Package Helm'){
                        //Pacakge Helm
                        //sh "helm package helm/helpline -d helm"
                        //We know the name of the .tgz will be <chartname>-<chartversion>.tgz
                        //def PACKAGE_NAME = "helpline-${chartVersion}.tgz"
                        //sh """
                        //echo "Package Name: ${PACKAGE_NAME}"
                        //"""
                        echo 'Package helm into .tgz'
                    }
                    stage('Overwrite values.yaml') {
                    // Read from values.yaml:
                        //def valuesYaml = readYaml file: 'helm/helpline/values.yaml'
                    // Overwrite the image tag specified in values.yaml.
                        //valuesYaml.image.tag = 'somevalue'
                        //sh "rm -f helm/helpline/values.yaml"
                        //writeYaml file: "helm/helpline/values.yaml", data: valuesYaml
                        echo 'overwrite values.yaml'
                    }
                    stage('Push to ChartMuseum') {
                        //Push to ChartMuseum
                        //Could put these credentials in Jenkins...and probably change them in ChartMuseum
                        //sh """
                        //curl -u admin:admin -k  \
                        //--data-binary ${PACKAGE_NAME} \
                        //http://chartmuseum-chartmuseum.chartmuseum:8080/api/charts
                        //"""
                        echo 'Push to ChartMuseum'                    
                    }
                }
                stage ('Deploy with Helm') {
                    //Could make this a "configuration step, and just set all remaining values before a global "deploy"
                    if (env.BRANCH_NAME == 'master'){
                        //Set Full path of TGZ
                        //def TGZ_PATH = TGZ_DEST_DIR + '/' + PACKAGE_NAME 
                        
                        /* Real Code Below
                        sh """
                        helm version --tiller-namespace ${TILLER_NAMESPACE} \
                        helm upgrade --install ${RELEASE_NAME_FINAL} \
                        ${TGZ_PATH} \
                        --namespace ${OS_DEPLOY_PROJECT} \
                        --tiller-namespace ${TILLER_NAMESPACE} \
                        -f ${VALUES_YAML_FINAL} \
                        --set image.apiRepository=${OS_DEPLOY_PROJECT}
                        --set image.apiTag=${DEPLOY_IMAGE_TAG}
                        """
                        */
                        
                        echo 'Deploy Production with helm to ' + OS_DEPLOY_PROJECT + ' with tag:' + DEPLOY_IMAGE_NAME
                        sh 'helm --help'
                    } else if (env.BRANCH_NAME.contains(HOTFIX_TAG_FILTER) && COMMIT_HEAD_TAG.contains(HOTFIX_TAG_FILTER)) {
                        //Currently the same as Master Release, but if we want to do something different with hotfixes, we can do it here.
                        //Set Full path of TGZ
                        //def TGZ_PATH = TGZ_DEST_DIR + '/' + PACKAGE_NAME 
                        
                        /* Real Code Below
                        sh """
                        helm version --tiller-namespace ${TILLER_NAMESPACE} \
                        helm upgrade --install ${RELEASE_NAME_FINAL} \
                        ${TGZ_PATH} \
                        --namespace ${OS_DEPLOY_PROJECT} \
                        --tiller-namespace ${TILLER_NAMESPACE} \
                        -f ${VALUES_YAML_FINAL} \
                        --set image.apiRepository=${OS_DEPLOY_PROJECT}
                        --set image.apiTag=${DEPLOY_IMAGE_TAG}
                        """
                        */
                        echo 'Deploy HotFix (Producion) with helm to ' + OS_DEPLOY_PROJECT + ' with tag:' + DEPLOY_IMAGE_NAME
                        sh 'helm --help'
                    } else if (env.BRANCH_NAME == 'develop' || env.BRANCH_NAME == 'stage' || (env.BRANCH_NAME.contains(TAG_FILTER) && COMMIT_HEAD_TAG.contains(TAG_FILTER))){
                        /* Real Code Below
                        sh """
                        helm version --tiller-namespace ${TILLER_NAMESPACE} \
                        helm upgrade --install ${RELEASE_NAME_FINAL} \
                        ${CHART_LOCATION} \
                        --namespace ${OS_DEPLOY_PROJECT} \
                        --tiller-namespace ${TILLER_NAMESPACE} \
                        -f ${VALUES_YAML_FINAL} \
                        --set image.apiRepository=${OS_DEPLOY_PROJECT}
                        --set image.apiTag=${DEPLOY_IMAGE_TAG}
                        """
                        */

                        //Deploy with :test
                        echo 'Deploy with helm to ' + OS_DEPLOY_PROJECT + ' with tag:' + DEPLOY_IMAGE_NAME
                        sh 'helm --help'
                    } else {
                        echo 'No Deploy.'
                        echo 'Head Tag:' + COMMIT_HEAD_TAG
                        echo 'Branch Name:' + env.BRANCH_NAME
                    }
                }
            }
        }
    }
}