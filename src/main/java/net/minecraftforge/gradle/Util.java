/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Internal utilities. Documented for maintainability, NOT for public consumption. */
final class Util extends SharedUtil {
    /// Gets the path to an artifact.
    ///
    /// @param group      The artifact group
    /// @param name       The artifact name
    /// @param version    The artifact version
    /// @param classifier The artifact classifier
    /// @param extension  The artifact extension
    /// @return The path to the artifact
    static String artifactPath(@Nullable String group, String name, @Nullable String version, @Nullable String classifier, @Nullable String extension) {
        return MessageFormat.format("{0}{1}/{2}/{1}-{3}{4}.{5}",
            group == null ? "" : group.replace('.', '/') + '/',
            name,
            version == null ? "/" : '/' + version + '/',
            version == null ? "" : version,
            classifier == null ? "" : "-" + classifier,
            extension == null ? "jar" : extension
        );
    }

    static <T extends Collection<?>> boolean isPresent(T c) {
        return !c.isEmpty();
    }

    static <T extends Map<?, ?>> boolean isPresent(T c) {
        return !c.isEmpty();
    }

    static boolean isPresent(String c) {
        return !c.isBlank();
    }

    static String dependencyToCamelCase(Dependency dependency) {
        var list = new ArrayList<String>(3);

        var group = dependency.getGroup();
        if (group != null)
            list.addAll(Arrays.asList(group.split("\\.")));

        list.add(dependency.getName());

        try {
            list.add(InvokerHelper.getProperty(dependency, "classifer").toString());
        } catch (Exception ignored) {
            // No classifier, not a problem
        }

        var builder = new StringBuilder(64);
        for (var s : list) {
            builder.append(StringGroovyMethods.capitalize(s));
        }
        return builder.toString();
    }

    // I know it's UGLY but this is for good reason, trust bro
    static boolean contains(ConfigurationContainer configurations, SourceSet sourceSet, Dependency dependency) {
        return contains(configurations, sourceSet.getCompileOnlyConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getCompileOnlyApiConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getCompileClasspathConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getAnnotationProcessorConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getApiConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getImplementationConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getApiElementsConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getRuntimeOnlyConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getRuntimeClasspathConfigurationName(), dependency) ||
            contains(configurations, sourceSet.getRuntimeElementsConfigurationName(), dependency);
    }

    private static boolean contains(ConfigurationContainer configurations, String configurationName, Dependency dependency) {
        var configuration = configurations.findByName(configurationName);
        return configuration != null && configuration.getDependencies().contains(dependency);
    }

    /// Ensures that a given task is run first in the task graph for the given project.
    ///
    /// This *does not* break the configuration cache as long as the task is always applied using this.
    ///
    /// @param project The project
    /// @param task    The task to run first
    static <T extends TaskProvider<?>> T runFirst(Project project, T task) {
        // copy the requests because the backed list isn't concurrent
        var requests = new ArrayList<>(project.getGradle().getStartParameter().getTaskRequests());

        // add the task to the front of the list
        requests.add(0, new TaskExecutionRequest() {
            @Override
            public List<String> getArgs() {
                return List.of(task.get().getPath());
            }

            @Override
            public @Nullable String getProjectPath() {
                return null;
            }

            @Override
            public @Nullable File getRootDir() {
                return null;
            }
        });

        // set the new requests
        project.getLogger().info("Adding task to beginning of task graph! Project: {}, Task: {}", project.getName(), task.getName());
        project.getGradle().getStartParameter().setTaskRequests(requests);
        return task;
    }
}
