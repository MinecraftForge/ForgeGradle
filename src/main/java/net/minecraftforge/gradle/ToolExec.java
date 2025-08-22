/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import net.minecraftforge.gradleutils.shared.EnhancedTask;
import net.minecraftforge.gradleutils.shared.Tool;
import net.minecraftforge.gradleutils.shared.ToolExecBase;
import org.gradle.api.Project;

abstract class ToolExec extends ToolExecBase<ForgeGradleProblems> implements EnhancedTask {
    ToolExec(Tool tool) {
        super(ForgeGradleProblems.class, tool);
    }

    @Override
    public Class<? extends EnhancedPlugin<? super Project>> pluginType() {
        return ForgeGradlePlugin.class;
    }
}
