pipeline {  
    environment {
         imagename = "portfolio"
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
    }

}