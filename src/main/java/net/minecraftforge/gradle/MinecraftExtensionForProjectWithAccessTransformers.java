/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.provider.Property;

/// An additional extension of [MinecraftExtensionForProject] that allows for working with AccessTransformers as long as
/// the `net.minecraftforge.accesstransformers` plugin has been applied before ForgeGradle.
///
/// @see MinecraftExtensionForProject
public sealed interface MinecraftExtensionForProjectWithAccessTransformers extends MinecraftExtensionForProject<ClosureOwner.MinecraftDependencyWithAccessTransformers> permits MinecraftExtensionInternal.ForProject.WithAccessTransformers {
    /// The path, relative to the related Minecraft dependency's [org.gradle.api.tasks.SourceSet#getResources()], to the
    /// AccessTransformers config file to use. If set, this will recursively enable access transformers for all
    /// Minecraft dependencies in the project.
    ///
    /// The default location for the AccessTransformer config file will be in
    /// [org.gradle.api.tasks.SourceSet#getResources()] -> first directory of
    /// [org.gradle.api.file.SourceDirectorySet#getSrcDirs()] -> `META-INF/accesstransformer.cfg`. If the source set,
    /// for whatever reason, does not have any resources directories set, ForgeGradle will make the best guess of
    /// {@code src/}{@link org.gradle.api.tasks.SourceSet#getName() name}{@code
    /// /resources/META-INF/accesstransformer.cfg}.
    ///
    /// @return The property for the default AccessTransformers path from the source set's resources.
    /// @apiNote Setting this property will apply to **all Minecraft dependencies** regardless of source set. A single
    /// Minecraft dependency can manually be opted into using AccessTransformers by setting the value of
    /// [MinecraftDependencyWithAccessTransformers#getAccessTransformer()], or by using
    /// [MinecraftDependencyWithAccessTransformers#setAccessTransformer].
    Property<String> getAccessTransformers();

    /// Sets if all Minecraft dependencies in this project should use AccessTransformers.
    ///
    /// @param accessTransformers If all Minecraft dependencies should use AccessTransformers.
    /// @see #getAccessTransformers()
    default void setAccessTransformers(boolean accessTransformers) {
        if (accessTransformers)
            this.getAccessTransformers().convention(MinecraftDependencyWithAccessTransformers.DEFAULT_PATH);
        else
            this.getAccessTransformers().unsetConvention();
    }
}
