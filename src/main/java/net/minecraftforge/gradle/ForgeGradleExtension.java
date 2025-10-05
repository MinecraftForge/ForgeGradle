/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/// The ForgeGradle extension contains a handful of helpers that are not directly related to development involving
/// Minecraft.
public sealed interface ForgeGradleExtension permits ForgeGradleExtensionInternal {
    /// The name for this extension in Gradle.
    String NAME = "fg";

    /**
     * A closure for the Forge maven to be passed into
     * {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(Closure)}.
     * <p>Declaring this in your buildscript is <strong>required</strong> for the Minecraft dependencies to resolve
     * properly, due to hosting Forge and MCP-related artifacts that may be dependencies for the Minecraft
     * artifact.</p>
     * <pre><code>
     * repositories {
     *     maven fg.forgeMaven
     * }
     * </code></pre>
     *
     * @return The closure
     */
    default Action<MavenArtifactRepository> getForgeMaven() {
        return ForgeGradleExtensionInternal.forgeMaven;
    }

    /**
     * A closure for the Minecraft libraries maven to be passed into
     * {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(Closure)}.
     * <p>Declaring this in your buildscript is <strong>required</strong> for the Minecraft dependencies to resolve
     * properly.</p>
     * <pre><code>
     * repositories {
     *     maven fg.minecraftLibsMaven
     * }
     * </code></pre>
     *
     * @return The closure
     */
    default Action<MavenArtifactRepository> getMinecraftLibsMaven() {
        return ForgeGradleExtensionInternal.minecraftLibsMaven;
    }
}
