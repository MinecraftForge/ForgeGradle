/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.userdev.dependency;

import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class AbstractDependencyFilter implements DependencyFilter
{
    private final Project project;

    protected final List<Spec<? super ArtifactIdentifier>> includeSpecs = new ArrayList<>();
    protected final List<Spec<? super ArtifactIdentifier>> excludeSpecs = new ArrayList<>();

    AbstractDependencyFilter(Project project) {
        this.project = project;
    }

    abstract protected void resolve(
      Set<ResolvedDependency> dependencies,
      Set<ResolvedDependency> includedDependencies,
      Set<ResolvedDependency> excludedDependencies);

    public FileCollection resolve(Configuration configuration) {
        Set<ResolvedDependency> includedDeps = new HashSet<>();
        Set<ResolvedDependency> excludedDeps = new HashSet<>();
        resolve(configuration.getResolvedConfiguration().getFirstLevelModuleDependencies(), includedDeps, excludedDeps);

        return project.files(includedDeps.stream()
                               .flatMap(dep -> dep.getAllModuleArtifacts().stream())
                               .map(ResolvedArtifact::getFile)
                               .collect(Collectors.toList()));
    }

    public FileCollection resolve(Collection<Configuration> configurations) {
        return configurations.stream().map(this::resolve).reduce(FileCollection::plus).orElse(project.files());
    }

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter exclude(Spec<? super ArtifactIdentifier> spec) {
        excludeSpecs.add(spec);
        return this;
    }

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    public DependencyFilter include(Spec<? super ArtifactIdentifier> spec) {
        includeSpecs.add(spec);
        return this;
    }

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     * @param notation
     * @return
     */
    public Spec<? super ArtifactIdentifier> project(Map<String, ?> notation) {
        return dependency(project.getDependencies().project(notation));
    }

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    public Spec<? super ArtifactIdentifier> project(String notation) {
        return dependency(project.getDependencies().project(ImmutableMap.of("path", notation, "configuration", "default")));
    }

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * @param notation
     * @return
     */
    public Spec<? super ArtifactIdentifier> dependency(Object notation) {
        return dependency(project.getDependencies().create(notation));
    }

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     * @param dependency
     * @return
     */
    public Spec<? super ArtifactIdentifier> dependency(Dependency dependency) {
        return this.dependency(new Closure<Boolean>(null) {

            @SuppressWarnings("ConstantConditions")
            @Override
            public Boolean call(final Object it)
            {
                if (it instanceof ArtifactIdentifier) {
                    final ArtifactIdentifier identifier = (ArtifactIdentifier) it;
                    return (dependency.getGroup() == null || Pattern.matches(dependency.getGroup(), identifier.getGroup())) &&
                            (dependency.getName() == null || Pattern.matches(dependency.getName(), identifier.getName())) &&
                            (dependency.getVersion() == null || Pattern.matches(dependency.getVersion(), identifier.getVersion()));
                }

                return false;
            }
        });
    }

    /**
     * Create a spec that matches the provided closure
     * @param spec
     * @return
     */
    public Spec<? super ArtifactIdentifier> dependency(Closure<Boolean> spec) {
        return Specs.convertClosureToSpec(spec);
    }

    @Override
    public boolean isIncluded(ResolvedDependency dependency) {
        return isIncluded(
          new ArtifactIdentifier(dependency.getModuleGroup(), dependency.getModuleName(), dependency.getModuleVersion())
        );
    }

    @Override
    public boolean isIncluded(ExternalModuleDependency dependency) {
        return isIncluded(
          new ArtifactIdentifier(dependency.getGroup(), dependency.getName(), dependency.getVersion())
        );
    }

    @Override
    public boolean isIncluded(ArtifactIdentifier dependency) {
        boolean include = includeSpecs.isEmpty() || includeSpecs.stream().anyMatch(spec -> spec.isSatisfiedBy(dependency));
        boolean exclude = !excludeSpecs.isEmpty() && excludeSpecs.stream().anyMatch(spec -> spec.isSatisfiedBy(dependency));
        return include && !exclude;
    }


}
