/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface ForgeGradleExtensionInternal extends ForgeGradleExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(ForgeGradleExtension.class);
    }

    Action<MavenArtifactRepository> forgeMaven = repo -> {
        repo.setName("MinecraftForge");
        repo.setUrl(Constants.FORGE_MAVEN);
    };

    Action<MavenArtifactRepository> minecraftLibsMaven = repo -> {
        repo.setName("Minecraft libraries");
        repo.setUrl(Constants.MC_LIBS_MAVEN);
    };
}
