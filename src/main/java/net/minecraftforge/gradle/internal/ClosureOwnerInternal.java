/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.transform.Generated;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import net.minecraftforge.gradle.ClosureOwner;
import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
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
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

interface ClosureOwnerInternal<D> extends ClosureOwner {
    /// Gets the owner delegate for this closure owner.
    ///
    /// The owner delegate sits on top of the [Closure][groovy.lang.Closure]'s original
    /// {@linkplain groovy.lang.Closure#getOwner() owner}, and is used primarily on top of it when the closure owner is
    /// invoked. If a member can't be found in the owner delegate, the original owner is queried instead.
    ///
    /// @return The owner delegate
    D getOwnerDelegate();

    private static RuntimeException stub() {
        return new UnsupportedOperationException();
    }

    interface MinecraftDependency extends ClosureOwnerInternal<net.minecraftforge.gradle.MinecraftDependency>, ClosureOwner.MinecraftDependency, HasPublicType {
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
        default NamedDomainObjectContainer<? extends SlimeLauncherOptions> getRuns() {
            return this.getOwnerDelegate().getRuns();
        }

        @Override
        default void runs(Closure<?> closure) {
            this.getOwnerDelegate().runs(closure);
        }

        @Override
        default void runs(Action<? super NamedDomainObjectContainer<? extends SlimeLauncherOptions>> action) {
            this.getOwnerDelegate().runs(action);
        }

        @Override
        default ConfigurableFileCollection getAccessTransformer() {
            return this.getOwnerDelegate().getAccessTransformer();
        }

        @Override
        default ConfigurableFileCollection getAccessTransformers() {
            return this.getOwnerDelegate().getAccessTransformers();
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
