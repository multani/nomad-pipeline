package info.multani.jenkins.plugins.nomad;


import com.google.common.collect.ImmutableMap;
import com.hashicorp.nomad.javasdk.NomadApiClient;
import com.hashicorp.nomad.javasdk.NomadApiConfiguration;
import com.hashicorp.nomad.javasdk.NomadException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.Util;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.model.labels.LabelAtom;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import info.multani.jenkins.plugins.nomad.pipeline.NomadJobTemplateMap;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Nomad cloud provider.
 *
 * Starts Jenkins agents in a Nomad cluster using defined Docker templates for each
 * label.
 *
 * @author Carlos Sanchez carlos@apache.org
 */
public class NomadCloud extends Cloud {

    public static final int DEFAULT_MAX_REQUESTS_PER_HOST = 32;

    private static final Logger LOGGER = Logger.getLogger(NomadCloud.class.getName());

    private static final String DEFAULT_ID = "jenkins/slave-default";

    public static final String JNLP_NAME = "jnlp";

    /**
     * Default timeout for idle workers that don't correctly indicate exit.
     */
    private static final int DEFAULT_RETENTION_TIMEOUT_MINUTES = 5;

    @Nonnull
    private List<NomadJobTemplate> templates = new ArrayList<>();
    private String serverUrl;


    private String jenkinsUrl;

    @CheckForNull
    private String jenkinsTunnel;

    private int containerCap = Integer.MAX_VALUE;
    private int retentionTimeout = DEFAULT_RETENTION_TIMEOUT_MINUTES;
    private int connectTimeout;
    private int readTimeout;

    private transient NomadApiClient client;

    @DataBoundConstructor
    public NomadCloud(String name) {
        super(name);
    }

    /**
     * Copy constructor. Allows to create copies of the original Nomad
     * cloud. Since it's a singleton by design, this method also allows
     * specifying a new name.
     *
     * @param name Name of the cloud to be created
     * @param source Source Nomad cloud implementation
     * @since 0.13
     */
    public NomadCloud(@NonNull String name, @NonNull NomadCloud source) {
        super(name);
        this.templates.addAll(source.templates);
        this.serverUrl = source.serverUrl;
        this.jenkinsUrl = source.jenkinsUrl;
        this.jenkinsTunnel = source.jenkinsTunnel;
        this.containerCap = source.containerCap;
        this.retentionTimeout = source.retentionTimeout;
        this.connectTimeout = source.connectTimeout;
    }

    public int getRetentionTimeout() {
        return retentionTimeout;
    }

    @DataBoundSetter
    public void setRetentionTimeout(int retentionTimeout) {
        this.retentionTimeout = retentionTimeout;
    }

    @Nonnull
    public List<NomadJobTemplate> getTemplates() {
        return templates;
    }

    /**
     * Returns all Nomad job templates for this cloud including the dynamic ones.
     *
     * @return all Nomad job templates for this cloud including the dynamic ones.
     */
    @Nonnull
    public List<NomadJobTemplate> getAllTemplates() {
        return NomadJobTemplateSource.getAll(this);
    }

    @DataBoundSetter
    public void setTemplates(@Nonnull List<NomadJobTemplate> templates) {
        this.templates = new ArrayList<>(templates);
    }

    public String getServerUrl() {
        return serverUrl;
    }

    @DataBoundSetter
    public void setServerUrl(@Nonnull String serverUrl) {
        this.serverUrl = serverUrl;
    }

    @CheckForNull
    public String getJenkinsUrl() {
        return jenkinsUrl;
    }

    @CheckForNull
    public String getSlaveUrl() {
        return Jenkins.getInstance().getRootUrl() + "jnlpJars/slave.jar";
    }

    /**
     * Returns Jenkins URL to be used by agents launched by this cloud. Always
     * ends with a trailing slash.
     *
     * Uses in order:
     * * cloud configuration
     * * environment variable <b>NOMAD_JENKINS_URL</b>
     * * Jenkins Location URL
     *
     * @return Jenkins URL to be used by agents launched by this cloud. Always
     * ends with a trailing slash.
     * @throws IllegalStateException if no Jenkins URL could be computed.
     */
    @Nonnull
    public String getJenkinsUrlOrDie() {
        JenkinsLocationConfiguration locationConfiguration = JenkinsLocationConfiguration.get();
        String url = StringUtils.defaultIfBlank(
                getJenkinsUrl(),
                StringUtils.defaultIfBlank(
                        System.getProperty("NOMAD_JENKINS_URL", System.getenv("NOMAD_JENKINS_URL")),
                        locationConfiguration.getUrl()
                )
        );
        if (url == null) {
            throw new IllegalStateException("Jenkins URL for Nomad is null");
        }
        url = url.endsWith("/") ? url : url + "/";
        return url;
    }

    @DataBoundSetter
    public void setJenkinsUrl(String jenkinsUrl) {
        this.jenkinsUrl = Util.fixEmptyAndTrim(jenkinsUrl);
    }

    public String getJenkinsTunnel() {
        return jenkinsTunnel;
    }

