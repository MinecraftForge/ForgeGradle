/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;

import java.util.List;

interface MinecraftExtensionInternal extends MinecraftExtension, MinecraftMappingsContainerInternal {
    Property<MinecraftMappingsInternal> getMappingsProperty();

    DirectoryProperty getMavenizerOutput();

    // NOTE: This internal interface does NOT implement MinecraftDependencyInternal as it is not actually a dependency!
    //       The top-level interface implements MinecraftDependency since it acts as a default for all Minecraft dependencies.
    interface ForProject extends MinecraftExtensionForProject, MinecraftExtensionInternal, MinecraftAccessTransformersContainerInternal {
        List<? extends MavenArtifactRepository> getRepositories();

        DirectoryProperty getEclipseOutputDir();
    }
}
