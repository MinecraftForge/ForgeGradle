/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
    static String artifactPath(String group, String name, String version, @Nullable String classifier, String extension) {
        return MessageFormat.format("{0}/{1}/{2}/{1}-{2}{3}.{4}",
            group.replace('.', '/'),
            name,
            version,
            classifier == null ? "" : "-" + classifier,
            extension
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
