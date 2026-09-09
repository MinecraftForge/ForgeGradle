/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.jspecify.annotations.Nullable;

import java.io.Serializable;

record ForgeGradleSharedData(
    @Nullable MinecraftMappingsInternal mappings
) implements Serializable {
    static final String NAME = "__fg_shared_data";
}
