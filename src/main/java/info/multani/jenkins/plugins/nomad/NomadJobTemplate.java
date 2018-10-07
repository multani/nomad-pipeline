package info.multani.jenkins.plugins.nomad;

import com.hashicorp.nomad.apimodel.Job;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.tools.ToolLocationNodeProperty;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Nomad Job Template
 *
 */
public class NomadJobTemplate extends AbstractDescribableImpl<NomadJobTemplate> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String FALLBACK_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplate.class.getName());

    public static final int DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT = 100;

//    private String inheritFrom;

    private String name;

    private String image;

    private String command;

    private String args;

    private int instanceCap = Integer.MAX_VALUE;

    private int slaveConnectTimeout = DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;

    private int idleMinutes;

    private String label;

    private Node.Mode nodeUsageMode;

    private Integer resourcesCPU;

    private Integer resourcesMemory;

    private List<TaskGroupTemplate> taskGroups = new ArrayList<>();

    private Map<String, String> envVars = new HashMap<>();

    private transient List<ToolLocationNodeProperty> nodeProperties;

    @DataBoundConstructor
    public NomadJobTemplate() {
    }

    public NomadJobTemplate(NomadJobTemplate from) {
        this.setTaskGroups(from.getTaskGroups());
        this.setInstanceCap(from.getInstanceCap());
        this.setLabel(from.getLabel());
//        this.setName(from.getName());
//        this.setInheritFrom(from.getInheritFrom());
        this.setNodeUsageMode(from.getNodeUsageMode());
        this.setSlaveConnectTimeout(from.getSlaveConnectTimeout());
    }

    private Optional<TaskGroupTemplate> getFirstContainer() {
        return Optional.ofNullable(getTaskGroups().isEmpty() ? null : getTaskGroups().get(0));
    }

//    public String getInheritFrom() {
//        return inheritFrom;
//    }
//
//    @DataBoundSetter
//    public void setInheritFrom(String inheritFrom) {
//        this.inheritFrom = inheritFrom;
//    }
//
    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return "Nomad Job Template";
    }

    public void setInstanceCap(int instanceCap) {
        if (instanceCap < 0) {
            this.instanceCap = Integer.MAX_VALUE;
        } else {
            this.instanceCap = instanceCap;
        }
    }
