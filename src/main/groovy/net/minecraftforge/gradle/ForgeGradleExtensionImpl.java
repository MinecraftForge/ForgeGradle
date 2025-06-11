/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;

record ForgeGradleExtensionImpl() implements ForgeGradleExtension {
    static <T extends ExtensionAware & PluginAware> void register(T target) {
        target.getExtensions().add(
            ForgeGradleExtension.class,
            ForgeGradleExtension.NAME,
            new ForgeGradleExtensionImpl()
        );
    }
}
