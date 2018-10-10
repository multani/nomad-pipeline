package info.multani.jenkins.plugins.nomad;
                                                                                                                                                                                                                                                                        

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.Extension;
import hudson.model.Label;
import hudson.model.Node;

/**
 * Implementation of {@link NomadJobTemplateFilter} filtering job templates matching the right label.
 */
@Extension
public class NomadJobTemplateLabelFilter extends NomadJobTemplateFilter {
    @Override
    protected NomadJobTemplate transform(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate jobTemplate, @CheckForNull Label label) {
        if ((label == null && jobTemplate.getNodeUsageMode() == Node.Mode.NORMAL) || (label != null && label.matches(jobTemplate.getLabelSet()))) {
            return jobTemplate;
        }
        return null;
    }
}
