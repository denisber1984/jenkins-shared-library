// vars/myLib.groovy
def sayHello(String name = 'World') {
    echo "Hello, ${name}!"
}
def buildDockerImage(nexusUrl, nexusRepo, commitId, buildNumber) {
    docker.build("${nexusUrl}/repository/${nexusRepo}:${commitId}-${buildNumber}", "-f polybot/Dockerfile polybot")
}

def pushDockerImage(nexusUrl, nexusRepo, commitId, buildNumber) {
    docker.withRegistry("http://${nexusUrl}", 'nexus-credentials') {
        docker.image("${nexusUrl}/repository/${nexusRepo}:${commitId}-${buildNumber}").push()
        docker.image("${nexusUrl}/repository/${nexusRepo}:${commitId}-${buildNumber}").push('latest')
    }
}

def runUnitTests(nexusUrl, nexusRepo, commitId, buildNumber) {
    docker.image("${nexusUrl}/repository/${nexusRepo}:${commitId}-${buildNumber}").inside {
        sh 'python3 -m pytest --junitxml=results.xml tests/test.py'
    }
    junit 'results.xml'
}

def snykSecurityScan(nexusUrl, nexusRepo, commitId, buildNumber, snykToken) {
    sh "snyk auth ${snykToken}"
    sh "snyk container test ${nexusUrl}/repository/${nexusRepo}:${commitId}-${buildNumber} --severity-threshold=high --file=polybot/Dockerfile --exclude-base-image-vulns --policy-path=./snyk-ignore.json"
}

def deployApplication(nexusUrl, nexusRepo, commitId, buildNumber, nexusCredentialsPsw, targetServer) {
    sh """
        ssh -o StrictHostKeyChecking=no ec2-user@${targetServer} '
            docker login -u admin -p ${nexusCredentialsPsw} http://${nexusUrl}
            docker pull ${nexusUrl}/repository/${nexusRepo}:latest
            docker stop mypolybot-app || true
            docker rm mypolybot-app || true
            docker run -d --name mypolybot-app -p 80:80 ${nexusUrl}/repository/${nexusRepo}:latest
        '
    """
}
