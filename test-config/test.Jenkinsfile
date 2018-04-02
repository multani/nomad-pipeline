// vim:ft=groovy

def label = "mypod-${UUID.randomUUID().toString()}"
echo "Using label ${label}"
NomadJobTemplate(label: label, containers: [
    taskGroupTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:alpine', resourcesMemory: 2048, resourcesCPU: 1000)
  ]) {
    echo "Nomad job created"
    node(label) {
        echo "in node{}"
        sh "env | sort"
        sh "sleep 10"
    }
}
