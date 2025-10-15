/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;

import javax.inject.Inject;

abstract class ForgeGradlePlugin extends EnhancedPlugin<ExtensionAware> {
    static final String NAME = "forgegradle";
    static final String DISPLAY_NAME = "ForgeGradle";

    static final Logger LOGGER = Logging.getLogger(ForgeGradlePlugin.class);

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    static {
        LOGGER.lifecycle("ForgeGradle 7 is an incubating plugin.");
    }

    @Inject
    public ForgeGradlePlugin() {
        super(NAME, DISPLAY_NAME, "fgtools");
    }

    @Override
    public void setup(ExtensionAware target) {
        if (target instanceof Project project && !problems.testFalse("net.minecraftforge.gradle.magic")) {
            LOGGER.info("Applying ForgeGradle Magic to {}", project);
            project.getPluginManager().apply(ForgeGradleMagicPlugin.class);
        }

        ForgeGradleExtensionImpl.register(this, target);
        MinecraftExtensionImpl.register(this, target);
    }
}
