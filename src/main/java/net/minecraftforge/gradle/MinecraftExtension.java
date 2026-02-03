/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/// The main extension for ForgeGradle, where the Minecraft dependency resolution takes place.
///
/// ## Restrictions
///
/// - When declaring Minecraft dependencies, only [module
/// dependencies](https://docs.gradle.org/current/userguide/declaring_dependencies.html#1_module_dependencies) are
/// supported.
///   - The resulting Minecraft dependency is created by the Minecraft Mavenizer. It is not merely a dependency
/// transformation, which means that it cannot use file and project dependencies to generate the Minecraft artifacts.
///   - Attempting to provide a non-module dependency to [MinecraftExtensionForProject#dependency(Object)], will cause
/// the build to fail.
public interface MinecraftExtension extends MinecraftMappingsContainer {
    /// The name for this extension in Gradle.
    String NAME = "minecraft";

    /**
     * A closure for the Minecraft Mavenizer's output to be passed into
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
     * @see #mavenizer(RepositoryHandler)
     * @deprecated Use {@link #mavenizer(RepositoryHandler)} instead, as it has special handling to avoid pitfalls when
     * including the mavenizer output in your repositories.
     */
    @Deprecated(since = "7.0")
    Action<MavenArtifactRepository> getMavenizer();

    /**
     * Adds the generated Minecraft maven to the given repository handler.
     * <pre><code>
     * minecraft.maven(repositories)
     * </code></pre>
     *
     * @param repositories The repository handler to add the maven to
     * @return The Minecraft maven
     * @apiNote This version includes special logic to avoid pitfalls when adding the Mavenizer output as a repository
     * when using ForgeGradle in a subproject, for example, if all projects need to use the Forge maven.
     * @see #getMavenizer()
     */
    default MavenArtifactRepository mavenizer(RepositoryHandler repositories) {
        var mavenizer = repositories.maven(this.getMavenizer());
        repositories.remove(mavenizer);
        repositories.addFirst(mavenizer);
        return mavenizer;
    }
}
