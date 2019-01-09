pipeline {  
    environment {
         componentName = "portfolio"
         imagename = "${componentName}:${BUILD_NUMBER}"
     }

    agent any
    stages {
       stage('Build') { 
          steps {
              sh 'mvn clean package' 
          }
       }  
       stage('Deliver') {
            steps {
                script {
                    docker.build imagename
                }
                sh '/push2dockerhub.sh $imagename'
            }
       }
       stage('Deploy') {
            steps {
                script {
                    step([$class: 'UCDeployPublisher',
                        component: [
                            componentName: componentName,
                            componentTag: '',
                            delivery: [
                                $class: 'Push',
                                baseDir: pwd(),
                                fileExcludePatterns: '',
                                fileIncludePatterns: 'manifests/deploy.yaml',
                                pushDescription: '',
                                pushIncremental: false,
                                pushProperties: '',
                                pushVersion: '$BUILD_NUMBER'
                            ]
                         ],
                         deploy: [
                             createSnapshot: [
                                 deployWithSnapshot: true,
                                 snapshotName: "${componentName}-$BUILD_NUMBER"
                             ],
                             deployApp: componentName,
                             deployDesc: 'Requested from Jenkins',
                             deployEnv: 'DEV 1',
                             deployOnlyChanged: true,
                             deployProc: 'Deploy',
                             deployReqProps: '',
                             deployVersions: imagename
                         ],
                         siteName: 'master1'
                     ])
                }
            }
       }
    }
}

