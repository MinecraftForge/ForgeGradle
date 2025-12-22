/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Plugin;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;

// TODO [GradleRuntimeCheck] Move this into its own repository? The idea is that it just checked for a required version.
//      All of our projects could benefit from this, it could also be something in GradleUtils Shared.
@SuppressWarnings("unused")
abstract class ForgeGradlePluginEntry implements Plugin<PluginAware> {
    private static final Logger LOGGER = Logging.getLogger(ForgeGradlePluginEntry.class);

    private static final GradleVersion CURRENT_GRADLE = GradleVersion.current();
    private static final GradleVersion MINIMUM_GRADLE = GradleVersion.version("9.3.0-rc-1");

    private static final String PLUGIN_DISPLAY_NAME = "ForgeGradle";
    private static final String PLUGIN_VERSION = "7";
    private static final String PLUGIN_CLASS = "net.minecraftforge.gradle.internal.ForgeGradlePlugin";

    @Inject
    public ForgeGradlePluginEntry() { }

    @Override
    public void apply(PluginAware target) {
        if (CURRENT_GRADLE.compareTo(MINIMUM_GRADLE) < 0) {
            String message = String.format(
                "%s %s requires %s or later to run. You are currently using %s.",
                PLUGIN_DISPLAY_NAME,
                PLUGIN_VERSION,
                MINIMUM_GRADLE,
                CURRENT_GRADLE
            );
            LOGGER.error("ERROR: {}", message);
            throw new IllegalStateException(message);
        }

        try {
            target.getPluginManager().apply(Class.forName(PLUGIN_CLASS));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(String.format("Failed to find the %s entry-point.", PLUGIN_DISPLAY_NAME), e);
        }
    }
}