//
    public int getInstanceCap() {
        return instanceCap;
    }

    public void setSlaveConnectTimeout(int slaveConnectTimeout) {
        if (slaveConnectTimeout <= 0) {
            LOGGER.log(Level.WARNING, "Agent -> Jenkins connection timeout " +
                    "cannot be <= 0. Falling back to the default value: " +
                    DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT);
            this.slaveConnectTimeout = DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
        } else {
            this.slaveConnectTimeout = slaveConnectTimeout;
        }
    }

    public int getSlaveConnectTimeout() {
        if (slaveConnectTimeout == 0)
            return DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;
        return slaveConnectTimeout;
    }

    @DataBoundSetter
    public void setInstanceCapStr(String instanceCapStr) {
        if (StringUtils.isBlank(instanceCapStr)) {
            setInstanceCap(Integer.MAX_VALUE);
        } else {
            setInstanceCap(Integer.parseInt(instanceCapStr));
        }
    }

    public String getInstanceCapStr() {
        if (getInstanceCap() == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(instanceCap);
        }
    }

    @DataBoundSetter
    public void setSlaveConnectTimeoutStr(String slaveConnectTimeoutStr) {
        if (StringUtils.isBlank(slaveConnectTimeoutStr)) {
            setSlaveConnectTimeout(DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT);
        } else {
            setSlaveConnectTimeout(Integer.parseInt(slaveConnectTimeoutStr));
        }
    }

    public String getSlaveConnectTimeoutStr() {
        return String.valueOf(slaveConnectTimeout);
    }

    public void setIdleMinutes(int i) {
        this.idleMinutes = i;
    }

    public int getIdleMinutes() {
        return idleMinutes;
    }

    @DataBoundSetter
    public void setIdleMinutesStr(String idleMinutes) {
        if (StringUtils.isBlank(idleMinutes)) {
            setIdleMinutes(0);
        } else {
            setIdleMinutes(Integer.parseInt(idleMinutes));
        }
    }

    public String getIdleMinutesStr() {
        if (getIdleMinutes() == 0) {
            return "";
        } else {
            return String.valueOf(idleMinutes);
        }
    }

    public Set<LabelAtom> getLabelSet() {
        return Label.parse(label);
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    @DataBoundSetter
    public void setNodeUsageMode(Node.Mode nodeUsageMode) {
        this.nodeUsageMode = nodeUsageMode;
    }

    @DataBoundSetter
    public void setNodeUsageMode(String nodeUsageMode) {
        this.nodeUsageMode = Node.Mode.valueOf(nodeUsageMode);
    }

    public Node.Mode getNodeUsageMode() {
        return nodeUsageMode;
    }

    @NotNull
    public Map<String, String> getEnvVars() {
        if (envVars == null) {
            return Collections.emptyMap();
        }
        return envVars;
    }

    public void addEnvVars(Map<String, String> envVars) {
        if (envVars != null) {
            this.envVars.putAll(envVars);
        }
    }

    @DataBoundSetter
    public void setEnvVars(Map<String, String> envVars) {
        if (envVars != null) {
            this.envVars.clear();
            this.addEnvVars(envVars);
        }
    }

    @DataBoundSetter
    public void setNodeProperties(List<ToolLocationNodeProperty> nodeProperties){
        this.nodeProperties = nodeProperties;
    }

    @Nonnull
    public List<ToolLocationNodeProperty> getNodeProperties(){
        if (nodeProperties == null) {
            return Collections.emptyList();
        }
        return nodeProperties;
    }

    @DataBoundSetter
    public void setTaskGroups(@Nonnull List<TaskGroupTemplate> items) {
        synchronized (this.taskGroups) {
            this.taskGroups.clear();
            this.taskGroups.addAll(items);
        }
    }

    @Nonnull
    public List<TaskGroupTemplate> getTaskGroups() {
        if (taskGroups == null) {
            return Collections.emptyList();
        }
        return taskGroups;
    }

    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        if (taskGroups == null) {
            taskGroups = new ArrayList<>();
            TaskGroupTemplate taskGroupTemplate = new TaskGroupTemplate(NomadCloud.JNLP_NAME, this.image);
            taskGroupTemplate.setCommand(command);
            taskGroupTemplate.setArgs(args);
            taskGroupTemplate.setEnvVars(envVars);
            taskGroupTemplate.setResourcesCPU(resourcesCPU);
            taskGroupTemplate.setResourcesMemory(resourcesMemory);
            taskGroups.add(taskGroupTemplate);
        }

        return this;
    }

    /**
     * Build a Pod object from a PodTemplate
     * 
     * @param slave
     */

    /**
     * Build a Pod object from a PodTemplate
     * @param slave
     * @return
     */
    public Job build(NomadSlave slave) {
        return new NomadJobTemplateBuilder(this).build(slave);
    }

    @Extension
    @Symbol("nomadJobTemplate")
    public static class DescriptorImpl extends Descriptor<NomadJobTemplate> {

        @Override
        public String getDisplayName() {
            return "Nomad Job";
        }

//        @SuppressWarnings("unused") // Used by jelly
//        @Restricted(DoNotUse.class) // Used by jelly
//        public List<? extends Descriptor> getEnvVarsDescriptors() {
//            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(TemplateEnvVar.class));
//        }
    }

    @Override
    public String toString() {
        return "NomadJobTemplate{" +
//                (inheritFrom == null ? "" : "inheritFrom='" + inheritFrom + '\'') +
                (name == null ? "" : ", name='" + name + '\'') +
                (image == null ? "" : ", image='" + image + '\'') +
                (command == null ? "" : ", command='" + command + '\'') +
                (args == null ? "" : ", args='" + args + '\'') +
                (instanceCap == Integer.MAX_VALUE ? "" : ", instanceCap=" + instanceCap) +
                (slaveConnectTimeout == DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT ? "" : ", slaveConnectTimeout=" + slaveConnectTimeout) +
                (idleMinutes == 0 ? "" : ", idleMinutes=" + idleMinutes) +
                (label == null ? "" : ", label='" + label + '\'') +
                (nodeUsageMode == null ? "" : ", nodeUsageMode=" + nodeUsageMode) +
                (resourcesCPU == null ? "" : ", resourcesCpu='" + resourcesCPU + '\'') +
                (resourcesMemory == null ? "" : ", resourcesMemory='" + resourcesMemory + '\'') +
                (taskGroups == null || taskGroups.isEmpty() ? "" : ", taskGroups=" + taskGroups) +
                (envVars == null || envVars.isEmpty() ? "" : ", envVars=" + envVars) +
                (nodeProperties == null || nodeProperties.isEmpty() ? "" : ", nodeProperties=" + nodeProperties) +
                '}';
    }
}
