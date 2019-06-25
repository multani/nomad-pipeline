package info.multani.jenkins.plugins.nomad;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Filters a job template according to criteria.
 */
public abstract class NomadJobTemplateFilter implements ExtensionPoint {

    /**
     * Returns a list of all implementations of {@link NomadJobTemplateFilter}.
     *
     * @return a list of all implementations of {@link NomadJobTemplateFilter}.
     */
    public static ExtensionList<NomadJobTemplateFilter> all() {
        return ExtensionList.lookup(NomadJobTemplateFilter.class);
    }

    /**
     * Pass the given job templates list into all filters implementations.
     *
     * @param cloud The cloud instance the job templates are getting considered
     * for
     * @param jobTemplates The initial list of job templates
     * @param label The label that was requested for provisioning
     * @return The job template list after filtering
     */
    public static List<NomadJobTemplate> applyAll(@Nonnull NomadCloud cloud, @Nonnull List<NomadJobTemplate> jobTemplates, @CheckForNull Label label) {
        List<NomadJobTemplate> result = new ArrayList<>();
        for (NomadJobTemplate t : jobTemplates) {
            NomadJobTemplate output = null;
            for (NomadJobTemplateFilter f : all()) {
                output = f.transform(cloud, t, label);
                if (output == null) {
                    break;
                }
            }
            if (output != null) {
                result.add(output);
            }
        }
        return result;
    }

    /**
     * Transforms a job template definition.
     *
     * @param cloud The {@link NomadCloud} instance the {@link NomadJobTemplate}
     * instances will be scheduled into.
     * @param jobTemplate The input job template to process.
     * @param label The label that was requested for provisioning
     * @return A new job template after transformation. It can be null if the
     * filter denies access to the given job template.
     */
    @CheckForNull
    protected abstract NomadJobTemplate transform(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate jobTemplate, @CheckForNull Label label);
}
