package info.multani.jenkins.plugins.nomad.pipeline;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import info.multani.jenkins.plugins.nomad.NomadCloud;
import info.multani.jenkins.plugins.nomad.NomadJobTemplate;
import info.multani.jenkins.plugins.nomad.PodTemplateSource;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.util.CopyOnWriteMap;

/**
 * A map of {@link NomadCloud} -&gt; List of {@link NomadJobTemplate} instances.
 */
@Extension
public class PodTemplateMap {
    private static final Logger LOGGER = Logger.getLogger(PodTemplateMap.class.getName());

    public static PodTemplateMap get() {
        // TODO Replace with lookupSingleton post 2.87
        return ExtensionList.lookup(PodTemplateMap.class).get(PodTemplateMap.class);
    }

    /**
     * List of Pod Templates indexed by cloud name
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
        List<NomadJobTemplate> podTemplates = map.get(cloud.name);
        return podTemplates == null ? new CopyOnWriteArrayList<>() : podTemplates;
    }

    /**
     * Adds a template for the corresponding cloud instance.
     * @param cloud The cloud instance.
     * @param podTemplate The pod template to add.
     */
    public void addTemplate(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate podTemplate) {
        List<NomadJobTemplate> list = getOrCreateTemplateList(cloud);
        list.add(podTemplate);
        map.put(cloud.name, list);
    }

    public void removeTemplate(@Nonnull NomadCloud cloud, @Nonnull NomadJobTemplate podTemplate) {
        getOrCreateTemplateList(cloud).remove(podTemplate);
    }

    @Extension
    public static class PodTemplateSourceImpl extends PodTemplateSource {

        @Nonnull
        @Override
        public List<NomadJobTemplate> getList(@Nonnull NomadCloud cloud) {
            return PodTemplateMap.get().getTemplates(cloud);
        }
    }

}
