/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import net.minecraftforge.gradle.MinecraftDependency;
import net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.Nullable;

interface MinecraftDependencyInternal extends MinecraftDependency, HasPublicType, MinecraftMappingsContainerInternal {
    String MC_EXT_NAME = "__fg_minecraft_dependency";
    String AT_COUNT_NAME = "__fg_minecraft_atcontainers";

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftDependency.class);
    }

    static boolean is(Dependency dependency) {
        try {
            return ((ExtensionAware) dependency).getExtensions().getExtraProperties().has(MC_EXT_NAME);
        } catch (Exception e) {
            return false;
        }
    }

    static @Nullable MinecraftDependencyInternal get(Dependency dependency) {
        try {
            return (MinecraftDependencyInternal) ((ExtensionAware) dependency).getExtensions().getExtraProperties().get(MC_EXT_NAME);
        } catch (Exception e) {
            return null;
        }
    }

    ExternalModuleDependency init(Object dependencyNotation, Closure<?> closure);

    // Can be nullable due to configuration caching.
    @Nullable ExternalModuleDependency asDependency();

    // Can be nullable due to configuration caching.
    @Nullable TaskProvider<SyncMavenizer> asTask();

    Action<? super AttributeContainer> addAttributes();

    default <R> Closure<R> closure(Closure<R> closure) {
        return closure.rehydrate(closure.getDelegate(), new ClosureOwnerImpl.MinecraftDependencyImpl(closure.getOwner(), this), closure.getThisObject());
    }

    void handle(Configuration configuration);

    void handle(SourceSet sourceSet);

    interface WithAccessTransformers extends MinecraftDependencyWithAccessTransformers, MinecraftDependencyInternal {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(MinecraftDependencyWithAccessTransformers.class);
        }

        default <R> Closure<R> closure(Closure<R> closure) {
            return closure.rehydrate(closure.getDelegate(), new ClosureOwnerImpl.MinecraftDependencyWithAccessTransformersImpl(closure.getOwner(), this), closure.getThisObject());
        }
    }
}
