/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.transform.Generated;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import net.minecraftforge.accesstransformers.gradle.AccessTransformersContainer;
import org.gradle.api.Action;
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
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

interface ClosureOwnerInternal<D> extends ClosureOwner<D> {
    default RuntimeException stub() {
        return new UnsupportedOperationException();
    }

    non-sealed interface MinecraftDependency extends ClosureOwnerInternal<net.minecraftforge.gradle.MinecraftDependency>, ClosureOwner.MinecraftDependency, HasPublicType {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(ClosureOwner.MinecraftDependency.class);
        }

        @Override
        default @Nullable MinecraftMappings getMappings() {
            return this.getOwnerDelegate().getMappings();
        }

        @Override
        default void mappings(String channel, String version) {
            this.getOwnerDelegate().mappings(channel, version);
        }

        @Override
        @Generated
        @SuppressWarnings("rawtypes")
        default void mappings(
            @NamedParams({
                @NamedParam(
                    type = String.class,
                    value = "channel",
                    required = true
                ),
                @NamedParam(
                    type = String.class,
                    value = "version",
                    required = true
                )
            }) Map namedArgs
        ) {
            this.getOwnerDelegate().mappings(namedArgs);
        }

        @Override
        default boolean isChanging() {
            throw this.stub();
        }

        @Override
        default ExternalModuleDependency setChanging(boolean changing) {
            throw this.stub();
        }

        @Override
        default ExternalModuleDependency copy() {
            throw this.stub();
        }

        @Override
        default boolean isForce() {
            throw this.stub();
        }

        @Override
        default void version(Action<? super MutableVersionConstraint> configureAction) {
            throw this.stub();
        }

        @Override
        default VersionConstraint getVersionConstraint() {
            throw this.stub();
        }

        @Override
        default ModuleDependency exclude(Map<String, String> excludeProperties) {
            throw this.stub();
        }

        @Override
        default Set<ExcludeRule> getExcludeRules() {
            throw this.stub();
        }

        @Override
        default Set<DependencyArtifact> getArtifacts() {
            throw this.stub();
        }

        @Override
        default ModuleDependency addArtifact(DependencyArtifact artifact) {
            throw this.stub();
        }

        @Override
        default DependencyArtifact artifact(Closure configureClosure) {
            throw this.stub();
        }

