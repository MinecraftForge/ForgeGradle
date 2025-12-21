/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;

/// The ForgeGradle extension contains a handful of helpers that are not directly related to development involving
/// Minecraft.
public interface ForgeGradleExtension {
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
    Action<MavenArtifactRepository> getForgeMaven();

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
    Action<MavenArtifactRepository> getMinecraftLibsMaven();

    /**
     * The attributes object for easy reference.
     * <pre><code>
     * dependencies {
     *     implementation 'com.example:example:1.0' {
     *         attributes.attribute(fg.attributes.os, 'windows')
     *     }
     * }
     * </code></pre>
     *
     * @return The attributes object
     * @see Attributes
     */
    Attributes getAttributes();

    /// This interface contains the attributes used by the [Minecraft][MinecraftExtension] extension for resolving the
    /// Minecraft and deobfuscated dependencies.
    ///
    /// @see ForgeGradleExtension#getAttributes()
    interface Attributes {
        /// The operating system of the project's host.
        ///
        /// This is used to filter natives from the Minecraft repo.
        ///
        /// @return The operating system attribute
        Attribute<String> getOs();

        /// The requested mappings channel of the project.
        ///
        /// This is determined using [MinecraftMappings#getChannel()] via [#getMappings()]
        ///
        /// @return The mappings channel attribute
        /// @see #getMappingsVersion()
        Attribute<String> getMappingsChannel();

        /// The requested mappings version of the project.
        ///
        /// This is determined using [MinecraftMappings#getVersion()] via [#getMappings()]
        ///
        /// @return The mappings channel version
        /// @see #getMappingsChannel()
        Attribute<String> getMappingsVersion();
    }
}
