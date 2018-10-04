package info.multani.jenkins.plugins.nomad;

import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.util.Collection;
import java.util.List;
import static java.util.stream.Collectors.toList;
import javax.annotation.Nonnull;

/**
 * A source of Nomad job templates.
 */
public abstract class NomadJobTemplateSource implements ExtensionPoint {
    public static List<NomadJobTemplate> getAll(@Nonnull NomadCloud cloud) {
        return ExtensionList.lookup(NomadJobTemplateSource.class)
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
