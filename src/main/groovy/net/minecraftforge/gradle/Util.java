/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.TaskExecutionRequest;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

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

    static @Nullable String getProperty(ProviderFactory providers, String property) {
        return providers.gradleProperty(property).orElse(providers.systemProperty(property)).getOrNull();
    }

    static @Nullable Boolean getBoolean(ProviderFactory providers, String property) {
        var gradleBoolean = getBoolean(providers.gradleProperty(property));
        return gradleBoolean != null ? gradleBoolean : getBoolean(providers.systemProperty(property));
    }

    private static @Nullable Boolean getBoolean(Provider<? extends String> provider) {
        if (Boolean.TRUE.equals(provider.map("true"::equalsIgnoreCase).getOrNull())) return true;
        if (Boolean.FALSE.equals(provider.map("false"::equalsIgnoreCase).getOrNull())) return false;
        return null;
    }

    static boolean isTrue(ProviderFactory providers, String property) {
        return isTrue(providers.gradleProperty(property)) || isTrue(providers.systemProperty(property));
    }

    private static boolean isTrue(Provider<? extends String> provider) {
        return Boolean.TRUE.equals(getBoolean(provider));
    }

    static boolean isFalse(ProviderFactory providers, String property) {
        return isFalse(providers.gradleProperty(property)) || isFalse(providers.systemProperty(property));
    }

    private static boolean isFalse(Provider<? extends String> provider) {
        return Boolean.FALSE.equals(getBoolean(provider));
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

    static <T> ActionableLazy<T> lazy(Callable<T> callable) {
        return lazy(Closures.callable(callable));
    }

    static <T> ActionableLazy<T> lazy(Closure<T> closure) {
        return new ActionableLazy.Simple<>(closure);
    }

    static String toString(ModuleVersionSelector module) {
        var version = module.getVersion();
        return "%s:%s%s".formatted(
            module.getGroup(),
            module.getName(),
            version != null ? ':' + version : ""
        );
    }

    static String toString(Dependency dependency) {
        var group = dependency.getGroup();
        var version = dependency.getVersion();
        var reason = dependency.getReason();
        return "(%s) %s%s%s%s%s".formatted(
            dependency.getClass().getName(),
            group != null ? group + ':' : "",
            dependency.getName(),
            version != null ? ':' + version : "",
            reason != null ? " (" + reason + ')' : "",
            dependency instanceof FileCollectionDependency files ? " [%s]".formatted(String.join(", ", files.getFiles().getFiles().stream().map(File::getAbsolutePath).map(CharSequence.class::cast)::iterator)) : ""
        );
    }

    sealed interface ActionableLazy<T> extends Supplier<T>, Callable<T> {
        default ActionableLazy<T> orElse(ActionableLazy<T> ifAbsent) {
            return this.isPresent() ? this : ifAbsent;
        }

        boolean isPresent();

        default void ifPresent(Action<? super T> action) {
            if (this.isPresent())
                action.execute(this.get());
        }

        void map(Action<? super T> action);

        ActionableLazy<T> copy();

        @Override
        default T call() {
            return this.get();
        }

        /// Represents a lazily computed value with the ability to optionally work with it using [#ifPresent(Action)] and
        /// safely mutate it using [#map(Action)].
        final class Simple<T> implements ActionableLazy<T> {
            private final Closure<T> closure;
            private @Nullable T value;

            private boolean present = false;

            private Closure<T> modifications = Closures.unaryOperator(UnaryOperator.identity());

            private Simple(Closure<T> closure) {
                this.closure = closure.compose(Closures.runnable(() -> this.present = true));
            }

            public void map(Action<? super T> action) {
                this.present = true;
                this.modifications = this.modifications.andThen(Closures.unaryOperator(value -> {
                    action.execute(value);
                    return value;
                }));
            }

            public boolean isPresent() {
                return this.present;
            }

            @Override
            @SuppressWarnings("ClassEscapesDefinedScope") // class is package-private
            public ActionableLazy<T> copy() {
                var ret = new Simple<>(this.closure);
                ret.value = this.value;
                ret.present = this.present;
                ret.modifications = this.modifications;
                return ret;
            }

            @Override
            public T get() {
                return this.value == null ? this.value = Closures.invoke(this.closure.andThen(this.modifications)) : this.value;
            }
        }
    }
}
