/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.ForgeGradleExtension;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.reflect.TypeOf;

import javax.inject.Inject;

abstract class ForgeGradleExtensionImpl implements ForgeGradleExtensionInternal {
    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        var extensions = target.getExtensions();
        extensions.create(ForgeGradleExtension.class, ForgeGradleExtension.NAME, ForgeGradleExtensionImpl.class);
    }

    @Inject
    public ForgeGradleExtensionImpl() { }

    @Override
    public TypeOf<?> getPublicType() {
        return ForgeGradleExtensionInternal.super.getPublicType();
    }
}
