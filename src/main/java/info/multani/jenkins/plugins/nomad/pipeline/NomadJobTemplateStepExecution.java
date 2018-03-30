package info.multani.jenkins.plugins.nomad.pipeline;

import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.lang.RandomStringUtils;
import info.multani.jenkins.plugins.nomad.NomadCloud;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

import com.google.common.base.Strings;
import com.hashicorp.nomad.javasdk.NomadApiClient;

import hudson.AbortException;
import hudson.model.Run;
import hudson.slaves.Cloud;
import jenkins.model.Jenkins;

public class NomadJobTemplateStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateStepExecution.class.getName());

    private static final long serialVersionUID = -6139090518333729333L;

    private static final transient String NAME_FORMAT = "%s-%s";

    @SuppressFBWarnings(value = "SE_TRANSIENT_FIELD_NOT_RESTORED", justification = "not needed on deserialization")
    private final transient NomadJobTemplateStep step;
    private final String cloudName;

    private NomadJobTemplate newTemplate = null;

    NomadJobTemplateStepExecution(NomadJobTemplateStep step, StepContext context) {
        super(context);
        this.step = step;
        this.cloudName = step.getCloud();
    }

    @Override
    public boolean start() throws Exception {

        Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            throw new AbortException(String.format("Cloud does not exist: %s", cloudName));
        }
        if (!(cloud instanceof NomadCloud)) {
            throw new AbortException(String.format("Cloud is not a Nomad cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }
        NomadCloud nomadCloud = (NomadCloud) cloud;

        Run<?, ?> run = getContext().get(Run.class);
        PodTemplateAction podTemplateAction = run.getAction(PodTemplateAction.class);
        NamespaceAction namespaceAction = run.getAction(NamespaceAction.class);
        String parentTemplates = podTemplateAction != null ? podTemplateAction.getParentTemplates() : null;

        //Let's generate a random name based on the user specified to make sure that we don't have
        //issues with concurrent builds, or messing with pre-existing configuration
        String randString = RandomStringUtils.random(5, "bcdfghjklmnpqrstvwxz0123456789");
        String name = String.format(NAME_FORMAT, step.getName(), randString);
        String namespace = checkNamespace(nomadCloud, namespaceAction);

        newTemplate = new NomadJobTemplate();
        newTemplate.setName(name);
        newTemplate.setNamespace(namespace);
//        newTemplate.setInheritFrom(!Strings.isNullOrEmpty(parentTemplates) ? parentTemplates : step.getInheritFrom());
        newTemplate.setInstanceCap(step.getInstanceCap());
        newTemplate.setIdleMinutes(step.getIdleMinutes());
        newTemplate.setSlaveConnectTimeout(step.getSlaveConnectTimeout());
        newTemplate.setLabel(step.getLabel());
        newTemplate.setEnvVars(step.getEnvVars());
//        newTemplate.setVolumes(step.getVolumes());
//        newTemplate.setCustomWorkspaceVolumeEnabled(step.getWorkspaceVolume() != null);
//        newTemplate.setWorkspaceVolume(step.getWorkspaceVolume());
        newTemplate.setContainers(step.getContainers());
//        newTemplate.setNodeSelector(step.getNodeSelector());
//        newTemplate.setNodeUsageMode(step.getNodeUsageMode());
//        newTemplate.setServiceAccount(step.getServiceAccount());
//        newTemplate.setAnnotations(step.getAnnotations());
//        newTemplate.setImagePullSecrets(
//                step.getImagePullSecrets().stream().map(x -> new PodImagePullSecret(x)).collect(toList()));

//        if(step.getActiveDeadlineSeconds() != 0) {
//            newTemplate.setActiveDeadlineSeconds(step.getActiveDeadlineSeconds());
//        }
        nomadCloud.addDynamicTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new PodTemplateCallback(newTemplate)).start();

        PodTemplateAction.push(run, name);
        NamespaceAction.push(run, namespace);
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        new PodTemplateAction(getContext().get(Run.class)).pop();
    }

    private String checkNamespace(NomadCloud nomadCloud, @CheckForNull NamespaceAction namespaceAction) {
        String namespace = null;
        if (!Strings.isNullOrEmpty(step.getNamespace())) {
            namespace = step.getNamespace();
        } else if ((namespaceAction != null) && (!Strings.isNullOrEmpty(namespaceAction.getNamespace()))) {
            namespace = namespaceAction.getNamespace();
        } else {
            namespace = nomadCloud.getNamespace();
        }
        return namespace;
    }

    /**
     * Re-inject the dynamic template when resuming the pipeline
     */
    @Override
    public void onResume() {
        super.onResume();
        Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
        if (cloud == null) {
            throw new RuntimeException(String.format("Cloud does not exist: %s", cloudName));
        }
        if (!(cloud instanceof NomadCloud)) {
            throw new RuntimeException(String.format("Cloud is not a Nomad cloud: %s (%s)", cloudName,
                    cloud.getClass().getName()));
        }
        NomadCloud nomadCloud = (NomadCloud) cloud;
        nomadCloud.addDynamicTemplate(newTemplate);
    }

    private class PodTemplateCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6043919968776851324L;

        private final NomadJobTemplate jobTemplate;

        private PodTemplateCallback(NomadJobTemplate podTemplate) {
            this.jobTemplate = podTemplate;
        }

        @Override
        /**
         * Remove the template after step is done
         */
        protected void finished(StepContext context) throws Exception {
            Cloud cloud = Jenkins.getInstance().getCloud(cloudName);
            if (cloud == null) {
                LOGGER.log(Level.WARNING, "Cloud {0} no longer exists, cannot delete job template {1}",
                        new Object[]{cloudName, jobTemplate.getName()});
                return;
            }
            if (cloud instanceof NomadCloud) {
                LOGGER.log(Level.INFO, "Removing job template and deleting job {1} from cloud {0}",
                        new Object[]{cloud.name, jobTemplate.getName()});
                NomadCloud nomadCloud = (NomadCloud) cloud;
                nomadCloud.removeDynamicTemplate(jobTemplate);
//                NomadApiClient client = nomadCloud.connect();
//                Boolean deleted = client.jobs().withName(jobTemplate.getName()).delete();
                boolean deleted = true; // TODO
                if (!Boolean.TRUE.equals(deleted)) {
                    LOGGER.log(Level.WARNING, "Failed to delete job {1}: not found",
                            new String[]{jobTemplate.getName()});
                    return;
                }
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not a NomadCloud: {0} {1}",
                        new String[]{cloud.name, cloud.getClass().getName()});
            }
        }
    }
}
