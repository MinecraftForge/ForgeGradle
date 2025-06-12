/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle

import groovy.transform.CompileStatic
import groovy.transform.NamedVariant
import groovy.transform.PackageScope
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtraPropertiesExtension.UnknownPropertyException
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.nativeplatform.OperatingSystemFamily
import org.jetbrains.annotations.Nullable

import java.util.function.Supplier

@CompileStatic
@PackageScope final class MinecraftDependencyImpl implements MinecraftDependencyInternal {
    private static final String AT_COUNT_NAME = 'fg_minecraft_atcontainers'

    final ExternalModuleDependency delegate

    private final Project project
    private final ForgeGradleProblems problems
    private final ObjectFactory objects

    private final Property<MinecraftExtension.Mappings> mappingsProp

    private final Util.ActionableLazy<AccessTransformersContainer> atContainer

    MinecraftDependencyImpl(Dependency dependency, Project project, Problems problems, ObjectFactory objects, ProviderFactory providers) {
        this(dependency, project, new ForgeGradleProblems(problems, providers), objects)
    }

    MinecraftDependencyImpl(Dependency dependency, Project project, ForgeGradleProblems problems, ObjectFactory objects) {
        this(dependency, project, problems, objects, (Util.ActionableLazy<AccessTransformersContainer>) null)
    }

    MinecraftDependencyImpl(Dependency dependency, Project project, ForgeGradleProblems problems, ObjectFactory objects, @Nullable Util.ActionableLazy<AccessTransformersContainer> atContainer) {
        this.delegate = validateDependency(dependency, problems)

        this.project = project
        this.problems = problems
        this.objects = objects

        this.mappingsProp = objects.property(MinecraftExtension.Mappings)

        this.atContainer = atContainer ?: this.defaultAtContainer()
    }

    private static ExternalModuleDependency validateDependency(Dependency dependency, ForgeGradleProblems problems) {
        if (dependency instanceof ExternalModuleDependency) {
            if (dependency.changing)
                throw problems.changingMinecraftDependency(dependency)

            return dependency
        } else {
            throw problems.invalidMinecraftDependencyType(dependency)
        }
    }

    private Util.ActionableLazy<AccessTransformersContainer> defaultAtContainer() {
        Util.lazy {
            this.project.pluginManager.apply('net.minecraftforge.accesstransformers')
            AccessTransformersContainer.register(this.project, Attribute.of('net.minecraftforge.gradle.accesstransformed.' + this.atContainerCount++, Boolean)) { }
        }
    }

    private int getAtContainerCount() {
        try {
            return (int) this.project.extensions.extraProperties.get(AT_COUNT_NAME)
        } catch (UnknownPropertyException ignored) {
            this.atContainerCount = 0
        }
    }

    private void setAtContainerCount(int count) {
        this.project.extensions.extraProperties.set(AT_COUNT_NAME, count)
    }

    @PackageScope void finish(Supplier<MinecraftExtension.Mappings> defaultMappings, Util.ActionableLazy<AccessTransformersContainer> defaultATs) {
        var mappings = this.mappingsProp.tap { finalizeValue() }.getOrElse(defaultMappings.get())

        this.attributes { attributes ->
            attributes.attribute(MinecraftExtension.Attributes.os, objects.named(OperatingSystemFamily, OperatingSystem.current().familyName))
            attributes.attribute(MinecraftExtension.Attributes.mappingsChannel, mappings.channel())
            attributes.attribute(MinecraftExtension.Attributes.mappingsVersion, mappings.version())

            this.atContainer.orElse(defaultATs).ifPresent { accessTransformers ->
                attributes.attribute(accessTransformers.attribute, true)
            }
        }
    }

    @Override
    @Nullable MinecraftExtension.Mappings getMappings() {
        this.mappingsProp.orNull
    }

    @Override
    @NamedVariant
    void mappings(String channel, String version) {
        // manual null-checks here instead of @NullCheck for enhanced problems reporting
        MinecraftExtension.Mappings.checkParam(this.problems, channel, 'channel')
        MinecraftExtension.Mappings.checkParam(this.problems, version, 'version')

        this.mappingsProp.set new MinecraftExtension.Mappings(channel, version)
    }

    @Override
    void setAccessTransformer(Provider<?> configFile) {
        this.accessTransformer { config = configFile }
    }

    @Override
    void setAccessTransformer(RegularFile configFile) {
        this.accessTransformer { config = configFile }
    }

    @Override
    void setAccessTransformer(File configFile) {
        this.accessTransformer { config = configFile }
    }

    @Override
    void setAccessTransformer(Object configFile) {
        this.accessTransformer { config = configFile }
    }

    @Override
    void accessTransformer(
        @DelegatesTo(value = AccessTransformersContainer.Options.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer.Options")
            Closure options
    ) {
        this.atContainer.map { it.options options }
    }

    @Override
    MinecraftDependency copy() {
        new MinecraftDependencyImpl(this.delegate.copy(), this.project, this.problems, this.objects, this.atContainer.copy())
    }
}
