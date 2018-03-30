package info.multani.jenkins.plugins.nomad;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Label;

/**
 * Filters a pod template according to criteria.
 */
public abstract class PodTemplateFilter implements ExtensionPoint {
    /**
     * Returns a list of all implementations of {@link PodTemplateFilter}.
     * @return a list of all implementations of {@link PodTemplateFilter}.
     */
    public static ExtensionList<PodTemplateFilter> all() {
        return ExtensionList.lookup(PodTemplateFilter.class);
    }

    /**
     * Pass the given pod templates list into all filters implementations.
     *
     * @param cloud The cloud instance the pod templates are getting considered for
     * @param podTemplates The initial list of pod templates
     * @param label The label that was requested for provisioning
     * @return The pod template list after filtering
     */
    public static List<NomadJobTemplate> applyAll(@Nonnull NomadCloud cloud, @Nonnull List<NomadJobTemplate> podTemplates, @CheckForNull Label label) {
        List<NomadJobTemplate> result = new ArrayList<>();
        for (NomadJobTemplate t : podTemplates) {
            NomadJobTemplate output = null;
            for (PodTemplateFilter f : all()) {
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
     * Transforms a pod template definition.
     *
     * @param cloud The {@link NomadCloud} instance the {@link NomadJobTemplate} instances will be scheduled into.
     * @param podTemplate The input pod template to process.
     * @param label The label that was requested for provisioning
     * @return A new pod template after transformation. It can be null if the filter denies access to the given pod template.
     */
    @CheckForNull
    protected abstract NomadJobTemplate transform(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate podTemplate, @CheckForNull Label label);
}