        @Override
        default DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
            throw this.stub();
        }

        @Override
        default boolean isTransitive() {
            throw this.stub();
        }

        @Override
        default ModuleDependency setTransitive(boolean transitive) {
            throw this.stub();
        }

        @Override
        default @Nullable String getTargetConfiguration() {
            throw this.stub();
        }

        @Override
        default void setTargetConfiguration(@Nullable String name) {
            throw this.stub();
        }

        @Override
        default AttributeContainer getAttributes() {
            throw this.stub();
        }

        @Override
        default ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
            throw this.stub();
        }

        @Override
        default ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
            throw this.stub();
        }

        @Override
        default List<Capability> getRequestedCapabilities() {
            throw this.stub();
        }

        @Override
        default Set<CapabilitySelector> getCapabilitySelectors() {
            throw this.stub();
        }

        @Override
        default void endorseStrictVersions() {
            throw this.stub();
        }

        @Override
        default void doNotEndorseStrictVersions() {
            throw this.stub();
        }

        @Override
        default boolean isEndorsingStrictVersions() {
            throw this.stub();
        }

        @Override default @Nullable String getGroup() {
            throw this.stub();
        }

        @Override
        default String getName() {
            throw this.stub();
        }

        @Override
        default @Nullable String getVersion() {
            throw this.stub();
        }

        @Override
        default @Nullable String getReason() {
            throw this.stub();
        }

        @Override
        default void because(@Nullable String reason) {
            throw this.stub();
        }

        @Override
        default boolean matchesStrictly(ModuleVersionIdentifier identifier) {
            throw this.stub();
        }

        @Override
        default ModuleIdentifier getModule() {
            throw this.stub();
        }
    }

    non-sealed interface MinecraftDependencyWithAccessTransformers extends ClosureOwnerInternal<net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers>, ClosureOwner.MinecraftDependencyWithAccessTransformers, HasPublicType {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(ClosureOwner.MinecraftDependencyWithAccessTransformers.class);
        }

        net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers getOwnerDelegate();

        @Override
        default void setAccessTransformer(RegularFile configFile) {
            this.getOwnerDelegate().setAccessTransformer(configFile);
        }

        @Override
        default void setAccessTransformer(File configFile) {
            this.getOwnerDelegate().setAccessTransformer(configFile);
        }

        @Override
        default void setAccessTransformer(Object configFile) {
            this.getOwnerDelegate().setAccessTransformer(configFile);
        }

        @Override
        default void setAccessTransformer(Provider<?> configFile) {
            this.getOwnerDelegate().setAccessTransformer(configFile);
        }

        @Override
        default void accessTransformer(Action<? super AccessTransformersContainer.Options> options) {
            this.getOwnerDelegate().accessTransformer(options);
        }

        @Override
        default @Nullable MinecraftMappings getMappings() {
            return this.getOwnerDelegate().getMappings();
        }

        @Override
        default void mappings(String channel, String version) {
            this.getOwnerDelegate().mappings(channel, version);
        }

        @Override
        default void mappings(
            @NamedParams({
                @NamedParam(
                    type = String.class,
                    value = "channel",
                    required = true
                ),
                @NamedParam(
                    type = String.class,
                    value = "version",
                    required = true
                )
            }) Map<?, ?> namedArgs
        ) {
            this.getOwnerDelegate().mappings(namedArgs);
        }

        @Override
        default boolean isChanging() {
            throw this.stub();
        }

        @Override
        default ExternalModuleDependency setChanging(boolean changing) {
            throw this.stub();
        }

        @Override
        default ExternalModuleDependency copy() {
            throw this.stub();
        }

        @Override
        default boolean isForce() {
            throw this.stub();
        }

        @Override
        default void version(Action<? super MutableVersionConstraint> configureAction) {
            throw this.stub();
        }

        @Override
        default VersionConstraint getVersionConstraint() {
            throw this.stub();
        }

        @Override
        default ModuleDependency exclude(Map<String, String> excludeProperties) {
            throw this.stub();
        }

        @Override
        default Set<ExcludeRule> getExcludeRules() {
            throw this.stub();
        }

        @Override
        default Set<DependencyArtifact> getArtifacts() {
            throw this.stub();
        }

        @Override
        default ModuleDependency addArtifact(DependencyArtifact artifact) {
            throw this.stub();
        }

        @Override
        default DependencyArtifact artifact(Closure configureClosure) {
            throw this.stub();
        }

        @Override
        default DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
            throw this.stub();
        }

        @Override
        default boolean isTransitive() {
            throw this.stub();
        }

        @Override
        default ModuleDependency setTransitive(boolean transitive) {
            throw this.stub();
        }

        @Override
        default @Nullable String getTargetConfiguration() {
            throw this.stub();
        }

        @Override
        default void setTargetConfiguration(@Nullable String name) {
            throw this.stub();
        }

        @Override
        default AttributeContainer getAttributes() {
            throw this.stub();
        }

        @Override
        default ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
            throw this.stub();
        }

        @Override
        default ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
            throw this.stub();
        }

        @Override
        default List<Capability> getRequestedCapabilities() {
            throw this.stub();
        }

        @Override
        default Set<CapabilitySelector> getCapabilitySelectors() {
            throw this.stub();
        }

        @Override
        default void endorseStrictVersions() {
            throw this.stub();
        }

        @Override
        default void doNotEndorseStrictVersions() {
            throw this.stub();
        }

        @Override
        default boolean isEndorsingStrictVersions() {
            throw this.stub();
        }

        @Override default @Nullable String getGroup() {
            throw this.stub();
        }

        @Override
        default String getName() {
            throw this.stub();
        }

        @Override
        default @Nullable String getVersion() {
            throw this.stub();
        }

        @Override
        default @Nullable String getReason() {
            throw this.stub();
        }

        @Override
        default void because(@Nullable String reason) {
            throw this.stub();
        }

        @Override
        default boolean matchesStrictly(ModuleVersionIdentifier identifier) {
            throw this.stub();
        }

        @Override
        default ModuleIdentifier getModule() {
            throw this.stub();
        }
    }
}
