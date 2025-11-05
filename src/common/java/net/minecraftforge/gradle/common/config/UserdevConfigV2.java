/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.config;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.util.Utils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UserdevConfigV2 extends UserdevConfigV1 {
    public static UserdevConfigV2 get(InputStream stream) {
        return Utils.fromJson(stream, UserdevConfigV2.class);
    }

    public static UserdevConfigV2 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    public DataFunction processor;
    public String patchesOriginalPrefix;
    public String patchesModifiedPrefix;
    @Nullable
    private Boolean notchObf; //This is a Boolean so we can set to null and it won't be printed in the json.
    @Nullable
    private List<String> universalFilters;
    @Nullable
    public List<String> modules; // Modules passed to --module-path
    @Nullable
    @Deprecated
    @ApiStatus.ScheduledForRemoval(inVersion = "V3")
    public ScopedDependencies extraDependencies;
    private String sourceFileCharset = StandardCharsets.UTF_8.name();

    public void setNotchObf(boolean value) {
        this.notchObf = value ? true : null;
    }

    public boolean getNotchObf() {
        return this.notchObf != null && this.notchObf;
    }

    public void setSourceFileCharset(String value) {
        if (!Charset.isSupported(value)) {
            throw new IllegalArgumentException("Unsupported charset: " + value);
        }
        sourceFileCharset = value;
    }

    public String getSourceFileCharset() {
        return sourceFileCharset;
    }

    public void addUniversalFilter(String value) {
        if (universalFilters == null)
            universalFilters = new ArrayList<>();
        universalFilters.add(value);
    }

    public List<String> getUniversalFilters() {
        return universalFilters == null ? Collections.emptyList() : universalFilters;
    }

    public void addModule(String value) {
        if (modules == null)
            modules = new ArrayList<>();
        modules.add(value);
    }

    @Nullable
    public List<String> getModules() {
        return modules;
    }

    public static class DataFunction extends Function {
        protected Map<String, String> data;

        public Map<String, String> getData() {
            return this.data == null ? Collections.emptyMap() : data;
        }

        @Nullable
        public String setData(String name, String path) {
            if (this.data == null)
                this.data = new HashMap<>();
            return this.data.put(name, path);
        }
    }

    private void addRuntimeDependency(String dependency) {
        if (this.extraDependencies == null)
            extraDependencies = new ScopedDependencies();

        if (this.extraDependencies.runtimeOnly == null)
            this.extraDependencies.runtimeOnly = new ArrayList<>();

        this.extraDependencies.runtimeOnly.add(dependency);
    }

    private void addCompileDependency(String dependency) {
        if (this.extraDependencies == null)
            extraDependencies = new ScopedDependencies();

        if (this.extraDependencies.compileOnly == null)
            this.extraDependencies.compileOnly = new ArrayList<>();

        this.extraDependencies.compileOnly.add(dependency);
    }

    private void addAnnotationProcessorDependency(String dependency) {
        if (this.extraDependencies == null)
            extraDependencies = new ScopedDependencies();

        if (this.extraDependencies.annotationProcessor == null)
            this.extraDependencies.annotationProcessor = new ArrayList<>();

        this.extraDependencies.annotationProcessor.add(dependency);
    }

    public static class ScopedDependencies {
        public @Nullable List<String> runtimeOnly;
        public @Nullable List<String> compileOnly;
        public @Nullable List<String> annotationProcessor;
    }
}
