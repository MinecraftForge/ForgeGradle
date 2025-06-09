/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/// The ForgeGradle extension contains a handful of helpers that are not directly related to development involving
/// Minecraft.
// IDE support for code snippets in Markdown comments is still poor, so some Javadoc comments use the legacy format
@SuppressWarnings({"unused" /* TODO [ForgeGradle7][Testing] Write functional tests */, "rawtypes" /* public-facing closures */})
public sealed interface ForgeGradleExtension permits ForgeGradleExtensionImpl {
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
     * @see #forgeMaven
     */
    Closure forgeMaven = Closures.<MavenArtifactRepository>consumer(repo -> {
        repo.setName("MinecraftForge");
        repo.setUrl(Constants.FORGE_MAVEN);
    });

    /**
     * A closure for the Forge releases maven to be passed into
     * {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(Closure)}.
     * <pre><code>
     * repositories {
     *     maven fg.forgeReleaseMaven
     * }
     * </code></pre>
     *
     * @see #forgeMaven
     */
    Closure forgeReleaseMaven = Closures.<MavenArtifactRepository>consumer(repo -> {
        repo.setName("MinecraftForge releases");
        repo.setUrl(Constants.FORGE_MAVEN + "releases");
    });

    /**
     * A closure for the Forge snapshot maven to be passed into
     * {@link org.gradle.api.artifacts.dsl.RepositoryHandler#maven(Closure)}.
     * <pre><code>
     * repositories {
     *     maven fg.forgeSnapshotMaven
     * }
     * </code></pre>
     *
     * @see #forgeMaven
     */
    Closure forgeSnapshotMaven = Closures.<MavenArtifactRepository>consumer(repo -> {
        repo.setName("MinecraftForge snapshots");
        repo.setUrl(Constants.FORGE_MAVEN + "snapshots");
    });

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
     */
    Closure minecraftLibsMaven = Closures.<MavenArtifactRepository>consumer(repo -> {
        repo.setName("Minecraft libraries");
        repo.setUrl("https://libraries.minecraft.net/");
    });
}
