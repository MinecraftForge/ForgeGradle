/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.ForgeGradleExtension;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

interface ForgeGradleExtensionInternal extends ForgeGradleExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(ForgeGradleExtension.class);
    }

    Action<MavenArtifactRepository> forgeMaven = repo -> {
        repo.setName("MinecraftForge");
        repo.setUrl(Constants.FORGE_MAVEN);
    };

    @Override
    default Action<MavenArtifactRepository> getForgeMaven() {
        return forgeMaven;
    }

    Action<MavenArtifactRepository> minecraftLibsMaven = repo -> {
        repo.setName("Minecraft libraries");
        repo.setUrl(Constants.MC_LIBS_MAVEN);
    };

    @Override
    default Action<MavenArtifactRepository> getMinecraftLibsMaven() {
        return minecraftLibsMaven;
    }

    @Override
    default Attributes getAttributes() {
        return ForgeGradleExtensionInternal.AttributesInternal.INSTANCE;
    }

    record AttributesInternal() implements Attributes {
        static AttributesInternal INSTANCE = new AttributesInternal();

        @Override
        public Attribute<String> getOs() {
            return ForgeAttributes.OperatingSystem.ATTRIBUTE;
        }

        @Override
        public Attribute<String> getMappingsChannel() {
            return ForgeAttributes.MappingsChannel.ATTRIBUTE;
        }

        @Override
        public Attribute<String> getMappingsVersion() {
            return ForgeAttributes.MappingsVersion.ATTRIBUTE;
        }
    }
}
