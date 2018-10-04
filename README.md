Jenkins plugin to run Jenkins job using the Nomad scheduler and configuring Nomad jobs from Jenkins pipelines.


How to build?
=============

```
mvn package
```

How to test?
============

Easy way:

* [download Nomad](https://releases.hashicorp.com/nomad/) and run `nomad agent
  -dev` on your machine (you need Docker also).
* then run: `mvn hpi:run -Djetty.consoleForceReload=false -Djava.util.logging.config.file=debug-plugin-logging.properties`
* wait for Jenkins to start and connect on http://localhost:8080/jenkins/
* create a new pipeline job with the code from `test-config/test.Jenkinsfile`
* configure Jenkins, add a new Cloud Provider and set the Server URL to http://localhost:4646
* execute the job!
