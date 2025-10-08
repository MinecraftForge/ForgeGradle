/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;

non-sealed interface MinecraftDependencyInternal extends MinecraftDependency, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftDependency.class);
    }

    ExternalModuleDependency getDelegate();

    default <R> Closure<R> closure(Closure<R> closure) {
        return closure.rehydrate(closure.getDelegate(), new ClosureOwnerImpl.MinecraftDependencyImpl(closure.getOwner(), this), closure.getThisObject());
    }

    void handle(SourceSet sourceSet);

    non-sealed interface WithAccessTransformers extends MinecraftDependencyWithAccessTransformers, MinecraftDependencyInternal {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(MinecraftDependencyWithAccessTransformers.class);
        }

        default <R> Closure<R> closure(Closure<R> closure) {
            return closure.rehydrate(closure.getDelegate(), new ClosureOwnerImpl.MinecraftDependencyWithAccessTransformersImpl(closure.getOwner(), this), closure.getThisObject());
        }
    }
}
