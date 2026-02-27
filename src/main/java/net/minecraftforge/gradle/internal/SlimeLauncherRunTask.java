/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

public interface SlimeLauncherRunTask {
    Property<String> getSourceSetName();
    DirectoryProperty getLocalCacheDir();
    ConfigurableFileCollection getMetadata();
    ConfigurableFileCollection getMinecraftClasspath();
    ConfigurableFileCollection getRuntimeClasspath();
    Property<String> getMappingChannel();
    Property<String> getMappingVersion();
    //RegularFileProperty getSrgToMcp();
}
