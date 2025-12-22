/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftAccessTransformersContainer;
import org.gradle.api.provider.Property;

interface MinecraftAccessTransformersContainerInternal extends MinecraftAccessTransformersContainer {
    boolean hasAccessTransformersPlugin();

    Property<String> getAccessTransformerPath();

    @Override
    default void setAccessTransformer(String accessTransformer) {
        this.getAccessTransformerPath().set(accessTransformer);
    }

    @Override
    default void setAccessTransformer(boolean accessTransformer) {
        if (accessTransformer)
            this.getAccessTransformerPath().set(DEFAULT_PATH);
        else
            this.getAccessTransformerPath().unset();
    }
}
