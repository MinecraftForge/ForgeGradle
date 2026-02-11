/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.json.JsonSlurper;
import net.minecraftforge.gradle.MavenizerInstance;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;

class MavenizerInstanceImpl implements MavenizerInstance {
    private final MinecraftExtensionImpl.ForProjectImpl extension;
    private final Provider<Boolean> valueSource;
    private final ExternalModuleDependency dependency;
    private final File jsonFile;

    private final MapProperty<String, String> invoke;
    private @Nullable Map<String, String> map;

    MavenizerInstanceImpl(
        MinecraftExtensionImpl.ForProjectImpl extension,
        Provider<Boolean> valueSource,
        ExternalModuleDependency dependency,
        File jsonFile
    ) {
        this.extension = extension;
        this.dependency = dependency;
        this.valueSource = valueSource;
        this.jsonFile = jsonFile;
        this.invoke = this.extension.getObjects().mapProperty(String.class, String.class)
            .convention(this.extension.getProviders().provider(this::invoke));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> invoke() {
        if (this.map == null) {
            valueSource.get(); // Execute Mavenizer, probably called before, but just be sure.
            this.map = (Map<String, String>) new JsonSlurper().parse(this.jsonFile, "UTF-8");
            //this.map.forEach((k, v) -> this.extension.getProject().getLogger().lifecycle(k + " => " + v));
        }
        return this.map;
    }

    private Provider<String> get(String key) {
        return this.invoke.getting(key)
            .orElse(this.extension.getProviders().provider(() -> {
                throw new IllegalStateException("Mavenizer did not output expected json data " + key);
            }));
    }

    @Override
    public Provider<ExternalModuleDependency> asProvider() {
        return this.invoke.map(m -> this.dependency);
    }

    @Override
    public Provider<String> getMappingVersion() {
        return get("mappings.version");
    }

    @Override
    public Provider<String> getToSrg() {
        return get("mappings.srg.artifact");
    }

    @Override
    public Provider<File> getToSrgFile() {
        return get("mappings.srg.file").map(this.extension.getProject()::file);
    }

    @Override
    public Provider<String> getToObf() {
        return get("mappings.obf.artifact");
    }

    @Override
    public Provider<File> getToObfFile() {
        return get("mappings.obf.file").map(this.extension.getProject()::file);
    }
}
