/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

sealed interface MinecraftDependencyInternal extends MinecraftDependency, HasPublicType permits MinecraftDependencyImpl {
    ExternalModuleDependency getDelegate();

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(MinecraftDependency.class);
    }

    @Override
    default boolean isForce() {
        return this.getDelegate().isForce();
    }

    @Override
    default void version(Action<? super MutableVersionConstraint> configureAction) {
        this.getDelegate().version(configureAction);
    }

    @Override
    default VersionConstraint getVersionConstraint() {
        return this.getDelegate().getVersionConstraint();
    }

    @Override
    default ModuleDependency exclude(Map<String, String> excludeProperties) {
        return this.getDelegate().exclude(excludeProperties);
    }

    @Override
    default Set<ExcludeRule> getExcludeRules() {
        return this.getDelegate().getExcludeRules();
    }

    @Override
    default Set<DependencyArtifact> getArtifacts() {
        return this.getDelegate().getArtifacts();
    }

    @Override
    default ModuleDependency addArtifact(DependencyArtifact artifact) {
        return this.getDelegate().addArtifact(artifact);
    }

    @Override
    default DependencyArtifact artifact(Closure configureClosure) {
        return this.getDelegate().artifact(configureClosure);
    }

    @Override
    default DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
        return this.getDelegate().artifact(configureAction);
    }

    @Override
    default boolean isTransitive() {
        return this.getDelegate().isTransitive();
    }

    @Override
    default ModuleDependency setTransitive(boolean transitive) {
        return this.getDelegate().setTransitive(transitive);
    }

    @Override
    default @Nullable String getTargetConfiguration() {
        return this.getDelegate().getTargetConfiguration();
    }

    @Override
    default void setTargetConfiguration(@Nullable String name) {
        this.getDelegate().setTargetConfiguration(name);
    }

    @Override
    default AttributeContainer getAttributes() {
        return this.getDelegate().getAttributes();
    }

    @Override
    default ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
        return this.getDelegate().attributes(configureAction);
    }

    @Override
    default ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
        return this.getDelegate().capabilities(configureAction);
    }

    @Override
    default List<Capability> getRequestedCapabilities() {
        return this.getDelegate().getRequestedCapabilities();
    }

    @Override
    default Set<CapabilitySelector> getCapabilitySelectors() {
        return this.getDelegate().getCapabilitySelectors();
    }

    @Override
    default void endorseStrictVersions() {
        this.getDelegate().endorseStrictVersions();
    }

    @Override
    default void doNotEndorseStrictVersions() {
        this.getDelegate().doNotEndorseStrictVersions();
    }

    @Override
    default boolean isEndorsingStrictVersions() {
        return this.getDelegate().isEndorsingStrictVersions();
    }

    @Override
    default @Nullable String getGroup() {
        return this.getDelegate().getGroup();
    }

    @Override
    default String getName() {
        return this.getDelegate().getName();
    }

    @Override
    default @Nullable String getVersion() {
        return this.getDelegate().getVersion();
    }

    @Override
    @Deprecated
    default boolean contentEquals(Dependency dependency) {
        return this.getDelegate().contentEquals(dependency);
    }

    @Override
    default @Nullable String getReason() {
        return this.getDelegate().getReason();
    }

    @Override
    default void because(@Nullable String reason) {
        this.getDelegate().because(reason);
    }

    @Override
    default boolean matchesStrictly(ModuleVersionIdentifier identifier) {
        return this.getDelegate().matchesStrictly(identifier);
    }

    @Override
    default ModuleIdentifier getModule() {
        return this.getDelegate().getModule();
    }
}
