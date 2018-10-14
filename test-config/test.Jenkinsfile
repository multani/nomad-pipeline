// vim:ft=groovy

def label = "my-job-${UUID.randomUUID().toString()}"
echo "Using label ${label}"

nomadJobTemplate(
    label: label,
    taskGroups: [
      taskTemplate(
        name: 'jnlp',
        image: 'jenkins/jnlp-slave:alpine',
        resourcesMemory: 2048,
        resourcesCPU: 1000,
        envVars: [
            envVar(key: 'test', value: 'foobar'),
            envVar(key: 'test123', value: 'foobar456qsd'),
        ]
    )
  ]
)
{
    echo "Nomad job created"
    node(label)
    {
        echo "In node {}"
        sh "env | sort"
        sh "sleep 1"
    }
}
