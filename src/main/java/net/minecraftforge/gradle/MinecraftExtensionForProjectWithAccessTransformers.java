/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

/// An additional extension of [MinecraftExtensionForProject] that allows for working with AccessTransformers as long
/// as the `net.minecraftforge.accesstransformers` plugin has been applied before ForgeGradle.
///
/// @see MinecraftExtensionForProject
public sealed interface MinecraftExtensionForProjectWithAccessTransformers extends MinecraftExtensionForProject<ClosureOwner.MinecraftDependencyWithAccessTransformers> permits MinecraftExtensionInternal.ForProject.WithAccessTransformers { }
