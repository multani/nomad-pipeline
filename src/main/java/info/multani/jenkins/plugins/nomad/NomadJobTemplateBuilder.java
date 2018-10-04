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

//import static info.multani.jenkins.plugins.nomad.PodTemplateUtils.*;
import com.google.common.base.Strings;
import com.hashicorp.nomad.apimodel.Job;
import com.hashicorp.nomad.apimodel.Resources;
import com.hashicorp.nomad.apimodel.RestartPolicy;
import com.hashicorp.nomad.apimodel.Task;
import com.hashicorp.nomad.apimodel.TaskGroup;
import static hudson.Util.replaceMacro;
import info.multani.jenkins.plugins.nomad.model.TemplateEnvVar;
import info.multani.jenkins.plugins.nomad.pipeline.NomadJobTemplateStepExecution;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Helper class to build Pods from PodTemplates
 *
 * @author Carlos Sanchez
 * @since
 *
 */
public class NomadJobTemplateBuilder {

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateBuilder.class.getName());

    private static final Pattern SPLIT_IN_SPACES = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");

    private static final String WORKSPACE_VOLUME_NAME = "workspace-volume";

    private static final String DEFAULT_JNLP_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(NomadJobTemplateStepExecution.class.getName() + ".defaultImage", "jenkins/jnlp-slave:alpine");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";
    private static final String NAME_REF = "\\$\\{computer.name\\}";

    private NomadJobTemplate template;

    public NomadJobTemplateBuilder(NomadJobTemplate template) {
        this.template = template;
    }

    /**
     * Create a Pod object from a PodTemplate
     *
     * @param slave
     * @return
     */
    public Job build(NomadSlave slave) {
        ArrayList<TaskGroup> taskGroups = new ArrayList<>();

//        Map<String, Container> taskGroups = new HashMap<>();
        for (TaskGroupTemplate taskGroupTemplate : template.getTaskGroups()) {
            taskGroups.add(
                    createContainer(slave, taskGroupTemplate, template.getEnvVars())
            );
        }

//        if (!taskGroups.containsKey(JNLP_NAME)) {
//            TaskGroupTemplate taskGroupTemplate = new TaskGroupTemplate(DEFAULT_JNLP_IMAGE);
//            taskGroupTemplate.setName(JNLP_NAME);
//            taskGroupTemplate.setArgs(DEFAULT_JNLP_ARGUMENTS);
//            taskGroups.put(JNLP_NAME, createContainer(slave, taskGroupTemplate, template.getEnvVars(), volumeMounts.values()));
//        }

        Job job = new Job();
        job.setId(slave.getNodeName());
        job.setName(slave.getNodeName());
        job.setRegion("global");
        job.addDatacenters("dc1");
        job.setType("batch");
        job.setTaskGroups(taskGroups);
        
        return job;

    }

    private TaskGroup createContainer(NomadSlave slave, TaskGroupTemplate taskGroupTemplate, Collection<TemplateEnvVar> globalEnvVars) {
        // Last-write wins map of environment variable names to values
        HashMap<String, String> env = new HashMap<>();

        // Add some default env vars for Jenkins
        env.put("JENKINS_SECRET", slave.getComputer().getJnlpMac());
        env.put("JENKINS_NAME", slave.getComputer().getName());
        env.put("JNLP_PROTOCOL_OPTS", "");

        NomadCloud cloud = slave.getNomadCloud();

        String url = cloud.getJenkinsUrlOrDie();

        env.put("JENKINS_URL", url);
        if (!StringUtils.isBlank(cloud.getJenkinsTunnel())) {
            env.put("JENKINS_TUNNEL", cloud.getJenkinsTunnel());
        }

        // Running on OpenShift Enterprise, security concerns force use of arbitrary user ID
        // As a result, container is running without a home set for user, resulting into using `/` for some tools,
        // and `?` for java build tools. So we force HOME to a safe location.
        env.put("HOME", taskGroupTemplate.getWorkingDir());

        Map<String, String> envVars = new HashMap<>();

        env.entrySet().forEach(item
                -> envVars.put(item.getKey(), item.getValue())
        );

//        if (globalEnvVars != null) {
//            globalEnvVars.forEach(item
//                    -> envVarsMap.put(item.getKey(), item.getValue())
//            );
//        }

//        if (containerTemplate.getEnvVars() != null) {
//            containerTemplate.getEnvVars().forEach(item
//                    -> envVarsMap.put(item.getKey(), item.getValue())
//            );
//        }

        //EnvVar[] envVars = envVarsMap.values().stream().toArray(EnvVar[]::new);
        List<String> arguments = Strings.isNullOrEmpty(taskGroupTemplate.getArgs()) ? Collections.emptyList()
                : parseDockerCommand(taskGroupTemplate.getArgs() //
                        .replaceAll(JNLPMAC_REF, slave.getComputer().getJnlpMac()) //
                        .replaceAll(NAME_REF, slave.getComputer().getName()));

//        List<VolumeMount> containerMounts = new ArrayList<>(volumeMounts);
//        ContainerPort[] ports = containerTemplate.getPorts().stream().map(entry -> entry.toPort()).toArray(size -> new ContainerPort[size]);

//        if (!Strings.isNullOrEmpty(containerTemplate.getWorkingDir())
//                && !PodVolume.volumeMountExists(containerTemplate.getWorkingDir(), volumeMounts)) {
//            containerMounts.add(new VolumeMount(containerTemplate.getWorkingDir(), WORKSPACE_VOLUME_NAME, false, null));
//        }
//        ContainerLivenessProbe clp = containerTemplate.getLivenessProbe();
//        Probe livenessProbe = null;
//        if (clp != null && parseLivenessProbe(clp.getExecArgs()) != null) {
//            livenessProbe = new ProbeBuilder()
//                    .withExec(new ExecAction(parseLivenessProbe(clp.getExecArgs())))
//                    .withInitialDelaySeconds(clp.getInitialDelaySeconds())
//                    .withTimeoutSeconds(clp.getTimeoutSeconds())
//                    .withFailureThreshold(clp.getFailureThreshold())
//                    .withPeriodSeconds(clp.getPeriodSeconds())
//                    .withSuccessThreshold(clp.getSuccessThreshold())
//                    .build();
//        }
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

        task.setEnv(envVars);

        Resources resources = new Resources()
                .setCpu(taskGroupTemplate.getResourcesCPU())
                .setMemoryMb(taskGroupTemplate.getResourceMemory());
        task.setResources(resources);

        taskGroup.addTasks(task);

        return taskGroup;

//        return new ContainerBuilder()
//                .withName()
//                .withImage()
//                .withNewSecurityContext()
//                .endSecurityContext()
//                .withWorkingDir(substituteEnv(containerTemplate.getWorkingDir()))
//                .addToEnv(envVars)
//                .addToPorts(ports)
//                .withCommand(parseDockerCommand())
//                .withArgs()
//                .withLivenessProbe(livenessProbe)
//                .withTty(containerTemplate.isTtyEnabled())
//                .withNewResources()
//                .withRequests(getResourcesMap(containerTemplate.getResourcesMemory(), containerTemplate.getResourcesCPU()))
//                .endResources()
//                .build();
    }

    public static String substituteEnv(String s) {
        return replaceMacro(s, System.getenv());
    }

    /**
     * Split a command in the parts that Docker need
     *
     * @param dockerCommand
     * @return
     */
    @Restricted(NoExternalUse.class)
    static List<String> parseDockerCommand(String dockerCommand) {
        if (dockerCommand == null || dockerCommand.isEmpty()) {
            return null;
        }
        // handle quoted arguments
        Matcher m = SPLIT_IN_SPACES.matcher(dockerCommand);
        List<String> commands = new ArrayList<String>();
        while (m.find()) {
            commands.add(m.group(1).replace("\"", ""));
        }

        return commands;
    }

    /**
     * Split a command in the parts that LivenessProbe need
     *
     * @param livenessProbeExec
     * @return
     */
//    @Restricted(NoExternalUse.class)
//    static List<String> parseLivenessProbe(String livenessProbeExec) {
//        if (StringUtils.isBlank(livenessProbeExec)) {
//            return null;
//        }
//        // handle quoted arguments
//        Matcher m = SPLIT_IN_SPACES.matcher(livenessProbeExec);
//        List<String> commands = new ArrayList<String>();
//        while (m.find()) {
//            commands.add(substituteEnv(m.group(1).replace("\"", "").replace("?:\\\"", "")));
//        }
//        return commands;
//    }
//
//    private Map<String, Quantity> getResourcesMap(String memory, String cpu) {
//        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity>builder();
//        String actualMemory = substituteEnv(memory);
//        String actualCpu = substituteEnv(cpu);
//        if (StringUtils.isNotBlank(actualMemory)) {
//            Quantity memoryQuantity = new Quantity(actualMemory);
//            builder.put("memory", memoryQuantity);
//        }
//        if (StringUtils.isNotBlank(actualCpu)) {
//            Quantity cpuQuantity = new Quantity(actualCpu);
//            builder.put("cpu", cpuQuantity);
//        }
//        return builder.build();
//    }
}
