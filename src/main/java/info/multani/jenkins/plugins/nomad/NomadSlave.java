package info.multani.jenkins.plugins.nomad;

import com.hashicorp.nomad.javasdk.EvaluationResponse;
import com.hashicorp.nomad.javasdk.NomadApiClient;
import com.hashicorp.nomad.javasdk.NomadException;
import hudson.Extension;
import hudson.Launcher;
import hudson.console.ModelHyperlinkNote;
import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.TaskListener;
import hudson.remoting.Engine;
import hudson.remoting.VirtualChannel;
import hudson.slaves.AbstractCloudSlave;
import hudson.slaves.Cloud;
import hudson.slaves.CloudRetentionStrategy;
import hudson.slaves.ComputerLauncher;
import hudson.slaves.OfflineCause;
import hudson.slaves.RetentionStrategy;
import info.multani.jenkins.plugins.nomad.pipeline.NomadJobTemplateStep;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.jenkinsci.plugins.durabletask.executors.OnceRetentionStrategy;
import org.jvnet.localizer.Localizable;
import org.jvnet.localizer.ResourceBundleHolder;

public class NomadSlave extends AbstractCloudSlave {

    private static final Logger LOGGER = Logger.getLogger(NomadSlave.class.getName());

    private static final Integer DISCONNECTION_TIMEOUT = Integer
            .getInteger(NomadSlave.class.getName() + ".disconnectionTimeout", 5);

    private static final long serialVersionUID = -8642936855413034232L;

    /**
     * The resource bundle reference
     */
    private static final ResourceBundleHolder HOLDER = ResourceBundleHolder.get(Messages.class);

    private final String cloudName;
    private final NomadJobTemplate template;
    private transient Set<Queue.Executable> executables = new HashSet<>();

    public NomadJobTemplate getTemplate() {
        return template;
    }

    protected NomadSlave(String name, NomadJobTemplate template, String nodeDescription, String cloudName, String labelStr,
                           ComputerLauncher computerLauncher, RetentionStrategy rs)
            throws Descriptor.FormException, IOException {
        super(name,
                nodeDescription,
                null, // TODO: remoteFs
                1,
//                template.getNodeUsageMode() != null ? template.getNodeUsageMode() : TODO
                Node.Mode.NORMAL,
                labelStr == null ? null : labelStr,
                computerLauncher,
                rs,
                template.getNodeProperties()
        );

        this.cloudName = cloudName;
        this.template = template;
    }

    public String getCloudName() {
        return cloudName;
    }

    /**
     * Returns the cloud instance which created this agent.
     * @return the cloud instance which created this agent.
     * @throws IllegalStateException if the cloud doesn't exist anymore, or is not a {@link NomadCloud}.
     */
    @Nonnull
    public NomadCloud getNomadCloud() {
        Cloud cloud = Jenkins.getInstance().getCloud(getCloudName());
        if (cloud instanceof NomadCloud) {
            return (NomadCloud) cloud;
        } else {
            throw new IllegalStateException(getClass().getName() + " can be launched only by instances of " + NomadCloud.class.getName());
        }
    }

    static String getSlaveName(NomadJobTemplate template) {
        String name = template.getName();
        if (StringUtils.isEmpty(name)) {
            template.generateName(NomadJobTemplateStep.DEFAULT_AGENT_NAME);
            name = template.getName();
        }
        return name;
    }

    @Override
    public NomadComputer createComputer() {
        return new NomadComputer(this);
    }

    @Override
    public Launcher createLauncher(TaskListener listener) {
        if (template != null) {
            Executor executor = Executor.currentExecutor();
            if (executor != null) {
                Queue.Executable currentExecutable = executor.getCurrentExecutable();
                if (currentExecutable != null && executables.add(currentExecutable)) {
                    listener.getLogger().println(Messages.NomadSlave_AgentIsProvisionedFromTemplate(
                            ModelHyperlinkNote.encodeTo("/computer/" + getNodeName(), getNodeName()),
                            getTemplate().getDisplayName())
                    );
                    listener.getLogger().println(getTemplate().getDescriptionForLogging());
                }
            }
        }

        return super.createLauncher(listener);
    }

