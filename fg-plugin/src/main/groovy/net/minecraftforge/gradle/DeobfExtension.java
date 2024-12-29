/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.jetbrains.annotations.ApiStatus;

/// The extension for working with deobfuscation in ForgeGradle. It is automatically applied by the
/// [minecraft][MinecraftExtension] extension when a Minecraft [dependency][MinecraftExtension.ForProject#dep(Object)]
/// is declared.
///
/// Deobfuscation is a necessary part of working with Forge for Minecraft 1.20.4 and older. This extension is designed
/// to make that burden as little as possible.
@ApiStatus.Experimental
public sealed interface DeobfExtension permits DeobfExtensionImpl {
    /// The name for this extension in Gradle.
    String NAME = "deobf";

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and
    /// configures it with the given closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    @SuppressWarnings("rawtypes") // public-facing closure
    ExternalModuleDependency deobf(
        Object value,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    );

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and applies
    /// the given action to it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(Object value, Action<? super Dependency> action) {
        return this.deobf(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(Object value) {
        return this.deobf(value, Closures.empty(this));
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and
    /// configures it with the given closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    @SuppressWarnings("rawtypes") // public-facing closure
    default ExternalModuleDependency deobf(
        Provider<?> value,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        return this.deobf(value.get(), closure);
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and applies
    /// the given action to it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(Provider<?> value, Action<? super Dependency> action) {
        return this.deobf(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(Provider<?> value) {
        return this.deobf(value, Closures.empty(this));
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and
    /// configures it with the given closure.
    ///
    /// @param value   The dependency
    /// @param closure The closure to configure the dependency with
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    @SuppressWarnings("rawtypes") // public-facing closure
    default ExternalModuleDependency deobf(
        ProviderConvertible<?> value,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        return this.deobf(value.asProvider(), closure);
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated and applies
    /// the given action to it.
    ///
    /// @param value  The dependency
    /// @param action The action to apply to the dependency attributes
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(ProviderConvertible<?> value, Action<? super Dependency> action) {
        return this.deobf(value, Closures.action(this, action));
    }

    /// Creates (or marks if existing) the given dependency as an obfuscated dependency to be deobfuscated.
    ///
    /// @param value The dependency
    /// @return The dependency
    /// @see <a href="https://docs.gradle.org/current/userguide/declaring_dependencies.html">Declaring Dependencies in
    /// Gradle</a>
    default ExternalModuleDependency deobf(ProviderConvertible<?> value) {
        return this.deobf(value, Closures.empty(this));
    }
}
