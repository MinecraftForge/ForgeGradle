/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.FromString;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.gradleutils.shared.Closures;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;

/// [Project][org.gradle.api.Project]-specific additions for the Minecraft extension. These will be accessible from the
/// `minecraft` DSL object within your project's buildscript.
///
/// @see MinecraftExtension
public interface MinecraftExtensionForProject extends MinecraftExtension, MinecraftDependency {
    /// Creates (or marks if existing) the given dependency as a Minecraft dependency and configures it with the given
    /// closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    Provider<ExternalModuleDependency> dependency(
        Object value,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.gradle.ClosureOwner.MinecraftDependency")
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
    default Provider<ExternalModuleDependency> dependency(Object value, Action<? super ClosureOwner.MinecraftDependency> action) {
        return this.dependency(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as a Minecraft dependency.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies
    /// in Gradle</a>
    default Provider<ExternalModuleDependency> dependency(Object value) {
        return this.dependency(value, Closures.empty(this));
    }
}
