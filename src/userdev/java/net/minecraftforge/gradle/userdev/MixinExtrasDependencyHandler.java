/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.jetbrains.annotations.Nullable;

final class MixinExtrasDependencyHandler {
    private MixinExtrasDependencyHandler() { }

    static void handle(Project project, String artifact) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        for (SourceSet sourceSet : java.getSourceSets()) {
            if (!containsForge(project, sourceSet))
                continue;

            for (Configuration configuration : getConfigurationsToAddME(project, sourceSet)) {
                configuration.withDependencies(dependencies -> dependencies.add(project.getDependencies().create(artifact)));
            }
        }
    }

    private static boolean containsForge(Project project, SourceSet sourceSet) {
        Configuration configuration = project.getConfigurations().findByName(sourceSet.getCompileClasspathConfigurationName());
        if (configuration == null) return false;

        return !configuration.getAllDependencies().matching(
            dependency -> "net.minecraftforge".equals(dependency.getGroup()) && "forge".equals(dependency.getName())
        ).isEmpty();
    }

    private static Configuration[] getConfigurationsToAddME(Project project, SourceSet sourceSet) {
        ConfigurationContainer configurations = project.getConfigurations();
        boolean addToCompileOnly = isMissingMixinExtras(configurations.findByName(sourceSet.getCompileClasspathConfigurationName()));
        boolean addToAnnotationProcessor = isMissingMixinExtras(configurations.findByName(sourceSet.getAnnotationProcessorConfigurationName()));

        if (addToCompileOnly && addToAnnotationProcessor)
            return new Configuration[] {configurations.getByName(sourceSet.getCompileOnlyConfigurationName()), configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName())};
        else if (addToCompileOnly)
            return new Configuration[] {configurations.getByName(sourceSet.getCompileOnlyConfigurationName())};
        else if (addToAnnotationProcessor)
            return new Configuration[] {configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName())};
        else
            return new Configuration[] { };
    }

    private static boolean isMissingMixinExtras(@Nullable Configuration configuration) {
        return configuration != null && configuration.getAllDependencies().matching(
            dependency -> "io.github.llamalad7".equals(dependency.getGroup()) && "mixinextras-common".equals(dependency.getName())
        ).isEmpty();
    }
}
