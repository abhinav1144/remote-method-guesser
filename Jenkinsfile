pipeline {
  agent any 
  tools {
    maven 'Maven'
  }
  stages {
    stage ('Initialize') {
      steps {
        sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
            ''' 
      }
    }
    
    stage ('Check-Git-Secrets') {
      steps {
        sh 'rm trufflehog || true'
        sh 'docker run gesellix/trufflehog --json https://github.com/abhinav1144/remote-method-guesser.git > trufflehog'
        sh 'cat trufflehog'
      }
    }
    
    stage ('Source Composition Analysis') {
      steps {
         sh 'rm owasp* || true'
         sh 'wget "https://raw.githubusercontent.com/abhinav1144/remote-method-guesser/master/owasp-dependency-check.sh" '
         sh 'chmod +x owasp-dependency-check.sh'
         sh 'bash owasp-dependency-check.sh'
         sh 'cat /var/lib/jenkins/OWASP-Dependency-Check/reports/dependency-check-report.xml'
        
      }
    }
    
    stage ('SAST') {
      steps {
        withSonarQubeEnv('sonar') {
          sh 'mvn sonar:sonar'
          sh 'cat target/sonar/report-task.txt'
        }
      }
    }
    
    stage ('Build') {
      steps {
      sh 'mvn clean package'
       }
    }
    
    stage ('Deploy-To-Tomcat') {
            steps {
           sshagent(['ee3c29f7-1033-459f-a644-0d3c22982d78']) {
                sh 'scp -o StrictHostKeyChecking=no target/*.war ubuntu@54.234.60.48:/home/ubuntu/project/apache-tomcat-9.0.62/webapps/remote-method-guesser.war'
              }      
           }       
    }
    
    
    stage ('DAST') {
      steps {
        sshagent(['ee3c29f7-1033-459f-a644-0d3c22982d78']) {
         sh 'ssh -o  StrictHostKeyChecking=no ubuntu@54.234.60.48 "sudo docker run -t owasp/zap2docker-stable zap-baseline.py -t http://54.234.60.48:8080/remote-method-guesser/" || true'
        }
      }
    }
    
  }
}
