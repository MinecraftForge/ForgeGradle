/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface MinecraftExtensionInternal extends MinecraftExtension, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftExtension.class);
    }

    record AttributesInternal() implements Attributes {
        static AttributesInternal INSTANCE = new AttributesInternal();
    }

    non-sealed interface ForProject<T extends ClosureOwner<?>> extends MinecraftExtensionForProject<T>, MinecraftExtensionInternal, HasPublicType {
        @Override
        default TypeOf<?> getPublicType() {
            return new TypeOf<MinecraftExtensionForProject<ClosureOwner.MinecraftDependency>>() { };
        }

        non-sealed interface WithAccessTransformers extends MinecraftExtensionForProjectWithAccessTransformers, MinecraftExtensionInternal.ForProject<ClosureOwner.MinecraftDependencyWithAccessTransformers>, HasPublicType {
            @Override
            default TypeOf<?> getPublicType() {
                return TypeOf.typeOf(MinecraftExtensionForProjectWithAccessTransformers.class);
            }
        }
    }
}
