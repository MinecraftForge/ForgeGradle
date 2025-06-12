/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;

import java.io.File;

interface ForgeGradleTask extends Task {
    private ForgeGradlePlugin getPlugin() {
        return this.getProject().getPlugins().getPlugin(ForgeGradlePlugin.class);
    }

    @Internal
    default Provider<File> getTool(Tools tool) {
        return this.getPlugin().getTool(tool);
    }

    @Internal
    default DirectoryProperty getGlobalCaches() {
        return this.getPlugin().getGlobalCaches();
    }
}
