/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package info.multani.jenkins.plugins.nomad;

import static info.multani.jenkins.plugins.nomad.NomadCloud.*;
import com.hashicorp.nomad.apimodel.Job;
import com.hashicorp.nomad.apimodel.Resources;
import com.hashicorp.nomad.apimodel.RestartPolicy;
import com.hashicorp.nomad.apimodel.Task;
import com.hashicorp.nomad.apimodel.TaskArtifact;
import com.hashicorp.nomad.apimodel.TaskGroup;
import static hudson.Util.replaceMacro;
import info.multani.jenkins.plugins.nomad.model.EnvVar;
import info.multani.jenkins.plugins.nomad.pipeline.NomadJobTemplateStepExecution;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.lang.StringUtils;

/**
 * Helper class to build Jobs from JobTemplates
 *
 * @author Carlos Sanchez
 * @since
 *
 */
public class NomadJobTemplateBuilder {

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateBuilder.class.getName());

    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(NomadJobTemplateStepExecution.class.getName() + ".defaultImage", "jenkins/jnlp-slave:alpine");

    private static final List<String> DEFAULT_JNLP_ARGUMENTS = Arrays.asList("${computer.jnlpmac}", "${computer.name}");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    private NomadJobTemplate template;

    public NomadJobTemplateBuilder(NomadJobTemplate template) {
        this.template = template;
    }

    /**
     * Create a Job object from a JobTemplate
     *
     * @param slave
     * @return
     */
    public Job build(NomadSlave slave) {
        ArrayList<TaskGroup> taskGroups = new ArrayList<>();

        for (TaskTemplate taskGroupTemplate : template.getTaskGroups()) {
            taskGroups.add(
                    createContainer(slave, taskGroupTemplate, template.getEnvVars())
            );
        }

        if (taskGroups.isEmpty()) {
            TaskTemplate taskGroupTemplate = new TaskTemplate(JNLP_NAME, DEFAULT_JNLP_IMAGE);
            taskGroupTemplate.setArgs(DEFAULT_JNLP_ARGUMENTS);
            taskGroups.add(
                    createContainer(slave, taskGroupTemplate, template.getEnvVars())
            );
        }

        Job job = new Job();
        job.setId(slave.getNodeName());
        job.setName(slave.getNodeName());
        job.setRegion("global"); // TODO
        job.addDatacenters("dc1"); // TODO
        job.setType("batch");
        job.setTaskGroups(taskGroups);
        
        return job;

    }

    private TaskGroup createContainer(NomadSlave slave, TaskTemplate taskGroupTemplate, Collection<EnvVar> globalEnvVars) {
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();
        NomadCloud cloud = slave.getNomadCloud();
        String url = cloud.getJenkinsUrlOrDie();
        
        // Add some default env vars for Jenkins
        env.put("JENKINS_SECRET", slave.getComputer().getJnlpMac());
        env.put("JENKINS_NAME", slave.getComputer().getName());
        env.put("JNLP_PROTOCOL_OPTS", "");
        env.put("JENKINS_JNLP_URL", url + "/computer/" + slave.getNodeName() + "/slave-agent.jnlp");

        env.put("JENKINS_URL", url);
        if (!StringUtils.isBlank(cloud.getJenkinsTunnel())) {
            env.put("JENKINS_TUNNEL", cloud.getJenkinsTunnel());
        }

        env.put("HOME", taskGroupTemplate.getWorkingDir());

        Map<String, String> envVars = new HashMap<>();

        env.entrySet().forEach(item
                -> envVars.put(item.getKey(), item.getValue())
        );

        if (globalEnvVars != null) {
            globalEnvVars.forEach(item
                    -> envVars.put(item.getKey(), item.getValue())
            );
        }

        if (taskGroupTemplate.getEnvVars() != null) {
            taskGroupTemplate.getEnvVars().forEach(item
                    -> envVars.put(item.getKey(), item.getValue())
            );
        }

        List<String> arguments = taskGroupTemplate
                .getArgs().stream()
                .map(e -> e.replaceAll(JNLPMAC_REF, slave.getComputer().getJnlpMac())
                        .replaceAll(NAME_REF, slave.getComputer().getName())
                )
                .collect(Collectors.toList());

        TaskGroup taskGroup = new TaskGroup();
        taskGroup.setName(substituteEnv(taskGroupTemplate.getName()));
        
        RestartPolicy restartPolicy = new RestartPolicy()
                .setMode("fail")
                .setAttempts(0);
        taskGroup.setRestartPolicy(restartPolicy);

        Task task = new Task();
        task.setName(substituteEnv(taskGroupTemplate.getName()));
        task.setDriver("docker");
        task.addConfig("image", substituteEnv(taskGroupTemplate.getImage()));
        task.addConfig("command", substituteEnv(taskGroupTemplate.getCommand()));
        task.addConfig("args", arguments);
        task.addConfig("network_mode", "host");
 
        // TODO: download the artifact only if needed
        task.addArtifacts(
                new TaskArtifact()
                        .setGetterSource(cloud.getSlaveUrl())
                        .setGetterOptions(null)
                        .setRelativeDest("/local/")
        );

        task.setEnv(envVars);

        Resources resources = new Resources()
                .setCpu(taskGroupTemplate.getResourcesCPU())
                .setMemoryMb(taskGroupTemplate.getResourceMemory());
        task.setResources(resources);

        taskGroup.addTasks(task);

        return taskGroup;
    }

    public static String substituteEnv(String s) {
        return replaceMacro(s, System.getenv());
    }
}
