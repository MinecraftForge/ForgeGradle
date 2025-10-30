/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.inject.Inject;

abstract class ForgeGradleMagicPlugin extends EnhancedPlugin<Project> {
    static final String NAME = "forgegradle-magic";
    static final String DISPLAY_NAME = "ForgeGradle Magic";

    static final Logger LOGGER = Logging.getLogger(ForgeGradleMagicPlugin.class);

    @Inject
    public ForgeGradleMagicPlugin() {
        super(NAME, DISPLAY_NAME);
    }

    @Override
    public void setup(Project project) {

    }
}
