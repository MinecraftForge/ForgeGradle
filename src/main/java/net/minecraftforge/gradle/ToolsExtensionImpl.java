/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import net.minecraftforge.gradleutils.shared.Closures;
import net.minecraftforge.gradleutils.shared.Tool;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jetbrains.annotations.Nullable;

import javax.inject.Inject;

abstract class ToolsExtensionImpl implements ToolsExtensionInternal {
    private final ForgeGradlePlugin plugin;

    private final Project project;

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

    static void register(
        ForgeGradlePlugin plugin,
        ExtensionAware target
    ) {
        if (!(target instanceof Project)) return;

        var extensions = target.getExtensions();
        extensions.create(ToolsExtension.NAME, ToolsExtensionImpl.class, plugin, target);
    }

    @Inject
    public ToolsExtensionImpl(ForgeGradlePlugin plugin, Project project) {
        this.plugin = plugin;
        this.project = project;
        Tools.forEach(tool -> this.getExtensions().add(tool.getName(), new DefinitionImpl()));
    }

    // TODO [ForgeGradle] Do this better
    Provider<FileCollection> getClasspath(Tool tool) {
        return this.getProviders().provider(() -> ((DefinitionImpl) this.getExtensions().getByName(tool.getName())).classpath);
    }

    // TODO [ForgeGradle] Do this better
    Provider<String> getMainClass(Tool tool) {
        var definition = (DefinitionImpl) this.getExtensions().getByName(tool.getName());
        return definition.mainClass;
    }

    // TODO [ForgeGradle] Do this better
    Provider<String> getJavaLauncher(Tool tool) {
        var definition = (DefinitionImpl) this.getExtensions().getByName(tool.getName());
        return definition.javaLauncher;
    }

    non-sealed class DefinitionImpl implements ToolDefinition {
        private @Nullable FileCollection classpath;
        private final Property<String> mainClass = getObjects().property(String.class);
        private final Property<String> javaLauncher = getObjects().property(String.class);

        @Override
        public void setClasspath(FileCollection files) {
            this.classpath = files;
        }

        @Override
        public void setClasspath(
            @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST)
            @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.dsl.DependencyHandler")
            Closure<? extends Dependency> dependency
        ) {
            this.classpath = project.getConfigurations().detachedConfiguration().withDependencies(
                dependencies -> dependencies.addLater(getProviders().provider(
                    () -> Closures.invoke(dependency, project.getDependencies())
                ))
            );
        }

        @Override
        public void setMainClass(Provider<String> mainClass) {
            this.mainClass.set(mainClass);
        }

        @Override
        public void setMainClass(String mainClass) {
            this.mainClass.set(mainClass);
        }

        @Override
        public void setJavaLauncher(Provider<? extends JavaLauncher> javaLauncher) {
            this.javaLauncher.set(javaLauncher.map(Util.LAUNCHER_EXECUTABLE));
        }

        @Override
        public void setJavaLauncher(JavaLauncher javaLauncher) {
            this.setJavaLauncher(getProviders().provider(() -> javaLauncher));
        }
    }
}
