package info.multani.jenkins.plugins.nomad;
                                                                                                                                                                                                                                                                        

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

/**
 * Implementation of {@link NomadJobTemplateFilter} filtering pod templates matching the right label.
 */
@Extension
public class NomadJobTemplateLabelFilter extends NomadJobTemplateFilter {
    @Override
    protected NomadJobTemplate transform(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate podTemplate, @CheckForNull Label label) {
        if ((label == null && podTemplate.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(podTemplate.getLabelSet()))) {
            return podTemplate;
        }
        return null;
    }
}
