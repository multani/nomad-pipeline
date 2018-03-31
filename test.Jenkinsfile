// vim:ft=groovy

def label = "mypod-${UUID.randomUUID().toString()}"
NomadJobTemplate(label: label, containers: [
    containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine')
  ]) {
    node(label) {
        stage('test') {
            echo "yopla"
        }
    }
}
