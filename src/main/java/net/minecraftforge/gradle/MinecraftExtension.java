/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.nativeplatform.OperatingSystemFamily;

/// The main extension for ForgeGradle, where the Minecraft dependency resolution takes place.
///
/// ## Restrictions
///
/// - When declaring Minecraft dependencies, only [module
/// dependencies](https://docs.gradle.org/current/userguide/declaring_dependencies.html#1_module_dependencies) are
/// supported.
///   - The resulting Minecraft dependency is created by the Minecraft Mavenizer. It is not merely a dependency
/// transformation, which means that it cannot use file and project dependencies to generate the Minecraft artifacts.
///   - Attempting to provide a non-module dependency to [MinecraftExtensionForProject#dependency(Object)], will cause the
/// build to fail.
public sealed interface MinecraftExtension extends MinecraftMappingsContainer permits MinecraftExtensionInternal, MinecraftExtensionForProject {
    /// The name for this extension in Gradle.
    String NAME = "minecraft";

    /**
     * A closure for the generated Minecraft maven to be passed into
     * {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(Closure)}.
     * <p>Declaring this in your buildscript is <strong>required</strong> for the Minecraft dependencies to resolve
     * properly.</p>
     * <pre><code>
     * repositories {
     *     maven minecraft.maven
     * }
     * </code></pre>
     *
     * @return The closure
     */
    Action<MavenArtifactRepository> getMaven();

    /**
     * Adds the generated Minecraft maven to the given repository handler.
     * <pre><code>
     * minecraft.maven(repositories)
     * </code></pre>
     *
     * @param repositories The repository handler to add the maven to
     * @return The Minecraft maven
     * @see #getMaven()
     */
    default MavenArtifactRepository maven(RepositoryHandler repositories) {
        return repositories.maven(this.getMaven());
    }

    /**
     * The attributes object for easy reference.
     * <pre><code>
     * dependencies {
     *     implementation 'com.example:example:1.0' {
     *         attributes.attribute(minecraft.attributes.os, objects.named(OperatingSystemFamily, OperatingSystemFamily.WINDOWS))
     *     }
     * }
     * </code></pre>
     *
     * @return The attributes object
     * @see Attributes
     */
    default Attributes getAttributes() {
        return MinecraftExtensionInternal.AttributesInternal.INSTANCE;
    };

    /// This interface contains the attributes used by the [Minecraft][MinecraftExtension] extension for resolving the
    /// Minecraft and deobfuscated dependencies.
    ///
    /// @see MinecraftExtension#getAttributes()
    sealed interface Attributes permits MinecraftExtensionInternal.AttributesInternal {
        /// The [operating system family][OperatingSystemFamily] of the project's host.
        ///
        /// This is used to filter natives from the Minecraft repo.
        Attribute<OperatingSystemFamily> os = Attribute.of("net.minecraftforge.native.operatingSystem", OperatingSystemFamily.class);
        /// The requested mappings channel of the project.
        ///
        /// This is determined using [MinecraftMappings#channel()] via [#getMappings()]
        ///
        /// @see #mappingsVersion
        Attribute<String> mappingsChannel = Attribute.of("net.minecraftforge.mappings.channel", String.class);
        /// The requested mappings version of the project.
        ///
        /// This is determined using [MinecraftMappings#version()] via [#getMappings()]
        ///
        /// @see #mappingsChannel
        Attribute<String> mappingsVersion = Attribute.of("net.minecraftforge.mappings.version", String.class);
    }
}
