/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util.runs;

import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.DefaultTask;

abstract class MinecraftPrepareRunTask extends DefaultTask {
    public MinecraftPrepareRunTask() {
        this.setGroup(RunConfig.RUNS_GROUP);
        this.setImpliesSubProjects(true); // Preparing the game in the current project and child projects is a bad idea
    }
}
