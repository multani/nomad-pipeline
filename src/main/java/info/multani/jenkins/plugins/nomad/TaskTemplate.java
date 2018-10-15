package info.multani.jenkins.plugins.nomad;

import com.google.common.base.Preconditions;
import com.hashicorp.nomad.apimodel.Resources;
import com.hashicorp.nomad.apimodel.Task;
import com.hashicorp.nomad.apimodel.TaskArtifact;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import static info.multani.jenkins.plugins.nomad.NomadCloud.JNLP_NAME;
import static info.multani.jenkins.plugins.nomad.NomadJobTemplateBuilder.substituteEnv;
import info.multani.jenkins.plugins.nomad.model.EnvVar;
import info.multani.jenkins.plugins.nomad.pipeline.NomadJobTemplateStepExecution;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class TaskTemplate extends AbstractDescribableImpl<TaskTemplate> implements Serializable {

    private static final long serialVersionUID = 4212681620316294146L;

    public static final String DEFAULT_WORKING_DIR = "/home/jenkins";

    private String name;

    private String image;

    private String workingDir = DEFAULT_WORKING_DIR;

    private String command;

    private List<String> args;

    private Integer resourcesCPU;

    private Integer resourcesMemory;

    private final List<EnvVar> envVars = new ArrayList<>();

    private boolean downloadAgentJar = false;

    private static final String DEFAULT_JNLP_IMAGE = System
            .getProperty(NomadJobTemplateStepExecution.class.getName() + ".defaultImage", "jenkins/jnlp-slave:alpine");

    private static final List<String> DEFAULT_JNLP_ARGUMENTS = Arrays.asList("${computer.jnlpmac}", "${computer.name}");

    private static final String JNLPMAC_REF = "\\$\\{computer.jnlpmac\\}";

    private static final String NAME_REF = "\\$\\{computer.name\\}";

    @DataBoundConstructor
    public TaskTemplate(String name, String image) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
    }

    public TaskTemplate(String name, String image, String command, List<String> args) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
        this.command = command;
        this.args = args;
    }

    public static TaskTemplate defaultTask() {
        TaskTemplate task = new TaskTemplate(JNLP_NAME, DEFAULT_JNLP_IMAGE);
        task.setArgs(DEFAULT_JNLP_ARGUMENTS);
        return task;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setImage(String image) {
        this.image = image;
    }

    public String getImage() {
        return image;
    }

    @DataBoundSetter
    public void setCommand(String command) {
        this.command = command;
    }

    public String getCommand() {
        return command;
    }

    @DataBoundSetter
    public void setArgs(List<String> args) {
        this.args = args;
    }

    @Nonnull
    public List<String> getArgs() {
        return args == null ? Collections.emptyList() : args;
    }

    public String getDisplayName() {
        return "Task Template";
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public List<EnvVar> getEnvVars() {
        return envVars != null ? envVars : Collections.emptyList();
    }

    @DataBoundSetter
    public void setEnvVars(List<EnvVar> envVars) {
        this.envVars.addAll(envVars);
    }

    public Integer getResourcesCPU() {
        return resourcesCPU;
    }

    @DataBoundSetter
    public void setResourcesCPU(Integer resourceLimitCpu) {
        this.resourcesCPU = resourceLimitCpu;
    }

    public Integer getResourceMemory() {
        return resourcesMemory;
    }

    @DataBoundSetter
    public void setResourcesMemory(Integer resourcesMemory) {
        this.resourcesMemory = resourcesMemory;
    }

    public Map<String,Object> getAsArgs() {
        Map<String,Object> argMap = new TreeMap<>();
        argMap.put("name", name);
        return argMap;
    }

    public boolean shouldDownloadAgentJar() {
        return downloadAgentJar;
    }

    @DataBoundSetter
    public void setDownloadAgentJar(boolean downloadAgentJar) {
        this.downloadAgentJar = downloadAgentJar;
    }

    public Task build(NomadSlave slave, Map<String, String> globalEnvVars) {
        Map<String, String> envVars = new HashMap<>();
        NomadCloud cloud = slave.getNomadCloud();

        List<String> arguments = this.getArgs().stream()
                .map(e -> e.replaceAll(JNLPMAC_REF, slave.getComputer().getJnlpMac())
                .replaceAll(NAME_REF, slave.getComputer().getName())
                )
                .collect(Collectors.toList());

        envVars.putAll(globalEnvVars);

        envVars.put("HOME", this.getWorkingDir());

        if (this.getEnvVars() != null) {
            this.getEnvVars().forEach(item
                    -> envVars.put(item.getKey(), item.getValue())
            );
        }

        Task task = new Task();
        task.setName(substituteEnv(this.getName()));
        task.setDriver("docker");
        task.addConfig("image", substituteEnv(getImage()));
        task.addConfig("command", substituteEnv(this.getCommand()));
        task.addConfig("args", arguments);
        task.addConfig("network_mode", "host");

        if (shouldDownloadAgentJar()) {
            TaskArtifact artifact = new TaskArtifact()
                    .setGetterSource(cloud.getSlaveUrl())
                    .setRelativeDest("/local/");
            task.addArtifacts(artifact);
        }

        task.setEnv(envVars);

        Resources resources = new Resources()
                .setCpu(this.getResourcesCPU())
                .setMemoryMb(this.getResourceMemory());
        task.setResources(resources);

        return task;
    }

    @Extension
    @Symbol("taskTemplate")
    public static class DescriptorImpl extends Descriptor<TaskTemplate> {

        @Override
        public String getDisplayName() {
            return "Task Template";
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public List<? extends Descriptor> getEnvVarsDescriptors() {
            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(EnvVar.class));
        }
    }

    @Override
    public String toString() {
        return "TaskTemplate{" +
                (name == null ? "" : "name='" + name + '\'') +
                (image == null ? "" : ", image='" + image + '\'') +
                (workingDir == null ? "" : ", workingDir='" + workingDir + '\'') +
                (command == null ? "" : ", command='" + command + '\'') +
                (args == null ? "" : ", args='" + args + '\'') +
                (resourcesCPU == null ? "" : ", resourcesCPU='" + resourcesCPU + '\'') +
                (resourcesMemory == null ? "" : ", resourcesMemory='" + resourcesMemory + '\'') +
                (envVars == null || envVars.isEmpty() ? "" : ", envVars=" + envVars) +
                '}';
    }
}