    @DataBoundSetter
    public void setJenkinsTunnel(String jenkinsTunnel) {
        this.jenkinsTunnel = Util.fixEmpty(jenkinsTunnel);
    }

    public int getContainerCap() {
        return containerCap;
    }

    @DataBoundSetter
    public void setContainerCapStr(String containerCapStr) {
        if (containerCapStr.equals("")) {
            this.containerCap = Integer.MAX_VALUE;
        } else {
            this.containerCap = Integer.parseInt(containerCapStr);
        }
    }

    public String getContainerCapStr() {
        if (containerCap == Integer.MAX_VALUE) {
            return "";
        } else {
            return String.valueOf(containerCap);
        }
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /**
     * Connects to Nomad.
     *
     * @return Nomad client.
     */
    @SuppressFBWarnings({"IS2_INCONSISTENT_SYNC", "DC_DOUBLECHECK"})
    public NomadApiClient connect() throws UnrecoverableKeyException, NoSuchAlgorithmException, KeyStoreException,
            IOException, CertificateEncodingException {

        LOGGER.log(Level.FINE, "Building connection to Nomad {0} URL {1}",
                new String[]{getDisplayName(), serverUrl});
        NomadApiConfiguration config = new NomadApiConfiguration.Builder()
                .setAddress(serverUrl)
                .build();
        client = new NomadApiClient(config);
        LOGGER.log(Level.FINE, "Connected to Nomad {0} URL {1}", new String[]{getDisplayName(), serverUrl});
        return client;
    }

    private String getIdForLabel(Label label) {
        if (label == null) {
            return DEFAULT_ID;
        }
        return "jenkins/" + label.getName();
    }

//    Map<String, String> getLabelsMap(Set<LabelAtom> labelSet) {
//        ImmutableMap.Builder<String, String> builder = ImmutableMap.<String, String>builder();
//        if (!labelSet.isEmpty()) {
//            for (LabelAtom label : labelSet) {
//                builder.put(getIdForLabel(label), "true");
//            }
//        }
//        return builder.build();
//    }

    @Override
    public synchronized Collection<NodeProvisioner.PlannedNode> provision(@CheckForNull final Label label, final int excessWorkload) {
        try {
            Set<String> allInProvisioning = InProvisioning.getAllInProvisioning(label);
            LOGGER.log(Level.FINE, "In provisioning : {0}", allInProvisioning);
            int toBeProvisioned = Math.max(0, excessWorkload - allInProvisioning.size());
            LOGGER.log(Level.INFO, "Excess workload after pending Nomad nodes: {0}", toBeProvisioned);

            List<NodeProvisioner.PlannedNode> r = new ArrayList<>();

            for (NomadJobTemplate t : getTemplatesFor(label)) {
                LOGGER.log(Level.INFO, "Template: {0}", t.getDisplayName());
                for (int i = 1; i <= toBeProvisioned; i++) {
                    if (!addProvisionedSlave(t, label)) {
                        break;
                    }
                    r.add(PlannedNodeBuilderFactory.createInstance().cloud(this).template(t).label(label).build());
                }
                if (r.size() > 0) {
                    // Already found a matching template
                    return r;
                }
            }
            return r;
        } catch (NomadException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException || cause instanceof ConnectException || cause instanceof UnknownHostException) {
                LOGGER.log(Level.WARNING, "Failed to connect to Nomad at {0}: {1}",
                        new String[]{serverUrl, cause.getMessage()});
            } else {
                LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Nomad",
                        cause != null ? cause : e);
            }
        } catch (ConnectException e) {
            LOGGER.log(Level.WARNING, "Failed to connect to Nomad at {0}", serverUrl);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to count the # of live instances on Nomad", e);
        }
        return Collections.emptyList();
    }

    /**
     * Check not too many already running.
     *
     */
    private boolean addProvisionedSlave(@Nonnull NomadJobTemplate template, @CheckForNull Label label) throws Exception {
        if (containerCap == 0) {
            return true;
        }

//        NomadApiClient c;
//        c = connect();
        LOGGER.log(Level.SEVERE, "implement me");
//        PodList slaveList = client.pods().inNamespace(templateNamespace).withLabels(getLabels()).list();
//        List<Pod> slaveListItems = slaveList.getItems();
//
//        Map<String, String> labelsMap = getLabelsMap(template.getLabelSet());
//        PodList namedList = client.pods().inNamespace(templateNamespace).withLabels(labelsMap).list();
//        List<Pod> namedListItems = namedList.getItems();
//
//        if (slaveListItems != null && containerCap <= slaveListItems.size()) {
//            LOGGER.log(Level.INFO,
//                    "Total container cap of {0} reached, not provisioning: {1} running or errored in namespace {2} with Kubernetes labels {3}",
//                    new Object[]{containerCap, slaveListItems.size(), client.getNamespace(), getLabels()});
//            return false;
//        }
//
//        if (namedListItems != null && slaveListItems != null && template.getInstanceCap() <= namedListItems.size()) {
//            LOGGER.log(Level.INFO,
//                    "Template instance cap of {0} reached for template {1}, not provisioning: {2} running or errored in namespace {3} with label \"{4}\" and Kubernetes labels {5}",
//                    new Object[]{template.getInstanceCap(), template.getName(), slaveListItems.size(),
//                        client.getNamespace(), label == null ? "" : label.toString(), labelsMap});
//            return false; // maxed out
//        }
        return true;
    }

    @Override
    public boolean canProvision(@CheckForNull Label label) {
        //return getTemplate(label) != null;
        return true;
    }

    /**
     * Gets {@link NomadJobTemplate} that has the matching {@link Label}.
     *
     * @param label label to look for in templates
     * @return the template
     */
    public NomadJobTemplate getTemplate(@CheckForNull Label label) {
        for (NomadJobTemplate t : templates) {
            if ((label == null && t.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(t.getLabelSet()))) {
                return t;
            }
        }
        return null;
    }

    /**
     * Gets all JobTemplates that have the matching {@link Label}.
     *
     * @param label label to look for in templates
     * @return list of matching templates
     */
    public List<NomadJobTemplate> getTemplatesFor(@CheckForNull Label label) {
        return NomadJobTemplateFilter.applyAll(this, getAllTemplates(), label);
    }

    /**
     * Add a new template to the cloud
     *
     * @param t docker template
     */
    public void addTemplate(NomadJobTemplate t) {
        this.templates.add(t);
        // t.parent = this;
    }

    /**
     * Remove a
     *
     * @param t docker template
     */
    public void removeTemplate(NomadJobTemplate t) {
        this.templates.remove(t);
    }

    /**
     * Add a dynamic job template. Won't be displayed in UI, and persisted
     * separately from the cloud instance.
     *
     * @param t the template to add
     */
    public void addDynamicTemplate(NomadJobTemplate t) {
        NomadJobTemplateMap.get().addTemplate(this, t);
    }

    /**
     * Remove a dynamic job template.
     *
     * @param t the template to remove
     */
    public void removeDynamicTemplate(NomadJobTemplate t) {
        NomadJobTemplateMap.get().removeTemplate(this, t);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {

        @Override
        public String getDisplayName() {
            return "Nomad";
        }

        public FormValidation doTestConnection(@QueryParameter String name, @QueryParameter String serverUrl, @QueryParameter String credentialsId,
                @QueryParameter String serverCertificate,
                @QueryParameter boolean skipTlsVerify,
                @QueryParameter int connectionTimeout,
                @QueryParameter int readTimeout) throws Exception {

            if (StringUtils.isBlank(name)) {
                return FormValidation.error("name is required");
            }

            try {
                NomadApiConfiguration config = new NomadApiConfiguration.Builder()
                        .setAddress(serverUrl)
                        .build();
                NomadApiClient client = new NomadApiClient(config);

                // test listing jobs
                client.getJobsApi().list();
                return FormValidation.ok("Connection test successful");
            } catch (NomadException e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error("Error testing connection %s: %s", serverUrl, e.getCause() == null
                        ? e.getMessage()
                        : String.format("%s: %s", e.getCause().getClass().getName(), e.getCause().getMessage()));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, String.format("Error testing connection %s", serverUrl), e);
                return FormValidation.error("Error testing connection %s: %s", serverUrl, e.getMessage());
            }
        }

//        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String serverUrl) {
//            return new StandardListBoxModel().withEmptySelection() //
//                    .withMatching( //
//                            CredentialsMatchers.anyOf(
//                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class),
//                                    CredentialsMatchers.instanceOf(FileCredentials.class),
//                                    CredentialsMatchers.instanceOf(TokenProducer.class),
//                                    CredentialsMatchers.instanceOf(
//                                            org.jenkinsci.plugins.kubernetes.credentials.TokenProducer.class),
//                                    CredentialsMatchers.instanceOf(StandardCertificateCredentials.class),
//                                    CredentialsMatchers.instanceOf(StringCredentials.class)), //
//                            CredentialsProvider.lookupCredentials(StandardCredentials.class, //
//                                    getInstance, //
//                                    ACL.SYSTEM, //
//                                    serverUrl != null ? URIRequirementBuilder.fromUri(serverUrl).build()
//                                            : Collections.EMPTY_LIST //
//                            ));
//
//        }
        public FormValidation doCheckMaxRequestsPerHostStr(@QueryParameter String value) throws IOException, ServletException {
            try {
                Integer.parseInt(value);
                return FormValidation.ok();
            } catch (NumberFormatException e) {
                return FormValidation.error("Please supply an integer");
            }
        }
    }

    @Override
    public String toString() {
        return String.format("NomadCloud name: %s serverUrl: %s", name, serverUrl);
    }

    @Extension
    public static class JobTemplateSourceImpl extends NomadJobTemplateSource {

        @Nonnull
        @Override
        public List<NomadJobTemplate> getList(@Nonnull NomadCloud cloud) {
            return cloud.getTemplates();
        }
    }
}
