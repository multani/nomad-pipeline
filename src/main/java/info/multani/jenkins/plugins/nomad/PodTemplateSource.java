package info.multani.jenkins.plugins.nomad;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;

import hudson.ExtensionList;
import hudson.ExtensionPoint;

/**
 * A source of pod templates.
 */
public abstract class PodTemplateSource implements ExtensionPoint {
    public static List<NomadJobTemplate> getAll(@Nonnull NomadCloud cloud) {
        return ExtensionList.lookup(PodTemplateSource.class)
                .stream()
                .map(s -> s.getList(cloud))
                .flatMap(Collection::stream)
                .collect(toList());
    }

    /**
     * The list of {@link NomadJobTemplate} contributed by this implementation.
     * @return The list of {@link NomadJobTemplate} contributed by this implementation.
     * @param cloud
     */
    @Nonnull
    protected abstract List<NomadJobTemplate> getList(@Nonnull NomadCloud cloud);
}
