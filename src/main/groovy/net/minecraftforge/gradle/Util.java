/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.TaskExecutionRequest;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/** Internal utilities. Documented for maintainability, NOT for public consumption. */
final class Util {
    /// @see #launcherFor(JavaPluginExtension, JavaToolchainService, JavaLanguageVersion)
    static Provider<JavaLauncher> launcherFor(JavaPluginExtension java, JavaToolchainService javaToolchains, int version) {
        return launcherFor(java, javaToolchains, JavaLanguageVersion.of(version));
    }

    /// Gets the Java launcher that [can compile or run][JavaLanguageVersion#canCompileOrRun(JavaLanguageVersion)] the
    /// given version.
    ///
    /// If the currently running Java toolchain is able to compile and run the given version, it will be used instead.
    ///
    /// @param java           The Java plugin extension of the currently-used toolchain
    /// @param javaToolchains The Java toolchain service to get the Java launcher from
    /// @param version        The version of Java required
    /// @return A provider for the Java launcher
    static Provider<JavaLauncher> launcherFor(JavaPluginExtension java, JavaToolchainService javaToolchains, JavaLanguageVersion version) {
        var currentToolchain = java.getToolchain();
        return currentToolchain.getLanguageVersion().getOrElse(JavaLanguageVersion.current()).canCompileOrRun(version)
            ? javaToolchains.launcherFor(currentToolchain)
            : launcherForStrictly(javaToolchains, version);
    }

    /// @see #launcherForStrictly(JavaToolchainService, JavaLanguageVersion)
    static Provider<JavaLauncher> launcherForStrictly(JavaToolchainService javaToolchains, int version) {
        return launcherForStrictly(javaToolchains, JavaLanguageVersion.of(version));
    }

    /// Gets the Java launcher strictly for the given version, even if the currently running Java toolchain is higher
    /// than it.
    ///
    /// @param javaToolchains The Java toolchain service to get the Java launcher from
    /// @param version        The version of Java required
    /// @return A provider for the Java launcher
    static Provider<JavaLauncher> launcherForStrictly(JavaToolchainService javaToolchains, JavaLanguageVersion version) {
        return javaToolchains.launcherFor(spec -> spec.getLanguageVersion().set(version));
    }

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

    static @Nullable <T extends Collection<?>> T nullIfEmpty(@Nullable T c) {
        return c == null || c.isEmpty() ? null : c;
    }

    static @Nullable <T extends Map<?, ?>> T nullIfEmpty(@Nullable T m) {
        return m == null || m.isEmpty() ? null : m;
    }

    static @Nullable String nullIfEmpty(@Nullable String s) {
        return s == null || s.isBlank() ? null : s;
    }

    static boolean isTrue(Provider<? extends String> provider) {
        return provider.map(Boolean::parseBoolean).getOrElse(false);
    }

    static <T> T tryElse(Callable<? extends T> value, T orElse) {
        try {
            return Objects.requireNonNull(value.call());
        } catch (Throwable e) {
            return orElse;
        }
    }

    /// Ensures that a given task is run first in the task graph for the given project.
    ///
    /// This *does not* break the configuration cache as long as the task is always applied using this.
    ///
    /// @param project The project
    /// @param task    The task to run first
    static void runFirst(Project project, TaskProvider<?> task) {
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
    }
}
