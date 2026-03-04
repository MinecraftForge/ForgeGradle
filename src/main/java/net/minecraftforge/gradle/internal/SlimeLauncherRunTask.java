/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public interface SlimeLauncherRunTask extends Task {
    Property<String> getSourceSetName();
    DirectoryProperty getLocalCacheDir();
    ConfigurableFileCollection getMetadata();
    ConfigurableFileCollection getMinecraftClasspath();
    ConfigurableFileCollection getRuntimeClasspath();
    ConfigurableFileCollection getPatcherModules();
    Property<String> getMinecraftVersion();
    Property<String> getMCPVersion();
    Property<String> getMappingChannel();
    Property<String> getMappingVersion();
    //RegularFileProperty getSrgToMcp();
}
