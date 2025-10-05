/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import groovy.transform.Generated;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import net.minecraftforge.accesstransformers.gradle.AccessTransformersConfiguration;
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
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

interface ClosureOwnerInternal<D> extends ClosureOwner<D> {
    private static RuntimeException stub() {
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
            throw stub();
        }

        @Override
        default ExternalModuleDependency setChanging(boolean changing) {
            throw stub();
        }

        @Override
        default ExternalModuleDependency copy() {
            throw stub();
        }

        @Override
        default boolean isForce() {
            throw stub();
        }

        @Override
        default void version(Action<? super MutableVersionConstraint> configureAction) {
            throw stub();
        }

        @Override
        default VersionConstraint getVersionConstraint() {
            throw stub();
        }

        @Override
        default ModuleDependency exclude(Map<String, String> excludeProperties) {
            throw stub();
        }

        @Override
        default Set<ExcludeRule> getExcludeRules() {
            throw stub();
        }

        @Override
        default Set<DependencyArtifact> getArtifacts() {
            throw stub();
        }

        @Override
        default ModuleDependency addArtifact(DependencyArtifact artifact) {
            throw stub();
        }

        @Override
        default DependencyArtifact artifact(Closure configureClosure) {
            throw stub();
        }

        @Override
        default DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
            throw stub();
        }

        @Override
        default boolean isTransitive() {
            throw stub();
        }

        @Override
        default ModuleDependency setTransitive(boolean transitive) {
            throw stub();
        }

        @Override
        default @Nullable String getTargetConfiguration() {
            throw stub();
        }

        @Override
        default void setTargetConfiguration(@Nullable String name) {
            throw stub();
        }

        @Override
        default AttributeContainer getAttributes() {
            throw stub();
        }

        @Override
        default ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
            throw stub();
        }

        @Override
        default ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
            throw stub();
        }

        @Override
        default List<Capability> getRequestedCapabilities() {
            throw stub();
        }

        @Override
        default Set<CapabilitySelector> getCapabilitySelectors() {
            throw stub();
        }

        @Override
        default void endorseStrictVersions() {
            throw stub();
        }

        @Override
        default void doNotEndorseStrictVersions() {
            throw stub();
        }

        @Override
        default boolean isEndorsingStrictVersions() {
            throw stub();
        }

        @Override default @Nullable String getGroup() {
            throw stub();
        }

        @Override
        default String getName() {
            throw stub();
        }

        @Override
        default @Nullable String getVersion() {
            throw stub();
        }

        @Override
        default @Nullable String getReason() {
            throw stub();
        }

        @Override
        default void because(@Nullable String reason) {
            throw stub();
        }

        @Override
        default boolean matchesStrictly(ModuleVersionIdentifier identifier) {
            throw stub();
        }

        @Override
        default ModuleIdentifier getModule() {
            throw stub();
        }
    }

    non-sealed interface MinecraftDependencyWithAccessTransformers extends ClosureOwnerInternal<net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers>, ClosureOwner.MinecraftDependencyWithAccessTransformers, HasPublicType {
        @Override
        default TypeOf<?> getPublicType() {
            return TypeOf.typeOf(ClosureOwner.MinecraftDependencyWithAccessTransformers.class);
        }

        net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers getOwnerDelegate();

        @Override
        default RegularFileProperty getAccessTransformer() {
            return this.getOwnerDelegate().getAccessTransformer();
        }

        @Override
        default void setAccessTransformer(String accessTransformer) {
            this.getOwnerDelegate().setAccessTransformer(accessTransformer);
        }

        @Override
        default void setAccessTransformer(boolean accessTransformer) {
            this.getOwnerDelegate().setAccessTransformer(accessTransformer);
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
            throw stub();
        }

        @Override
        default ExternalModuleDependency setChanging(boolean changing) {
            throw stub();
        }

        @Override
        default ExternalModuleDependency copy() {
            throw stub();
        }

        @Override
        default boolean isForce() {
            throw stub();
        }

        @Override
        default void version(Action<? super MutableVersionConstraint> configureAction) {
            throw stub();
        }

        @Override
        default VersionConstraint getVersionConstraint() {
            throw stub();
        }

        @Override
        default ModuleDependency exclude(Map<String, String> excludeProperties) {
            throw stub();
        }

        @Override
        default Set<ExcludeRule> getExcludeRules() {
            throw stub();
        }

        @Override
        default Set<DependencyArtifact> getArtifacts() {
            throw stub();
        }

        @Override
        default ModuleDependency addArtifact(DependencyArtifact artifact) {
            throw stub();
        }

        @Override
        default DependencyArtifact artifact(Closure configureClosure) {
            throw stub();
        }

        @Override
        default DependencyArtifact artifact(Action<? super DependencyArtifact> configureAction) {
            throw stub();
        }

        @Override
        default boolean isTransitive() {
            throw stub();
        }

        @Override
        default ModuleDependency setTransitive(boolean transitive) {
            throw stub();
        }

        @Override
        default @Nullable String getTargetConfiguration() {
            throw stub();
        }

        @Override
        default void setTargetConfiguration(@Nullable String name) {
            throw stub();
        }

        @Override
        default AttributeContainer getAttributes() {
            throw stub();
        }

        @Override
        default ModuleDependency attributes(Action<? super AttributeContainer> configureAction) {
            throw stub();
        }

        @Override
        default ModuleDependency capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configureAction) {
            throw stub();
        }

        @Override
        default List<Capability> getRequestedCapabilities() {
            throw stub();
        }

        @Override
        default Set<CapabilitySelector> getCapabilitySelectors() {
            throw stub();
        }

        @Override
        default void endorseStrictVersions() {
            throw stub();
        }

        @Override
        default void doNotEndorseStrictVersions() {
            throw stub();
        }

        @Override
        default boolean isEndorsingStrictVersions() {
            throw stub();
        }

        @Override default @Nullable String getGroup() {
            throw stub();
        }

        @Override
        default String getName() {
            throw stub();
        }

        @Override
        default @Nullable String getVersion() {
            throw stub();
        }

        @Override
        default @Nullable String getReason() {
            throw stub();
        }

        @Override
        default void because(@Nullable String reason) {
            throw stub();
        }

        @Override
        default boolean matchesStrictly(ModuleVersionIdentifier identifier) {
            throw stub();
        }

        @Override
        default ModuleIdentifier getModule() {
            throw stub();
        }
    }
}
