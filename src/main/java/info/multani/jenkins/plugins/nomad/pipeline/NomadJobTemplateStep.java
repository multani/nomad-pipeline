package info.multani.jenkins.plugins.nomad.pipeline;

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import info.multani.jenkins.plugins.nomad.TaskTemplate;
import info.multani.jenkins.plugins.nomad.model.EnvVar;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class NomadJobTemplateStep extends Step implements Serializable {

    private static final long serialVersionUID = 5588861066775717487L;

    private static final String DEFAULT_CLOUD = "nomad";

    public static final String DEFAULT_AGENT_NAME = "jenkins-worker";

    private String cloud = DEFAULT_CLOUD;

    private final String label;
    private final String name;
    private String region;
    private List<String> datacenters;

    private List<TaskTemplate> taskGroups = new ArrayList<>();
    private List<EnvVar> envVars = new ArrayList<>();

    private int instanceCap = Integer.MAX_VALUE;
    private int idleMinutes;
    private int slaveConnectTimeout = NomadJobTemplate.DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;

    private Node.Mode nodeUsageMode;
    private String workingDir = TaskTemplate.DEFAULT_WORKING_DIR;

    @DataBoundConstructor
    public NomadJobTemplateStep(String label, String name) {
        this.label = label;
        this.name = name == null ? NomadJobTemplateStep.DEFAULT_AGENT_NAME : name;
    }

    public String getRegion() {
        return region;
    }

    @DataBoundSetter
    public void setRegion(String region) {
        this.region = region;
    }

    public List<String> getDatacenters() {
        if (datacenters == null) {
            return new ArrayList<>();
        }
        return datacenters;
    }

    @DataBoundSetter
    public void setDatacenters(List<String> datacenters) {
        this.datacenters = datacenters;
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

    public List<TaskTemplate> getTaskGroups() {
        return taskGroups;
    }

    @DataBoundSetter
    public void setTaskGroups(List<TaskTemplate> taskGroups) {
        this.taskGroups = taskGroups;
    }

    public List<EnvVar> getEnvVars() {
        return (List<EnvVar>) (envVars == null ? Collections.emptyList() : envVars);
    }

    @DataBoundSetter
    public void setEnvVars(List<EnvVar> envVars) {
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
            return "nomadJobTemplate";
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
