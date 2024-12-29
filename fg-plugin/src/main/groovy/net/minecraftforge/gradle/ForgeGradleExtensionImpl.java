/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.plugins.ExtensionAware;

record ForgeGradleExtensionImpl() implements ForgeGradleExtension {
    static void register(ExtensionAware target) {
        target.getExtensions().add(
            ForgeGradleExtension.class,
            ForgeGradleExtension.NAME,
            new ForgeGradleExtensionImpl()
        );
    }
}
