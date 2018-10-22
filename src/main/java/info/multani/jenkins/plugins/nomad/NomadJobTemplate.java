package info.multani.jenkins.plugins.nomad;

import com.google.common.collect.ImmutableMap;
import com.hashicorp.nomad.apimodel.Job;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.DescriptorVisibilityFilter;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.tools.ToolLocationNodeProperty;
import info.multani.jenkins.plugins.nomad.model.EnvVar;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Nomad Job Template
 *
 */
public class NomadJobTemplate extends AbstractDescribableImpl<NomadJobTemplate> implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final String FALLBACK_ARGUMENTS = "${computer.jnlpmac} ${computer.name}";

    private static final transient String JOB_NAME_FORMAT = "%s-%s";

    private static final String DEFAULT_ID = "jenkins/slave-default";

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplate.class.getName());

    public static final int DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT = 100;

    private String region;

    private List<String> datacenters;

    private String name;

    private String image;

    private String command;

    private List<String> args;

    private int instanceCap = Integer.MAX_VALUE;

    private int slaveConnectTimeout = DEFAULT_SLAVE_JENKINS_CONNECTION_TIMEOUT;

    private int idleMinutes;

    private String label;

    private Node.Mode nodeUsageMode;

    private Integer resourcesCPU = 100; // Mhz

    private Integer resourcesMemory = 300; // MB

    private List<TaskTemplate> taskGroups = new ArrayList<>();

    private List<EnvVar> envVars = new ArrayList<>();

    private transient List<ToolLocationNodeProperty> nodeProperties;

    @DataBoundConstructor
    public NomadJobTemplate() {
    }

    public NomadJobTemplate(NomadJobTemplate from) {
        this.setTaskGroups(from.getTaskGroups());
        this.setInstanceCap(from.getInstanceCap());
        this.setLabel(from.getLabel());
        this.setName(from.getName());
        this.setNodeUsageMode(from.getNodeUsageMode());
        this.setSlaveConnectTimeout(from.getSlaveConnectTimeout());
    }

    private Optional<TaskTemplate> getFirstContainer() {
        return Optional.ofNullable(getTaskGroups().isEmpty() ? null : getTaskGroups().get(0));
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void generateName(String stepName) {
        //Let's generate a random name based on the user specified to make sure that we don't have
        //issues with concurrent builds, or messing with pre-existing configuration
        String randString = RandomStringUtils.random(10, "bcdfghjklmnpqrstvwxz0123456789");
        setName(String.format(JOB_NAME_FORMAT, stepName, randString));
    }

    public String getDisplayName() {
        return "Nomad Job Template";
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

    public Map<String, String> getLabelsMap() {
        Set<LabelAtom> labelSet = getLabelSet();
        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String> builder();
        if (!labelSet.isEmpty()) {
            labelSet.forEach((label) -> {
                builder.put(label == null ? DEFAULT_ID : "jenkins/" + label.getName(), "true");
            });
        }
        return builder.build();
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

    public List<EnvVar> getEnvVars() {
        if (envVars == null) {
            return Collections.emptyList();
        }
        return envVars;
    }

    public void addEnvVars(List<EnvVar> envVars) {
        if (envVars != null) {
            this.envVars.addAll(envVars);
        }
    }

    @DataBoundSetter
    public void setEnvVars(List<EnvVar> envVars) {
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
    public void setTaskGroups(@Nonnull List<TaskTemplate> items) {
        synchronized (this.taskGroups) {
            this.taskGroups.clear();
            this.taskGroups.addAll(items);
        }
    }

    @Nonnull
    public List<TaskTemplate> getTaskGroups() {
        if (taskGroups == null) {
            return Collections.emptyList();
        }
        return taskGroups;
    }

    @SuppressWarnings("deprecation")
    protected Object readResolve() {
        if (taskGroups == null) {
            taskGroups = new ArrayList<>();
            TaskTemplate taskGroupTemplate = new TaskTemplate(NomadCloud.JNLP_NAME, this.image);
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
     * Build a Job object from a JobTemplate
     * @param slave
     * @return
     */
    public Job build(NomadSlave slave) {
        return new NomadJobTemplateBuilder(this).build(slave);
    }

    public String getDescriptionForLogging() {
        return String.format("Agent specification [%s] (%s): %n%s",
                getDisplayName(),
                getLabel(),
                getTasksDescriptionForLogging());
    }

    private String getTasksDescriptionForLogging() {
        List<TaskTemplate> tasks = getTaskGroups();
        StringBuilder sb = new StringBuilder();
        List<StringBuilder> output = new ArrayList<>();
        for (TaskTemplate t : tasks) {
            sb.append("  * Task [")
                    .append(t.getName())
                    .append("] ")
                    .append(t.getImage()).append(" ");
            StringBuilder optional = new StringBuilder();
            optionalField(optional, "CPU", t.getResourcesCPU().toString(), "Mhz");
            optionalField(optional, "Memory", t.getResourceMemory().toString(), "MB");
            if (optional.length() > 0) {
                sb.append("(").append(optional).append(")");
            }
            output.add(sb);
        }
        return String.join("\n", output);
    }

    private void optionalField(StringBuilder builder, String label, String value, String unit) {
        if (value != null) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(label).append(": ").append(value).append(" ").append(unit);
        }
    }

    @Extension
    @Symbol("nomadJobTemplate")
    public static class DescriptorImpl extends Descriptor<NomadJobTemplate> {

        @Override
        public String getDisplayName() {
            return "Nomad Job";
        }

        @SuppressWarnings("unused") // Used by jelly
        @Restricted(DoNotUse.class) // Used by jelly
        public List<? extends Descriptor> getEnvVarsDescriptors() {
            return DescriptorVisibilityFilter.apply(null, Jenkins.getInstance().getDescriptorList(EnvVar.class));
        }
    }

    @Override
    public String toString() {
        return "NomadJobTemplate{" +
                (name == null ? "" : "name='" + name + '\'') +
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
