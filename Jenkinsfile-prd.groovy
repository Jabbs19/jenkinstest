def label = "jenkins-slave-${UUID.randomUUID().toString()}"
//Production Variables
def PRD_OS_PROJECT = "psd-helpline-prd"
def PRD_IMAGE_TAG = "production"
def PRD_HELM_VALUES_LOC = "helm/helpline/values-prd.yaml"

//Release Variables
def REL_OS_PROJECT = "psd-helpline-rel"
def REL_IMAGE_TAG = "release"
def REL_HELM_VALUES_LOC = "helm/helpline/values-rel.yaml"

//Develop Variables
def DEV_OS_PROJECT = "psd-helpline-tst"
def DEV_IMAGE_TAG = "test"
def DEV_HELM_VALUES_LOC = "helm/helpline/values-tst.yaml"

//Stage Variables
def STG_OS_PROJECT = "psd-helpline-stg"
def STG_IMAGE_TAG = "feature"
def STG_HELM_VALUES_LOC = "helm/helpline/values.yaml"

//HotFix Variables (Not all needed unless deploying.)
//def HOT_OS_PROJECT = "psd-helpline-hot"
def HOT_IMAGE_TAG = "hotfix"
//def HOT_HELM_VALUES_LOC = "helm/helpline/values-hot.yaml"

//Git Variables
def GIT_REL_TAG_FILTER="release-mj-"
def GIT_HOT_TAG_FILTER = "hotfix-mj-"    
def GIT_COMMIT_HEAD_TAG = ""            //Git Tag at HEAD of Branch
def GIT_SHORT_COMMIT = ""               //Git Short Commit (e.g. 1574ga2)
def GIT_PARSED_APP_VERSION = ""         //Parsed AppVersion (after stripping Tag Identifiers)

//Image Variables
def IMAGE_NAME = "helpline"         //
def IMAGE_BUILD_OS_PROJECT = ""     //OpenShift Project (aka Repo) (e.g. psd-helpline-tst)
def IMAGE_BUILD_TAG = ""            //Image Tag created on Build (e.g. :test
def IMAGE_DEPLOY_TAG = ""           //Image Tag used in Deployment (e.g. :test, or :v1.4a for some environments.)


//Helm and Tiller Stuff
def HELM_TILLER_NAMESPACE = 'ocp-tiller'
def HELM_CHARTNAME = 'helpline'
def HELM_CHART_LOCATION = 'helm/helpline'
def HELM_TGZ_DEST_DIR = 'helm'
def HELM_RELEASE_NAME_FINAL = ""    //Final name of Chart Release (e.g. psd-helpline-tst)
def HELM_YAML_LOCATION_FINAL = ""   //Final YAML location (set from env variables above)
def HELM_TGZ_PACKAGE_FINAL = ""     //Final full path for TGZ file (e.g. helm/helpline-0.4.0.tgz)
def HELM_CHART_VERSION = ""         //Global variables used for current/set Chart Version (e.g. 0.4.0)
def HELM_APP_VERSION_FINAL = ""     //Used when setting the Chart.yaml appVersion to either Tag Version or Branch-ShortCommit

