package info.multani.jenkins.plugins.nomad.pipeline;

import com.hashicorp.nomad.javasdk.EvaluationResponse;
import com.hashicorp.nomad.javasdk.NomadApiClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.model.Run;
import hudson.slaves.Cloud;
import info.multani.jenkins.plugins.nomad.NomadCloud;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class NomadJobTemplateStepExecution extends AbstractStepExecutionImpl {

    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateStepExecution.class.getName());

    private static final long serialVersionUID = -6139090518333729333L;


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
        NomadJobTemplateAction jobTemplateAction = run.getAction(NomadJobTemplateAction.class);
        String parentTemplates = jobTemplateAction != null ? jobTemplateAction.getParentTemplates() : null;

        newTemplate = new NomadJobTemplate();
        newTemplate.generateName(step.getName());
        newTemplate.setRegion(step.getRegion());
        newTemplate.setDatacenters(step.getDatacenters());
        newTemplate.setInstanceCap(step.getInstanceCap());
        newTemplate.setIdleMinutes(step.getIdleMinutes());
        newTemplate.setSlaveConnectTimeout(step.getSlaveConnectTimeout());
        newTemplate.setLabel(step.getLabel());
        newTemplate.setEnvVars(step.getEnvVars());
        newTemplate.setTaskGroups(step.getTaskGroups());
        newTemplate.setNodeUsageMode(step.getNodeUsageMode());

        nomadCloud.addDynamicTemplate(newTemplate);
        getContext().newBodyInvoker().withContext(step).withCallback(new NomadJobTemplateCallback(newTemplate)).start();

        NomadJobTemplateAction.push(run, newTemplate.getName());
        return false;
    }

    @Override
    public void stop(Throwable cause) throws Exception {
        new NomadJobTemplateAction(getContext().get(Run.class)).pop();
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

    private class NomadJobTemplateCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6043919968776851324L;

        private final NomadJobTemplate jobTemplate;

        private NomadJobTemplateCallback(NomadJobTemplate jobTemplate) {
            this.jobTemplate = jobTemplate;
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
                NomadApiClient client = nomadCloud.connect();
                
                LOGGER.log(Level.FINE, "Deregistering job {0} from cloud {0}",
                        new Object[]{jobTemplate.getName(), cloud.name});
                EvaluationResponse response = client.getJobsApi().deregister(jobTemplate.getName());
                LOGGER.log(Level.FINE, "Deregistered {0} using evaluation ID {1}",
                        new Object[]{jobTemplate.getName(), response.getValue()});
                
                boolean deleted = response.getHttpResponse().getStatusLine().getStatusCode() == 200;
                if (!deleted) {
                    LOGGER.log(Level.WARNING, "Failed to deregister job {0}: HTTP {1}",
                        new Object[]{jobTemplate.getName(),
                            response.getHttpResponse().getStatusLine().getStatusCode()});
                    return;
                }
            } else {
                LOGGER.log(Level.WARNING, "Cloud is not a NomadCloud: {0} {1}",
                        new String[]{cloud.name, cloud.getClass().getName()});
            }
        }
    }
}
