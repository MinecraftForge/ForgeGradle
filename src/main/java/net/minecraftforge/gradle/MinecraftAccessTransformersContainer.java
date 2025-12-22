/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.file.RegularFileProperty;

public interface MinecraftAccessTransformersContainer {
    /// The default path, from the source set's [resources][org.gradle.api.tasks.SourceSet#getResources()], for the
    /// AccessTransformers config to be located in.
    String DEFAULT_PATH = "META-INF/accesstransformer.cfg";

    /// Gets the AccessTransformer configuration to use.
    ///
    /// @return The property for the configuration file to use
    RegularFileProperty getAccessTransformer();

    /// Sets the path, relative to this dependency's [org.gradle.api.tasks.SourceSet#getResources()], to the
    /// AccessTransformers config file to use.
    ///
    /// The default location for the AccessTransformer config file will be in
    /// [org.gradle.api.tasks.SourceSet#getResources()] -> first directory of
    /// [org.gradle.api.file.SourceDirectorySet#getSrcDirs()] -> `META-INF/accesstransformer.cfg`. If the source set,
    /// for whatever reason, does not have any resources directories set, ForgeGradle will make the best guess of
    /// {@code src/}{@link org.gradle.api.tasks.SourceSet#getName() name}{@code
    /// /resources/META-INF/accesstransformer.cfg}.
    ///
    /// @param accessTransformer The path to the config file to use
    /// @apiNote Using [#getAccessTransformer()] -> [RegularFileProperty#set] is strongly recommended if your config
    /// file is in a strict location.
    void setAccessTransformer(String accessTransformer);

    /// Sets if this dependency should use AccessTransformers.
    ///
    /// If `true`, this calls [#setAccessTransformer(String)] using [#DEFAULT_PATH] as the path. If `false`, this will
    /// force this dependency to *not use* AccessTransformers, even if the convention is set to do so from the Minecraft
    /// extension. This can be used to opt-out of AccessTransformer for a single Minecraft dependency if it is enabled
    /// globally for the rest of them in a project.
    ///
    /// @param accessTransformer If this dependency should use AccessTransformers
    /// @see #setAccessTransformer(String)
    void setAccessTransformer(boolean accessTransformer);
}