//Generic or Operational Variables
def DEPLOY_OS_PROJECT = ""          //Project that the image will be pulled from for deployment.
def RELEASE_TYPE = ""               //Branch name, with tags mapped to an easy-to-read name (e.g. release, hotfix)



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
                GIT_COMMIT_HEAD_TAG = sh(returnStdout: true, script: "git tag -l --points-at HEAD | tail -1").trim()
                GIT_SHORT_COMMIT = sh(returnStdout: true, script: "git rev-parse --short HEAD | tail -1").trim()
                //Parse "App Version"
                if (GIT_COMMIT_HEAD_TAG != "") {
                    if (GIT_COMMIT_HEAD_TAG.substring(0,GIT_REL_TAG_FILTER.length()) == GIT_REL_TAG_FILTER) {
                        GIT_PARSED_APP_VERSION = GIT_COMMIT_HEAD_TAG.substring(GIT_REL_TAG_FILTER.length(),GIT_COMMIT_HEAD_TAG.length())
                    } else if (GIT_COMMIT_HEAD_TAG.substring(0,GIT_HOT_TAG_FILTER.length()) == GIT_HOT_TAG_FILTER) {
                        GIT_PARSED_APP_VERSION = GIT_COMMIT_HEAD_TAG.substring(GIT_HOT_TAG_FILTER.length(),GIT_COMMIT_HEAD_TAG.length())
                    } else {
                        GIT_PARSED_APP_VERSION = ""
                    }
                    echo 'App Version Parsing Complete (if needed): ' + GIT_PARSED_APP_VERSION
                }

            }
            stage('Set Branch Variables') {
                echo 'Set Branch Variables Branch:'
                RELEASE_TYPE = 'master'
                DEPLOY_OS_PROJECT = PRD_OS_PROJECT
                IMAGE_DEPLOY_TAG = GIT_PARSED_APP_VERSION
                HELM_RELEASE_NAME_FINAL = HELM_CHARTNAME + '-prd'
                HELM_YAML_LOCATION_FINAL = PRD_HELM_VALUES_LOC
                //Tags for Master Release Checks.  Could add more filters, sig checks, etc.
                if (GIT_COMMIT_HEAD_TAG == "") {
                    error "No Git Version at Commit Head"
                } else {
                    HELM_APP_VERSION_FINAL = GIT_PARSED_APP_VERSION
                }

                //Print Variables for Logging.
                echo 'Display Some Variables for Logging'
                echo 'Git Tag: ' + GIT_COMMIT_HEAD_TAG
                echo 'Commit Short Hash: ' + GIT_SHORT_COMMIT
                echo 'Release Type: ' + RELEASE_TYPE
            }
            stage('Image Management') {
                def SOURCE_TAG = ""
                def DEST_TAG = ""
               
                //"Pre-tags" image for deployments.
                SOURCE_TAG = REL_OS_PROJECT + '/' + IMAGE_NAME + ':' + GIT_PARSED_APP_VERSION
                DEST_TAG = PRD_OS_PROJECT + '/' + IMAGE_NAME + ':' + GIT_PARSED_APP_VERSION
                sh "echo 'oc tag ${SOURCE_TAG} ${DEST_TAG}'"

                SOURCE_TAG = REL_OS_PROJECT + '/' + IMAGE_NAME + ':' + GIT_PARSED_APP_VERSION
                DEST_TAG = PRD_OS_PROJECT + '/' + IMAGE_NAME + ':' + PRD_IMAGE_TAG
                sh "echo 'oc tag ${SOURCE_TAG} ${DEST_TAG}'"
            }
            stage ('Create Application Secrets') {
                //Create whatever secrets are needed for application deployment and running.
                //Nothing needed for HotFix since it won't be deployed, just leave whats there for regular releases.
                    
                // Will need to create uniqu credentials for all environments that have unique values.
                // e.g. dev-rails-master-key, stg-rails-master-key, prd-rails-master-key, rel-rails-master-key

                /*withCredentials([
                    string(credentialsId: 'psd-rails-master-key', variable: 'RAILS_MASTER_KEY'),
                    string(credentialsId: 'psd-helpline-database-password-tst', variable: 'DATABASE_PWD'),
                    string(credentialsId: 'psd-helpline-keybase', variable: 'KEYBASE')
                    ]) {
                        
                        //Put all of the If else in here.
                        }
                */
                
                //Fake Data:
                def RAILS_MASTER_KEY = 'rails-master-key-14njfgaht3q3tj'
                def DATABASE_PWD = 'hereismydbapassword'
                def KEYBASE = '75q05704319750914375093475070134901'

                //Application Secrets
                sh """
                echo 'Deleting and Creating Application Secrets in ${DEPLOY_OS_PROJECT}'
                echo 'oc project ${DEPLOY_OS_PROJECT}'
                echo 'oc delete secret "bt-helpline-secret" -n ${DEPLOY_OS_PROJECT}'
                echo 'oc create secret generic bt-helpline-secret --from-literal=database-password="${DATABASE_PWD}" --from-literal=keybase="${KEYBASE}"'
                """ 
                } 
            }
        }
        //What environments will Deploy? (Can always filter further later.)
   
        container('helm') {
            stage('Initialize Helm') {
                echo 'Initialize Helm Client and Check tiller access'
                
                sh """
                echo 'helm init --client-only'
                echo 'helm version --tiller-namespace ${HELM_TILLER_NAMESPACE}'
                """
            }
            stage('Helm Lint Tests') {
                echo 'Run Helm Lint Test'

                sh """
                echo 'helm lint ${HELM_CHART_LOCATION}'
                """
            }
            stage('Helm Chart.yaml Processing and Versioning') {
                //Don't have to do AppVersion for all branches.
                //Version the AppVersion.  Just cosmetic, so that Helm will display what's currently deployed.
                def chartYaml = readYaml file: 'helm/helpline/Chart.yaml'
                def appVersion = chartYaml.appVersion.toString()
                HELM_CHART_VERSION = chartYaml.version.toString()

                echo 'Current Helm AppVersion in File: ' + appVersion
                echo 'Current Helm Chart Version in File: ' + HELM_CHART_VERSION    //Used for Master                            
                echo 'New Helm AppVersion for Deployment: ' + HELM_APP_VERSION_FINAL
                //Overwrite value.
                chartYaml.appVersion = HELM_APP_VERSION_FINAL
                //Overwrite file
                sh "rm -f helm/helpline/Chart.yaml"
                writeYaml file: "helm/helpline/Chart.yaml", data: chartYaml
            }
            stage('Helm Values Versioning') {
                //Modify Image Tag in deployment (this will just have the value hardcoded in manfiests)
                //Also cosmetic, as the --set image will save these values historically as well.
                def valuesYaml = readYaml file: 'helm/helpline/values.yaml'
                def currentImage = valuesYaml.images.apiTag.toString()
                echo 'Current Helm Image Tag in File: '+currentImage
                echo 'New Helm Image Tag for Deployment: ' + IMAGE_DEPLOY_TAG
                //Overwrite Values
                valuesYaml.images.apiTag = IMAGE_DEPLOY_TAG
                //Overwrite file
                sh "rm -f helm/helpline/values.yaml"
                writeYaml file: "helm/helpline/values.yaml", data: valuesYaml
            }
            stage('Package Helm and Push to ChartMuseum'){
                sh """
                echo "helm package ${HELM_CHART_LOCATION} -d ${HELM_TGZ_DEST_DIR}"
                """
                HELM_TGZ_PACKAGE_FINAL = HELM_CHARTNAME + '-' + HELM_CHART_VERSION + '.tgz'
                echo 'Helm .tgz Package: ' + HELM_TGZ_PACKAGE_FINAL

                sh """
                echo 'curl -u admin:admin -k --data-binary ${HELM_TGZ_PACKAGE_FINAL} http://chartmuseum-chartmuseum.chartmuseum:8080/api/charts'
                """
            }
            stage ('Deploy with Helm') {
                //Could make this a "configuration step, and just set all remaining values before a global "deploy"
                echo 'Deploy with helm to ' + DEPLOY_OS_PROJECT + ' with tag:' + IMAGE_DEPLOY_TAG

                //Set Full path of TGZ
                def TGZ_PATH = HELM_TGZ_DEST_DIR + '/' + HELM_TGZ_PACKAGE_FINAL 
                

                sh """
                echo 'helm version --tiller-namespace ${HELM_TILLER_NAMESPACE} \
                helm upgrade --install ${HELM_RELEASE_NAME_FINAL} \
                ${TGZ_PATH} \
                --namespace ${DEPLOY_OS_PROJECT} \
                --tiller-namespace ${HELM_TILLER_NAMESPACE} \
                -f ${HELM_YAML_LOCATION_FINAL} \
                --set image.apiRepository=${DEPLOY_OS_PROJECT}
                --set image.apiTag=${IMAGE_DEPLOY_TAG} \
                '
                """
                sh 'helm --help'
            
            }
        }
        stage('Post-Deploy Verification') {
            container('jnlp') {
                    echo 'Post Deploy Verfication for Master'
                    sh """
                    echo 'oc -n ${DEPLOY_OS_PROJECT} rollout status deployment api'
                    """
            }
        }    
        stage('Post-Deploy Jobs') {
            //Could be run by Helm as Jobs, or by OpenShift using 'oc apply'
            container('jnlp') {
                    echo 'Post Deploy Jobs for Master'
                    sh """
                    echo 'oc apply -f k8/post-upgrade-job.yml --namespace ${DEPLOY_OS_PROJECT}'
                    """
            }
        }                
        stage('JIRA') {
            container('jnlp') {
                    echo 'JIRA Update of Issue/Ticket that its available in Production'
            }
        }
    }
}
