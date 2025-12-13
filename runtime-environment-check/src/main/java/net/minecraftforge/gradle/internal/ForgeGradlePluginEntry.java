/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Plugin;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.GradleVersion;

import javax.inject.Inject;

// TODO [GradleRuntimeCheck] Move this into its own repository? The idea is that it just checked for a required version.
//      All of our projects could benefit from this, it could also be something in GradleUtils Shared.
@SuppressWarnings("unused")
abstract class ForgeGradlePluginEntry implements Plugin<PluginAware> {
    private static final GradleVersion CURRENT_GRADLE = GradleVersion.current();
    private static final GradleVersion MINIMUM_GRADLE = GradleVersion.version("9.3.0-rc-1");

    @Inject
    public ForgeGradlePluginEntry() { }

    @Override
    public void apply(PluginAware target) {
        if (CURRENT_GRADLE.compareTo(MINIMUM_GRADLE) < 0) {
            String message = String.format(
                "ForgeGradle requires %s or later to run. You are currently using %s.",
                MINIMUM_GRADLE,
                CURRENT_GRADLE
            );
            throw new IllegalStateException(message);
        }

        try {
            target.getPluginManager().apply(Class.forName("net.minecraftforge.gradle.internal.ForgeGradlePlugin"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to find the ForgeGradle entry-point.", e);
        }
    }
}
