/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;

public interface SlimeLauncherRunTask extends Task {
    @Input Property<String> getSourceSetName();
    @Internal DirectoryProperty getLocalCacheDir();
    @InputFiles ConfigurableFileCollection getMetadata();
    @InputFiles ConfigurableFileCollection getMinecraftClasspath();
    @InputFiles ConfigurableFileCollection getRuntimeClasspath();
    @InputFiles ConfigurableFileCollection getPatcherModules();
    @Input Property<String> getMinecraftVersion();
    @Input Property<String> getMCPVersion();
    @Input Property<String> getMappingChannel();
    @Input Property<String> getMappingVersion();
    //@InputFile RegularFileProperty getSrgToMcp();
}
