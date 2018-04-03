package info.multani.jenkins.plugins.nomad.pipeline;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import info.multani.jenkins.plugins.nomad.TaskGroupTemplate;
import info.multani.jenkins.plugins.nomad.PodAnnotation;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import info.multani.jenkins.plugins.nomad.model.TemplateEnvVar;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import com.google.common.collect.ImmutableSet;

import hudson.Extension;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;

public class NomadJobTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "nomad";

    private String cloud = DEFAULT_CLOUD;
    private String inheritFrom;

    private final String label;
    private final String name;

    private List<TaskGroupTemplate> taskGroups = new ArrayList<>();
    private List<TemplateEnvVar> envVars = new ArrayList<>();

    private int instanceCap = Integer.MAX_VALUE;
    private int idleMinutes;
    private int slaveConnectTimeout = NomadJobTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;

    private Node.Mode nodeUsageMode;
    private String workingDir = TaskGroupTemplate.DEFAULT_WORKING_DIR;

    @DataBoundConstructor
    public NomadJobTemplateStep(String label, String name) {
        this.label = label;
        this.name = name == null ? "jenkins-slave" : name;
    }

    public String getLabel() {
        return label;
    }

    public String getName() {
        return name;
    }

    public String getCloud() {
        return cloud;
    }

    @DataBoundSetter
    public void setCloud(String cloud) {
        this.cloud = cloud;
    }

    public String getInheritFrom() {
        return inheritFrom;
    }

    @DataBoundSetter
    public void setInheritFrom(String inheritFrom) {
        this.inheritFrom = inheritFrom;
    }

    public List<TaskGroupTemplate> getTaskGroups() {
        return taskGroups;
    }

    @DataBoundSetter
    public void setTaskGroups(List<TaskGroupTemplate> taskGroups) {
        this.taskGroups = taskGroups;
    }

    public List<TemplateEnvVar> getEnvVars() {
        return (List<TemplateEnvVar>) (envVars == null ? Collections.emptyList() : envVars);
    }

    @DataBoundSetter
    public void setEnvVars(List<TemplateEnvVar> envVars) {
        if (envVars != null) {
            this.envVars.clear();
            this.envVars.addAll(envVars);
        }
    }

    public int getInstanceCap() {
        return instanceCap;
    }

    @DataBoundSetter
    public void setInstanceCap(int instanceCap) {
        this.instanceCap = instanceCap;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutes(int idleMinutes) {
        this.idleMinutes = idleMinutes;
    }

    public int getSlaveConnectTimeout() {
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        this.slaveConnectTimeout = slaveConnectTimeout;
    }

    public Node.Mode getNodeUsageMode() {
        return nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(Node.Mode nodeUsageMode) {
        this.nodeUsageMode = nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(String nodeUsageMode) {
        this.nodeUsageMode = Node.Mode.valueOf(nodeUsageMode);
    }
    
    public String getWorkingDir() {
        return workingDir;
    }

    @DataBoundSetter
    public void setWorkingDir(String workingDir) {
        this.workingDir = workingDir;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new NomadJobTemplateStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "NomadJobTemplate";
        }

        @Override
        public String getDisplayName() {
            return "Define a Nomad job to use in the Nomad plugin";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }

        @Override
        public boolean isAdvanced() {
            return true;
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }
    }
}
