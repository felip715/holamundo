pipeline{
    agent {
         label 'master'
    }
    /*
    tools {
         maven 'maven 3.6'
         jdk 'java'
    }
    */
    environment {
        // This can be nexus3 or nexus2
        NEXUS_VERSION = "nexus3"
        // This can be http or https
        NEXUS_PROTOCOL = "http"
        // Where your Nexus is running. 'nexus-3' is defined in the docker-compose file
        NEXUS_URL = "127.0.0.1:8081"
        // Repository where we will upload the artifact
        NEXUS_REPOSITORY = "maven-releases"
        // Jenkins credential id to authenticate to Nexus OSS
        NEXUS_CREDENTIAL_ID = "nexus_jenkins"
        
        // Workfolder
        //WORKFOLDER = "/usr/jenkins/node_agent/workspace"
    }

    stages{
        stage('Checkout'){
            steps{
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[credentialsId: 'github_ssh', url: 'git@github.com:felip715/holamundo.git']]])
            }
        }
        stage('Build artifact'){
            agent {
                label 'host_agent_P2'
            }
            steps{
                sh '''
                    #mvn clean install
                    echo $JAVA_HOME
                    pwd
                    hostname
                    whoami
                    /home/jenkins/apache-maven-3.6.3/bin/mvn package
                '''
            }
        }
        stage('Upload to nexus'){
            agent {
                label 'master'
            }
            steps{
                script{
                    pom = readMavenPom(file: 'pom.xml')
                    filesByGlob = findFiles(glob: "target/*.${pom.packaging}")
                    echo "${filesByGlob[0].name} ${filesByGlob[0].path} ${filesByGlob[0].directory} ${filesByGlob[0].length} ${filesByGlob[0].lastModified}"
                    artifactPath = filesByGlob[0].path
                    artifactExists = fileExists artifactPath

                    if(artifactExists) {
                        echo "*** File: ${artifactPath}, group: ${pom.groupId}, packaging: ${pom.packaging}, version ${pom.version}";
                        
                        nexusArtifactUploader(
                            nexusVersion: NEXUS_VERSION,
                            protocol: NEXUS_PROTOCOL,
                            nexusUrl: NEXUS_URL,
                            groupId: pom.groupId,
                            version: pom.version,
                            repository: NEXUS_REPOSITORY,
                            credentialsId: NEXUS_CREDENTIAL_ID,
                            artifacts: [
                                // Artifact generated such as .jar, .ear and .war files.
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: artifactPath,
                                type: pom.packaging],

                                // Lets upload the pom.xml file for additional information for Transitive dependencies
                                [artifactId: pom.artifactId,
                                classifier: '',
                                file: "pom.xml",
                                type: "pom"]
                            ]
                        );

                    } else {
                        error "*** File: ${artifactPath}, could not be found";
                    }
                }

            }
        } //fin stage upload
        
        
        stage("Post") {
            agent {
                label 'maven'
            }
            steps {
                sh '''
                    pwd
                    echo "Clean up workfolder"
                    rm -Rf *
                '''
            }
        } //fin stage post
        
    }
}
