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
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.jetbrains.annotations.ApiStatus;

import java.util.function.Function;

@ApiStatus.Experimental
public sealed interface ToolDefinition permits ToolsExtensionImpl.DefinitionImpl {
    /// Sets the configuration to use as the classpath for AccessTransformers.
    ///
    /// @param files The file collection to use
    void setClasspath(FileCollection files);

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependency The dependency to use
    void setClasspath(
        @DelegatesTo(value = DependencyHandler.class, strategy = Closure.DELEGATE_FIRST)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.dsl.DependencyHandler")
        Closure<? extends Dependency> dependency
    );

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependency The dependency to use
    default void setClasspath(Function<? super DependencyHandler, ? extends Dependency> dependency) {
        this.setClasspath(Closures.function(this, dependency));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param closure            A configuring closure for the dependency
    @SuppressWarnings("rawtypes")
    default void setClasspath(
        Object dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        this.setClasspath(dependencies -> dependencies.create(dependencyNotation, closure));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param action             A configuring action for the dependency
    default void setClasspath(
        Object dependencyNotation,
        Action<? super Dependency> action
    ) {
        this.setClasspath(dependencyNotation, Closures.action(this, action));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    default void setClasspath(Object dependencyNotation) {
        this.setClasspath(dependencyNotation, Closures.empty(this));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param closure            A configuring closure for the dependency
    @SuppressWarnings("rawtypes")
    default void setClasspath(
        Provider<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        this.setClasspath(dependencyNotation.get(), closure);
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param action             A configuring action for the dependency
    default void setClasspath(
        Provider<?> dependencyNotation,
        Action<? super Dependency> action
    ) {
        this.setClasspath(dependencyNotation, Closures.action(this, action));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    default void setClasspath(Provider<?> dependencyNotation) {
        this.setClasspath(dependencyNotation, Closures.empty(this));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param closure            A configuring closure for the dependency
    @SuppressWarnings("rawtypes")
    default void setClasspath(
        ProviderConvertible<?> dependencyNotation,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure closure
    ) {
        this.setClasspath(dependencyNotation.asProvider(), closure);
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    /// @param action             A configuring action for the dependency
    default void setClasspath(
        ProviderConvertible<?> dependencyNotation,
        Action<? super Dependency> action
    ) {
        this.setClasspath(dependencyNotation, Closures.action(this, action));
    }

    /// Sets the dependency to use as the classpath for AccessTransformers.
    ///
    /// @param dependencyNotation The dependency (notation) to use
    default void setClasspath(ProviderConvertible<?> dependencyNotation) {
        this.setClasspath(dependencyNotation, Closures.empty(this));
    }

    /// Sets the main class to invoke when running AccessTransformers.
    ///
    /// @param mainClass The main class to use
    /// @apiNote This is *not required* if the given [classpath][#setClasspath(FileCollection)] is a single
    /// executable jar.
    void setMainClass(Provider<String> mainClass);

    /// Sets the main class to invoke when running AccessTransformers.
    ///
    /// @param mainClass The main class to use
    /// @apiNote This is *not required* if the given [classpath][#setClasspath(FileCollection)] is a single
    /// executable jar.
    void setMainClass(String mainClass);

    /// Sets the Java launcher to use to run AccessTransformers.
    ///
    /// This can be easily acquired using [Java toolchains][org.gradle.jvm.toolchain.JavaToolchainService].
    ///
    /// @param javaLauncher The Java launcher to use
    /// @see org.gradle.jvm.toolchain.JavaToolchainService#launcherFor(Action)
    void setJavaLauncher(Provider<? extends JavaLauncher> javaLauncher);

    /// Sets the Java launcher to use to run AccessTransformers.
    ///
    /// This can be easily acquired using [Java toolchains][org.gradle.jvm.toolchain.JavaToolchainService].
    ///
    /// @param javaLauncher The Java launcher to use
    /// @apiNote This method exists in case consumers have an eagerly processed `JavaLauncher` object. It is
    /// recommended to use [#setJavaLauncher(Provider)] instead.
    /// @see org.gradle.jvm.toolchain.JavaToolchainService#launcherFor(Action)
    void setJavaLauncher(JavaLauncher javaLauncher);
}