    protected Object readResolve() {
        this.executables = new HashSet<>();
        return this;
    }

    @Override
    protected void _terminate(TaskListener listener) throws IOException, InterruptedException {
        LOGGER.log(Level.INFO, "Terminating Nomad job for agent {0}", name);

        NomadCloud cloud;
        try {
            cloud = getNomadCloud();
        } catch (IllegalStateException e) {
            String msg = String.format("Unable to terminate agent %s. Cloud may have been removed. There may be leftover resources on the Nomad cluster.", name);
            e.printStackTrace(listener.fatalError(msg));
            LOGGER.log(Level.SEVERE, msg);
            return;
        }
        NomadApiClient client;
        try {
            client = cloud.connect();
        } catch (UnrecoverableKeyException | CertificateEncodingException | NoSuchAlgorithmException
                | KeyStoreException e) {
            String msg = String.format("Failed to connect to cloud %s", getCloudName());
            e.printStackTrace(listener.fatalError(msg));
            LOGGER.log(Level.SEVERE, msg);
            return;
        }

        // TODO: check the job status and the job retention policy to determine
        // if the job needs to be stopped or not.

        Computer computer = toComputer();
        if (computer == null) {
            String msg = String.format("Computer for agent is null: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            return;
        }

        // Tell the slave to stop JNLP reconnects.
        VirtualChannel ch = computer.getChannel();
        if (ch != null) {
            ch.call(new SlaveDisconnector());
        }

        OfflineCause offlineCause = OfflineCause.create(new Localizable(HOLDER, "offline"));

        Future<?> disconnected = computer.disconnect(offlineCause);
        // wait a bit for disconnection to avoid stack traces in logs
        try {
            disconnected.get(DISCONNECTION_TIMEOUT, TimeUnit.SECONDS);
        } catch (Exception e) {
            String msg = String.format("Ignoring error waiting for agent disconnection %s: %s", name, e.getMessage());
            LOGGER.log(Level.INFO, msg, e);
        }

        if (getCloudName() == null) {
            String msg = String.format("Cloud name is not set for agent, can't terminate: %s", name);
            LOGGER.log(Level.SEVERE, msg);
            listener.fatalError(msg);
            return;
        }

        deleteJob(listener, client);

        String msg = String.format("Disconnected computer %s", name);
        LOGGER.log(Level.INFO, msg);
        listener.getLogger().println(msg);
    }

    private void deleteJob(TaskListener listener, NomadApiClient client) throws IOException {
        EvaluationResponse response;
        LOGGER.log(Level.FINE, "Deregistering job {0} from cloud {1}",
                new Object[]{name, getCloudName()});
        try {
            response = client.getJobsApi().deregister(name);
        } catch (NomadException e) {
            String msg = String.format("Failed to delete job for agent %s: %s", name,
                    e.getMessage());
            LOGGER.log(Level.WARNING, msg, e);
            listener.error(msg);
            return;
        }

        LOGGER.log(Level.FINE, "Deregistered {0} using evaluation ID {1}",
                new Object[]{name, response.getValue()});

        boolean deleted = response.getHttpResponse().getStatusLine().getStatusCode() == 200;

        if (!deleted) {
            String msg = String.format("Failed to delete job for agent %s: HTTP %s",
                    name, response.getHttpResponse().getStatusLine().getStatusCode());
            LOGGER.log(Level.WARNING, msg);
            listener.error(msg);
            return;
        }

        String msg = String.format("Terminated Nomad job for agent %s", name);
        LOGGER.log(Level.INFO, msg);
        listener.getLogger().println(msg);
    }

    @Override
    public String toString() {
        return String.format("NomadSlave name: %s", name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;

        NomadSlave that = (NomadSlave) o;

        if (cloudName != null ? !cloudName.equals(that.cloudName) : that.cloudName != null) return false;
        return template != null ? template.equals(that.template) : that.template == null;
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (cloudName != null ? cloudName.hashCode() : 0);
        result = 31 * result + (template != null ? template.hashCode() : 0);
        return result;
    }

    /**
     * Returns a new {@link Builder} instance.
     * @return a new {@link Builder} instance.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builds a {@link NomadSlave} instance.
     */
    public static class Builder {
        private String name;
        private String nodeDescription;
        private NomadJobTemplate jobTemplate;
        private NomadCloud cloud;
        private String label;
        private ComputerLauncher computerLauncher;
        private RetentionStrategy retentionStrategy;

        /**
         * @param name The name of the future {@link NomadSlave}
         * @return the current instance for method chaining
         */
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        /**
         * @param nodeDescription The node description of the future {@link NomadSlave}
         * @return the current instance for method chaining
         */
        public Builder nodeDescription(String nodeDescription) {
            this.nodeDescription = nodeDescription;
            return this;
        }

        /**
         * @param jobTemplate The job template the future {@link NomadSlave} has been created from
         * @return the current instance for method chaining
         */
        public Builder jobTemplate(NomadJobTemplate jobTemplate) {
            this.jobTemplate = jobTemplate;
            return this;
        }

        /**
         * @param cloud The cloud that is provisioning the {@link NomadSlave} instance.
         * @return the current instance for method chaining
         */
        public Builder cloud(NomadCloud cloud) {
            this.cloud = cloud;
            return this;
        }

        /**
         * @param label The label the {@link NomadSlave} has.
         * @return the current instance for method chaining
         */
        public Builder label(String label) {
            this.label = label;
            return this;
        }

        /**
         * @param computerLauncher The computer launcher to use to launch the {@link NomadSlave} instance.
         * @return the current instance for method chaining
         */
        public Builder computerLauncher(ComputerLauncher computerLauncher) {
            this.computerLauncher = computerLauncher;
            return this;
        }

        /**
         * @param retentionStrategy The retention strategy to use for the {@link NomadSlave} instance.
         * @return the current instance for method chaining
         */
        public Builder retentionStrategy(RetentionStrategy retentionStrategy) {
            this.retentionStrategy = retentionStrategy;
            return this;
        }

        private RetentionStrategy determineRetentionStrategy() {
            if (jobTemplate.getIdleMinutes() == 0) {
                return new OnceRetentionStrategy(cloud.getRetentionTimeout());
            } else {
                return new CloudRetentionStrategy(jobTemplate.getIdleMinutes());
            }
        }

        /**
         * Builds the resulting {@link NomadSlave} instance.
         * @return an initialized {@link NomadSlave} instance.
         * @throws IOException
         * @throws Descriptor.FormException
         */
        public NomadSlave build() throws IOException, Descriptor.FormException {
            Validate.notNull(jobTemplate);
            Validate.notNull(cloud);
            return new NomadSlave(
                    name == null ? getSlaveName(jobTemplate) : name,
                    jobTemplate,
                    nodeDescription == null ? jobTemplate.getName() : nodeDescription,
                    cloud.name,
                    label == null ? jobTemplate.getLabel() : label,
                    computerLauncher == null ? new NomadLauncher() : computerLauncher,
                    retentionStrategy == null ? determineRetentionStrategy() : retentionStrategy);
        }
    }


    @Extension
    public static final class DescriptorImpl extends SlaveDescriptor {

        @Override
        public String getDisplayName() {
            return "Nomad Agent";
        }

        @Override
        public boolean isInstantiable() {
            return false;
        }
    }

    private static class SlaveDisconnector extends MasterToSlaveCallable<Void, IOException> {

        private static final long serialVersionUID = 8683427258340193283L;

        private static final Logger LOGGER = Logger.getLogger(SlaveDisconnector.class.getName());

        @Override
        public Void call() throws IOException {
            Engine e = Engine.current();
            // No engine, do nothing.
            if (e == null) {
                return null;
            }
            // Tell the slave JNLP agent to not attempt further reconnects.
            e.setNoReconnect(true);
            LOGGER.log(Level.INFO, "Disabled slave engine reconnects.");
            return null;
        }

    }
}
