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

/// The Minecraft dependency contains information essential for how the
/// {@linkplain MinecraftExtensionForProject minecraft extension} processes Minecraft dependencies.
public interface MinecraftDependency extends MinecraftMappingsContainer, MinecraftAccessTransformersContainer {
    /// The collection of Slime Launcher options with which to create the launcher tasks.
    ///
    /// @return The collection of run task options
    NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns();

    /// Configures the Slime Launcher options for this project, which will be used to create the launcher tasks.
    ///
    /// @param closure The configuring closure
    default void runs(
        @DelegatesTo(NamedDomainObjectContainer.class)
        @ClosureParams(value = FromString.class, options = "org.gradle.api.NamedDomainObjectContainer<net.minecraftforge.gradle.SlimeLauncherOptions>")
        Closure<?> closure
    ) {
        this.getRuns().configure(closure);
    }

    /// Configures the Slime Launcher options for this project, which will be used to create the launcher tasks.
    ///
    /// @param action The configuring action
    default void runs(Action<? super NamedDomainObjectContainer<? extends SlimeLauncherOptions>> action) {
        this.runs(Closures.action(this, action));
    }
}
