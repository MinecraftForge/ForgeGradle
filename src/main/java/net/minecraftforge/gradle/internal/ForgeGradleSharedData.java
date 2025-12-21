package net.minecraftforge.gradle.internal;

import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.jspecify.annotations.Nullable;

import java.util.List;

record ForgeGradleSharedData(
    @Nullable MinecraftMappingsImpl mappings
) {
    static final String NAME = "__fg_shared_data";
}
