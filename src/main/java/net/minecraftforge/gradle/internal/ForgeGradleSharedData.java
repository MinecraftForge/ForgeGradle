package net.minecraftforge.gradle.internal;

import org.jspecify.annotations.Nullable;

record ForgeGradleSharedData(
    @Nullable MinecraftMappingsInternal mappings
) {
    static final String NAME = "__fg_shared_data";
}
