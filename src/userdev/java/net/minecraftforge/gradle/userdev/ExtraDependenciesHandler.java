/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

final class ExtraDependenciesHandler {
    private ExtraDependenciesHandler() { }

    enum Scope {
        RUNTIME(SourceSet::getRuntimeClasspathConfigurationName, SourceSet::getRuntimeOnlyConfigurationName),
        COMPILE(SourceSet::getCompileClasspathConfigurationName, SourceSet::getCompileOnlyConfigurationName),
        ANNOTATION_PROCESSOR(SourceSet::getAnnotationProcessorConfigurationName, SourceSet::getAnnotationProcessorConfigurationName);

        private final Function<SourceSet, String> classpath;
        private final Function<SourceSet, String> declarable;

        Scope(Function<SourceSet, String> classpath, Function<SourceSet, String> declarable) {
            this.classpath = classpath;
            this.declarable = declarable;
        }

        private @Nullable Configuration getClasspathConfiguration(Project project, SourceSet sourceSet) {
            return project.getConfigurations().findByName(this.classpath.apply(sourceSet));
        }

        private @Nullable Configuration getDeclarableConfiguration(Project project, SourceSet sourceSet) {
            return project.getConfigurations().findByName(this.declarable.apply(sourceSet));
        }
    }

    static void handle(Project project, Scope scope, String group, String name, String artifact) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        for (SourceSet sourceSet : java.getSourceSets()) {
            Configuration classpath = scope.getClasspathConfiguration(project, sourceSet);
            if (!containsDependency(classpath, group, name))
                continue;

            ExternalModuleDependency dependency = project.getDependencyFactory().create(artifact);
            if (isMissingDependency(classpath, dependency)) {
                Configuration configuration = scope.getDeclarableConfiguration(project, sourceSet);
                if (configuration == null) continue;

                configuration.withDependencies(dependencies -> dependencies.add(dependency));
            }
        }
    }

    private static boolean containsDependency(@Nullable Configuration configuration, String group, String name) {
        if (configuration == null) return false;

        return !configuration.getAllDependencies().matching(
            dependency -> group.equals(dependency.getGroup()) && name.equals(dependency.getName())
        ).isEmpty();
    }

    private static boolean isMissingDependency(@Nullable Configuration configuration, Dependency dependency) {
        return configuration != null && configuration.getAllDependencies().matching(
            d -> Objects.equals(d.getGroup(), dependency.getGroup()) && Objects.equals(dependency.getName(), d.getName())
        ).isEmpty();
    }
}
