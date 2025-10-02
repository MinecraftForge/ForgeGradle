/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import net.minecraftforge.gradleutils.shared.EnhancedTask;
import org.gradle.api.Project;
import org.gradle.api.Task;

interface ForgeGradleTask extends Task, EnhancedTask<ForgeGradleProblems> {
    @Override
    default Class<? extends EnhancedPlugin<? super Project>> pluginType() {
        return ForgeGradlePlugin.class;
    }

    @Override
    default Class<ForgeGradleProblems> problemsType() {
        return ForgeGradleProblems.class;
    }
}
