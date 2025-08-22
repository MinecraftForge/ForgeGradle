/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.artifacts.ExternalModuleDependency;

/// A closure owner is used to replace a closure's owner to allow for multiple delegates.
///
/// This is done to allow for buildscript authors to work with an abstraction that implements multiple interfaces
/// without needing to create an object that does so. For example, the [net.minecraftforge.gradle.MinecraftDependency]
/// implementation is not itself an [ExternalModuleDependency], but the [MinecraftDependency] closure owner provides an
/// abstraction that delegates to both of those interfaces simultaneously.
///
/// @param <D> The type of the owner delegate to be used on top of the original owner
public interface ClosureOwner<D> {
    /// Gets the owner delegate for this closure owner.
    ///
    /// The owner delegate sits on top of the [Closure][groovy.lang.Closure]'s original
    /// {@linkplain groovy.lang.Closure#getOwner() owner}, and is used primarily on top of it when the closure owner is
    /// invoked. If a member can't be found in the owner delegate, the original owner is queried instead.
    ///
    /// @return The owner delegate
    D getOwnerDelegate();

    /// A closure owner that delegates to [net.minecraftforge.gradle.MinecraftDependency] and
    /// [ExternalModuleDependency].
    ///
    /// @see ClosureOwner
    sealed interface MinecraftDependency extends ClosureOwner<net.minecraftforge.gradle.MinecraftDependency>, net.minecraftforge.gradle.MinecraftDependency, ExternalModuleDependency permits ClosureOwnerInternal.MinecraftDependency { }

    /// A closure owner that delegates to [net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers] and
    /// [ExternalModuleDependency].
    ///
    /// @see ClosureOwner
    sealed interface MinecraftDependencyWithAccessTransformers extends ClosureOwner<net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers>, net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers, ExternalModuleDependency permits ClosureOwnerInternal.MinecraftDependencyWithAccessTransformers { }
}
