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
        RUNTIME(SourceSet::getRuntimeClasspathConfigurationName, SourceSet::getRuntimeClasspathConfigurationName, SourceSet::getRuntimeOnlyConfigurationName),
        COMPILE(SourceSet::getCompileClasspathConfigurationName, SourceSet::getCompileClasspathConfigurationName, SourceSet::getCompileOnlyConfigurationName),
        ANNOTATION_PROCESSOR(SourceSet::getAnnotationProcessorConfigurationName, SourceSet::getRuntimeClasspathConfigurationName, SourceSet::getAnnotationProcessorConfigurationName);

        private final Function<SourceSet, String> classpath;
        private final Function<SourceSet, String> condition;
        private final Function<SourceSet, String> declarable;

        Scope(Function<SourceSet, String> classpath, Function<SourceSet, String> condition, Function<SourceSet, String> declarable) {
            this.classpath = classpath;
            this.condition = condition;
            this.declarable = declarable;
        }

        // Check this for dependency
        private @Nullable Configuration getClasspathConfiguration(Project project, SourceSet sourceSet) {
            return project.getConfigurations().findByName(this.classpath.apply(sourceSet));
        }

        // Check this for Forge
        private @Nullable Configuration getConditionConfiguration(Project project, SourceSet sourceSet) {
            return project.getConfigurations().findByName(this.condition.apply(sourceSet));
        }

        // Add dependency to this
        private @Nullable Configuration getDeclarableConfiguration(Project project, SourceSet sourceSet) {
            return project.getConfigurations().findByName(this.declarable.apply(sourceSet));
        }
    }

    static void handle(Project project, Scope scope, String group, String name, String artifact) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        for (SourceSet sourceSet : java.getSourceSets()) {
            if (isMissingDependency(scope.getConditionConfiguration(project, sourceSet), group, name))
                continue;

            ExternalModuleDependency dependency = project.getDependencyFactory().create(artifact);
            if (isMissingDependency(scope.getClasspathConfiguration(project, sourceSet), dependency)) {
                Configuration configuration = scope.getDeclarableConfiguration(project, sourceSet);
                if (configuration == null) continue;

                configuration.getDependencies().add(dependency);
            }
        }
    }

    private static boolean isMissingDependency(@Nullable Configuration configuration, String group, String name) {
        return configuration != null && configuration.getAllDependencies().matching(
            dependency -> group.equals(dependency.getGroup()) && name.equals(dependency.getName())
        ).isEmpty();
    }

    private static boolean isMissingDependency(@Nullable Configuration configuration, Dependency dependency) {
        return configuration != null && configuration.getAllDependencies().matching(
            d -> Objects.equals(d.getGroup(), dependency.getGroup()) && Objects.equals(dependency.getName(), d.getName())
        ).isEmpty();
    }
}
