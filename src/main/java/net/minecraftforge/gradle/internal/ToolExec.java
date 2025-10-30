/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.Tool;
import net.minecraftforge.gradleutils.shared.ToolExecBase;

abstract class ToolExec extends ToolExecBase<ForgeGradleProblems> implements ForgeGradleTask {
    ToolExec(Tool tool) {
        super(tool);
    }
}
