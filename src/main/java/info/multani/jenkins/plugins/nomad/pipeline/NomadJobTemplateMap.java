package info.multani.jenkins.plugins.nomad.pipeline;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.CopyOnWriteMap;
import info.multani.jenkins.plugins.nomad.NomadCloud;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import info.multani.jenkins.plugins.nomad.NomadJobTemplateSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/**
 * A map of {@link NomadCloud} -&gt; List of {@link NomadJobTemplate} instances.
 */
@Extension
public class NomadJobTemplateMap {
    private static final Logger LOGGER = Logger.getLogger(NomadJobTemplateMap.class.getName());

    public static NomadJobTemplateMap get() {
        return ExtensionList.lookupSingleton(NomadJobTemplateMap.class);
    }

    /**
     * List of Job Templates indexed by cloud name
     */
    private Map<String, List<NomadJobTemplate>> map = new CopyOnWriteMap.Hash<>();

    /**
     * Returns a read-only view of the templates available for the corresponding cloud instance.
     * @param cloud The Nomad cloud instance for which templates are needed
     * @return a read-only view of the templates available for the corresponding cloud instance.
     */
    @Nonnull
    public List<NomadJobTemplate> getTemplates(@Nonnull NomadCloud cloud) {
        return Collections.unmodifiableList(getOrCreateTemplateList(cloud));
    }

    private List<NomadJobTemplate> getOrCreateTemplateList(@Nonnull NomadCloud cloud) {
        List<NomadJobTemplate> jobTemplates = map.get(cloud.name);
        return jobTemplates == null ? new CopyOnWriteArrayList<>() : jobTemplates;
    }

    /**
     * Adds a template for the corresponding cloud instance.
     * @param cloud The cloud instance.
     * @param jobTemplate The job template to add.
     */
    public void addTemplate(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate jobTemplate) {
        List<NomadJobTemplate> list = getOrCreateTemplateList(cloud);
        list.add(jobTemplate);
        map.put(cloud.name, list);
    }

    public void removeTemplate(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate jobTemplate) {
        getOrCreateTemplateList(cloud).remove(jobTemplate);
    }

    @Extension
    public static class JobTemplateSourceImpl extends NomadJobTemplateSource {

        @Nonnull
        @Override
        public List<NomadJobTemplate> getList(@Nonnull NomadCloud cloud) {
            return NomadJobTemplateMap.get().getTemplates(cloud);
        }
    }

}
