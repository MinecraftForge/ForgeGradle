/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

public interface SlimeLauncherRunTask extends Task {
    @Input Property<String> getSourceSetName();
    @Internal DirectoryProperty getLocalCacheDir();
    @InputFiles @Classpath ConfigurableFileCollection getMetadata();
    @InputFiles @Classpath ConfigurableFileCollection getMinecraftClasspath();
    @InputFiles @Classpath @Optional ConfigurableFileCollection getExtraLibraries();
    @InputFiles @Classpath ConfigurableFileCollection getRuntimeClasspath();
    @InputFiles @Classpath ConfigurableFileCollection getPatcherModules();
    @Input Property<String> getMinecraftVersion();
    @Input Property<String> getMCPVersion();
    @Input Property<String> getMappingChannel();
    @Input Property<String> getMappingVersion();
    @InputFile @Classpath @Optional RegularFileProperty getMcpToSrg();
    @InputFile @Classpath @Optional RegularFileProperty getMcpToObf();
}
