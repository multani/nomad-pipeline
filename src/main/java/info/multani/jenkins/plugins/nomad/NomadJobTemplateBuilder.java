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

import com.hashicorp.nomad.apimodel.Job;
import com.hashicorp.nomad.apimodel.RestartPolicy;
import com.hashicorp.nomad.apimodel.Task;
import com.hashicorp.nomad.apimodel.TaskGroup;
import static hudson.Util.replaceMacro;
import info.multani.jenkins.plugins.nomad.model.EnvVar;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

/**
 * Helper class to build Job from JobTemplate
 */
public class NomadJobTemplateBuilder {

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateBuilder.class.getName());

    private final NomadJobTemplate template;

    public NomadJobTemplateBuilder(NomadJobTemplate template) {
        this.template = template;
    }

    public Job build(NomadSlave slave) {
        ArrayList<TaskGroup> taskGroups = new ArrayList<>();

        template.getTaskGroups().forEach((t) -> {
            taskGroups.add(
                    createTaskGroup(slave, t, template.getEnvVars())
            );
        });

        if (taskGroups.isEmpty()) {
            TaskTemplate task = TaskTemplate.defaultTask();
            taskGroups.add(
                    createTaskGroup(slave, task, template.getEnvVars())
            );
        }

        NomadCloud cloud = slave.getNomadCloud();

        Map<String, String> meta = new HashMap<>();
        meta.putAll(cloud.getLabels());
        meta.putAll(template.getLabelsMap());

        Job job = new Job();
        job.setMeta(meta);
        job.setId(slave.getNodeName());
        job.setName(slave.getNodeName());
        job.setRegion(getRegion(cloud));
        job.addDatacenters(getDatacenters(cloud));
        job.setType("batch");
        job.setTaskGroups(taskGroups);

        return job;
    }

    private String getRegion(NomadCloud cloud) {
        return template.getRegion() == null ? cloud.getRegion() : template.getRegion();
    }

    private String[] getDatacenters(NomadCloud cloud) {
        List<String> dc = template.getDatacenters();
        if (dc.isEmpty()) {
            dc = cloud.getDatacentersList();
        }

        return dc.toArray(new String[0]);
    }

    private TaskGroup createTaskGroup(NomadSlave slave, TaskTemplate taskTemplate, Collection<EnvVar> globalEnvVars) {
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();
        NomadCloud cloud = slave.getNomadCloud();
        String url = cloud.getJenkinsUrlOrDie();

        // Default common environment variables for all the containers.
        env.put("JENKINS_SECRET", slave.getComputer().getJnlpMac());
        env.put("JENKINS_AGENT_NAME", slave.getComputer().getName());
        env.put("JNLP_PROTOCOL_OPTS", "");
        env.put("JENKINS_JNLP_URL", url + "/computer/" + slave.getNodeName() + "/slave-agent.jnlp");

        env.put("JENKINS_URL", url);
        if (!StringUtils.isBlank(cloud.getJenkinsTunnel())) {
            env.put("JENKINS_TUNNEL", cloud.getJenkinsTunnel());
        }

        if (globalEnvVars != null) {
            globalEnvVars.forEach(item
                    -> env.put(item.getKey(), item.getValue())
            );
        }

        TaskGroup taskGroup = new TaskGroup();
        taskGroup.setName(substituteEnv(taskTemplate.getName()));

        Task task = taskTemplate.build(slave, new HashMap<>(env));
        taskGroup.addTasks(task);
        
        RestartPolicy restartPolicy = new RestartPolicy()
                .setMode("fail")
                .setAttempts(0);
        taskGroup.setRestartPolicy(restartPolicy);

        return taskGroup;
    }

    public static String substituteEnv(String s) {
        return replaceMacro(s, System.getenv());
    }
}