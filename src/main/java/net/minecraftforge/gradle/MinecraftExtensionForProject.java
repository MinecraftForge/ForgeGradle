/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

/// [Project][org.gradle.api.Project]-specific additions for the Minecraft extension. These will be accessible from the
/// `minecraft` DSL object within your project's buildscript.
///
/// @param <T> The type of closure owner used for [#dep(Object, Closure)]
/// @see MinecraftExtension
sealed public interface MinecraftExtensionForProject<T extends ClosureOwner<?>> extends MinecraftExtension permits MinecraftExtensionForProjectWithAccessTransformers, MinecraftExtensionInternal.ForProject {
    /// The collection of Slime Launcher options with which to create the launcher tasks.
    ///
    /// @return The collection of run task options
    NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns();

    /// Configures the Slime Launcher options for this project, which will be used to create the launcher tasks.
    ///
    /// @param closure The configuring closure
    void runs(
        @DelegatesTo(NamedDomainObjectContainer.class)
        @ClosureParams(value = FromString.class, options = "org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>")
        Closure<?> closure
    );

    /// Configures the Slime Launcher options for this project, which will be used to create the launcher tasks.
    ///
    /// @param action The configuring action
    default void runs(Action<? super NamedDomainObjectContainer<? extends SlimeLauncherOptions>> action) {
        this.runs(Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and configures it with the given
    /// closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    Provider<ExternalModuleDependency> dep(
        Object value,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = FromString.class, options = "T")
        Closure<?> closure
    );

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and applies the given action to
    /// it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(Object value, Action<? super T> action) {
        return this.dep(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(Object value) {
        return this.dep(value, Closures.empty(this));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and configures it with the given
    /// closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(
        Provider<?> value,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = FromString.class, options = "T")
        Closure<?> closure
    ) {
        return this.dep(value.get(), closure);
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and applies the given action to
    /// it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(Provider<?> value, Action<? super T> action) {
        return this.dep(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(Provider<?> value) {
        return this.dep(value, Closures.empty(this));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and configures it with the given
    /// closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(
        ProviderConvertible<?> value,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = FromString.class, options = "T")
        Closure<?> closure
    ) {
        return this.dep(value.asProvider(), closure);
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and applies the given action to
    /// it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(ProviderConvertible<?> value, Action<? super T> action) {
        return this.dep(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dep(ProviderConvertible<?> value) {
        return this.dep(value, Closures.empty(this));
    }
}
