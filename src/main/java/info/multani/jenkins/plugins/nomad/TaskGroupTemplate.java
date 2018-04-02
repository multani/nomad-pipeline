package info.multani.jenkins.plugins.nomad;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import info.multani.jenkins.plugins.nomad.model.TemplateEnvVar;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.base.Preconditions;
import com.hashicorp.nomad.apimodel.Task;
import com.hashicorp.nomad.apimodel.TaskGroup;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import jenkins.model.Jenkins;

// TODO: actually, this defines a Nomad Task within a Task Group
public class TaskGroupTemplate extends AbstractDescribableImpl<TaskGroupTemplate> implements Serializable {

    private static final long serialVersionUID = 4212681620316294146L;

    public static final String DEFAULT_WORKING_DIR = "/home/jenkins";

    private String name;

    private String image;

    private String workingDir = DEFAULT_WORKING_DIR;

    private String command;

    private String args;

    private boolean ttyEnabled;

    private Integer resourcesCPU;

    private Integer resourcesMemory;

    private String shell;

    private final List<TemplateEnvVar> envVars = new ArrayList<>();
    private List<PortMapping> ports = new ArrayList<PortMapping>();

    private ContainerLivenessProbe livenessProbe;

    @Deprecated
    public TaskGroupTemplate(String image) {
        this(null, image);
    }

    @DataBoundConstructor
    public TaskGroupTemplate(String name, String image) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
    }

    public TaskGroupTemplate(String name, String image, String command, String args) {
        Preconditions.checkArgument(!StringUtils.isBlank(image));
        this.name = name;
        this.image = image;
        this.command = command;
        this.args = args;
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
    public void setArgs(String args) {
        this.args = args;
    }

    public String getArgs() {
        return args;
    }

    @DataBoundSetter
    public void setTtyEnabled(boolean ttyEnabled) {
        this.ttyEnabled = ttyEnabled;
    }

    public boolean isTtyEnabled() {
        return ttyEnabled;
    }

    public String getDisplayName() {
        return "Container Pod Template";
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    public String getWorkingDir() {
        return workingDir;
    }

    public List<TemplateEnvVar> getEnvVars() {
        return envVars != null ? envVars : Collections.emptyList();
    }

    @DataBoundSetter
    public void setEnvVars(List<TemplateEnvVar> envVars) {
        this.envVars.addAll(envVars);
    }


    public ContainerLivenessProbe getLivenessProbe() { return livenessProbe; }

    @DataBoundSetter
    public void setLivenessProbe(ContainerLivenessProbe livenessProbe) {
        this.livenessProbe = livenessProbe;
    }

    public List<PortMapping> getPorts() {
        return ports != null ? ports : Collections.emptyList();
    }

    @DataBoundSetter
    public void setPorts(List<PortMapping> ports) {
        this.ports = ports;
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

        if (!StringUtils.isEmpty(shell)) {
            argMap.put("shell", shell);
        }

        return argMap;
    }

    @Extension
    @Symbol("taskGroupTemplate")
    public static class DescriptorImpl extends Descriptor<TaskGroupTemplate> {

        @Override
        public String getDisplayName() {
            return "Task Group Template";
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public List<? extends Descriptor> getEnvVarsDescriptors() {
            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(TemplateEnvVar.class));
        }
    }

    @Override
    public String toString() {
        return "TaskGroupTemplate{" +
                (name == null ? "" : "name='" + name + '\'') +
                (image == null ? "" : ", image='" + image + '\'') +
                (workingDir == null ? "" : ", workingDir='" + workingDir + '\'') +
                (command == null ? "" : ", command='" + command + '\'') +
                (args == null ? "" : ", args='" + args + '\'') +
                (!ttyEnabled ? "" : ", ttyEnabled=" + ttyEnabled) +
                (resourcesCPU == null ? "" : ", resourcesCPU='" + resourcesCPU + '\'') +
                (resourcesMemory == null ? "" : ", resourcesMemory='" + resourcesMemory + '\'') +
                (envVars == null || envVars.isEmpty() ? "" : ", envVars=" + envVars) +
                (ports == null || ports.isEmpty() ? "" : ", ports=" + ports) +
                (livenessProbe == null ? "" : ", livenessProbe=" + livenessProbe) +
                '}';
    }

    public String getShell() {
        return shell;
    }

    @DataBoundSetter
    public void setShell(String shell) {
        this.shell = shell;
    }
}
