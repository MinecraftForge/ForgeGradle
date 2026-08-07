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
    FileCollection mapZip(Object files, String name);
    FileCollection mapZip(FileCollection files, String name);
    FileCollection mapAccessTransformer(Object files);
    FileCollection mapAccessTransformer(FileCollection files);
    FileCollection mapFacades(Object files);
    FileCollection mapFacades(FileCollection files);
}
