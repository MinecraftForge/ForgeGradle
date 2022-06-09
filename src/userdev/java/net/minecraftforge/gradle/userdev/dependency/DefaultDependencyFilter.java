package net.minecraftforge.gradle.userdev.dependency;

import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolvedDependency;

import java.util.Set;

public class DefaultDependencyFilter extends AbstractDependencyFilter
{
    public DefaultDependencyFilter(final Project project)
    {
        super(project);
    }

    @Override
    protected void resolve(final Set<ResolvedDependency> dependencies, final Set<ResolvedDependency> includedDependencies, final Set<ResolvedDependency> excludedDependencies)
    {
        dependencies.forEach(resolvedDependency -> {
            if (isIncluded(resolvedDependency)) {
                includedDependencies.add(resolvedDependency);
            } else {
                excludedDependencies.add(resolvedDependency);
            }

            resolve(resolvedDependency.getChildren(), includedDependencies, excludedDependencies);
        });
    }
}
