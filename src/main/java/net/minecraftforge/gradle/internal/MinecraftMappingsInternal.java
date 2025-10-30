/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftMappings;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

interface MinecraftMappingsInternal extends MinecraftMappings, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftMappings.class);
    }
}
