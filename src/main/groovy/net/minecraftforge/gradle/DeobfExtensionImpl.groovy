/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

@CompileStatic
@PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.CONSTRUCTORS])
final class DeobfExtensionImpl implements DeobfExtension {
    private final Project project
    private final ForgeGradleProblems problems

    // Dependencies
    private final Property<MinecraftExtension.Mappings> mappingsProp
    private final Map<Configuration, ? extends ModuleVersionSelector> dependencies = new HashMap<>()
    private final Action<? super AttributeContainer> attributes = { AttributeContainer a ->
        a.attribute MinecraftExtension.Attributes.mappingsChannel, this.mappings.channel()
        a.attribute MinecraftExtension.Attributes.mappingsVersion, this.mappings.version()
    }

    DeobfExtensionImpl(Project project, ForgeGradleProblems problems, ObjectFactory objects, Provider<MinecraftExtension.Mappings> mappings) {
        this.project = project
        this.problems = problems
        this.mappingsProp = objects.property(MinecraftExtension.Mappings).convention(mappings)
    }

    private MinecraftExtension.Mappings getMappings() {
        this.mappingsProp.get()
    }

    @Override
    ExternalModuleDependency deobf(
        Object value,
        @DelegatesTo(value = ExternalModuleDependency, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType, options = 'org.gradle.api.artifacts.ExternalModuleDependency')
            Closure closure
    ) {
        this.dependencies.computeIfAbsent(this.project.configurations.detachedConfiguration()) { configuration ->
            this.project.dependencies.create(value) { Dependency dependency ->
                if (dependency instanceof ExternalModuleDependency) {
                    dependency.attributes(this.attributes)
                    Closures.invoke(dependency, closure)
                    configuration.dependencies.add(dependency.copy())
                } else {
                    throw problems.invalidDeobfDependencyType(dependency)
                }
            } as ExternalModuleDependency
        } as ExternalModuleDependency
    }
}
