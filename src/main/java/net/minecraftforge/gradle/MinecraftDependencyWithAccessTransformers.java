/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer;
import org.gradle.api.Action;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.File;

/// An extension of [MinecraftDependency] that contains additional convenience methods for working with
/// AccessTransformers.
///
/// @see MinecraftDependency
public sealed interface MinecraftDependencyWithAccessTransformers extends MinecraftDependency permits ClosureOwner.MinecraftDependencyWithAccessTransformers, MinecraftDependencyInternal.WithAccessTransformers {
    /// Sets the AccessTransformer configuration to use.
    ///
    /// @param configFile The configuration file to use
    void setAccessTransformer(RegularFile configFile);

    /// Sets the AccessTransformer configuration to use.
    ///
    /// @param configFile The configuration file to use
    void setAccessTransformer(File configFile);

    /// Sets the AccessTransformer configuration to use.
    ///
    /// The given object is resolved using [org.gradle.api.Project#file(Object)].
    ///
    /// @param configFile The configuration file to use
    void setAccessTransformer(Object configFile);

    /// Sets the AccessTransformer configuration to use.
    ///
    /// If the given provider does not provide a [file][File] or [regular file][RegularFile], the result will be
    /// resolved using [org.gradle.api.Project#file(Object)], similarly to [#setAccessTransformer(Object)].
    ///
    /// @param configFile The configuration file to use
    void setAccessTransformer(Provider<?> configFile);

    /// Configures the AccessTransformer options for this project.
    ///
    /// @param options The options to apply
    void accessTransformer(Action<? super AccessTransformersContainer.Options> options);
}
