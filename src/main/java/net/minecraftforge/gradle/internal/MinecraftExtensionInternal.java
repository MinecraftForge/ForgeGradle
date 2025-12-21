/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.ClosureOwner;
import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

import java.util.List;

interface MinecraftExtensionInternal extends MinecraftExtension, HasPublicType, MinecraftMappingsContainerInternal {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftExtension.class);
    }

    @Override
    default Attributes getAttributes() {
        return AttributesInternal.INSTANCE;
    }

    Property<MinecraftMappingsInternal> getMappingsProperty();

    DirectoryProperty getMavenizerOutput();

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

    interface ForProject extends MinecraftExtensionForProject, MinecraftExtensionInternal, HasPublicType, MinecraftAccessTransformersContainerInternal {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(MinecraftExtensionForProject.class);
        }

        List<? extends MavenArtifactRepository> getRepositories();

        DirectoryProperty getEclipseOutputDir();
    }
}
