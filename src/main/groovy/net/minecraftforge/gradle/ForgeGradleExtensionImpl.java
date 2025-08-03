/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

record ForgeGradleExtensionImpl() implements ForgeGradleExtension, HasPublicType {
    private static final ForgeGradleExtensionImpl INSTANCE = new ForgeGradleExtensionImpl();

    static void register(ExtensionAware target) {
        target.getExtensions().add(
            ForgeGradleExtension.class,
            ForgeGradleExtension.NAME,
            INSTANCE
        );
    }

    @Override
    public TypeOf<?> getPublicType() {
        return TypeOf.typeOf(ForgeGradleExtension.class);
    }
}
