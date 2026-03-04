/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import net.minecraftforge.gradle.MavenizerInstance;
import net.minecraftforge.gradle.MinecraftDependency;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

interface MinecraftDependencyInternal extends MinecraftDependency, HasPublicType, MinecraftMappingsContainerInternal, MinecraftAccessTransformersContainerInternal {
    String MC_EXT_NAME = "__fg_minecraft_dependency";

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftDependency.class);
    }

    @Override
    @NullUnmarked
    NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns();

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

    default <R> Closure<R> closure(Closure<R> closure) {
        return closure.rehydrate(closure.getDelegate(), new ClosureOwnerImpl.MinecraftDependencyImpl(closure.getOwner(), this), closure.getThisObject());
    }

    MavenizerInstance getMavenizerInstance();
    FileCollection getMinecraftDependencies();
    FileCollection getMetadataDependency();
    FileCollection getPatcherModules();
    TaskProvider<SlimeLauncherMetadata> getMetadataTask();
    String getPath();
    ModuleIdentifier getModule();
    String getKey();

    void handle(Configuration configuration);

    void handle(NamedDomainObjectSet<SourceSet> sourceSets, NamedDomainObjectSet<SourceSet> allSourceSets);
}
