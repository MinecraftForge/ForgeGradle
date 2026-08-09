/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.file.FileCollection;

/// [Project][org.gradle.api.Project]-specific additions for the ForgeGradle extension. These will be accessible from the
/// `fg` DSL object within your project's buildscript.
///
/// @see ForgeGradleExtension
public interface ForgeGradleExtensionForProject extends ForgeGradleExtension {
    /// Attempts to locate and extract a file inside archives.
    /// @param files The files to search, accepts anything that can be passed to {@link org.gradle.api.Project#files(Object...)}
    /// @param name The exact name to search for in archives
    FileCollection findFiles(Object files, String name);

    /// Attempts to locate and extract a file inside archives.
    /// @param files The files to search, any non-archives (zip, or jar) will be ignored
    /// @param name The exact name to search for in archives
    FileCollection findFiles(FileCollection files, String name);

    /// Attempts to locate and extract any Access Transformer configurations from archives.
    /// Configurations will be located via:
    ///   - A Space Separated list in the Manifest entry `FMLAT`
    ///   - The `mods.accessTransformers` entry in mods.toml a configuration file
    ///   - The default location of META-INF/accesstransformer.cfg
    ///
    /// @param files The files to search, accepts anything that can be passed to {@link org.gradle.api.Project#files(Object...)}
    FileCollection findAccessTransformers(Object files);

    /// Attempts to locate and extract any Access Transformer configurations from archives.
    /// Configurations will be located via:
    ///   - A Space Separated list in the Manifest entry `FMLAT`
    ///   - The `mods.accessTransformers` entry in mods.toml a configuration file
    ///   - The default location of META-INF/accesstransformer.cfg
    ///
    /// @param files The files to search, any non-archives (zip, or jar) will be ignored
    FileCollection findAccessTransformers(FileCollection files);

    /// Attempts to locate and extract any Facade configurations from archives.
    /// Configurations will be located via:
    ///   - A Space Separated list in the Manifest entry `FORGE_FACADE`
    ///   - The default location of META-INF/facades.cfg
    ///
    /// @param files The files to search, accepts anything that can be passed to {@link org.gradle.api.Project#files(Object...)}
    FileCollection findFacades(Object files);

    /// Attempts to locate and extract any Facade configurations from archives.
    /// Configurations will be located via:
    ///   - A Space Separated list in the Manifest entry `FORGE_FACADE`
    ///   - The default location of META-INF/facades.cfg
    ///
    /// @param files The files to search, any non-archives (zip, or jar) will be ignored
    FileCollection findFacades(FileCollection files);
}
