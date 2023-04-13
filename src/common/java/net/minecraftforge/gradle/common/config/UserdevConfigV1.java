/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.config;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class UserdevConfigV1 extends Config {
    public static UserdevConfigV1 get(InputStream stream) {
        return Utils.fromJson(stream, UserdevConfigV1.class);
    }
    public static UserdevConfigV1 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    @Nullable
    public String mcp;    // Do not specify this unless there is no parent.
    @Nullable
    public String parent; // To fully resolve, we must walk the parents until we hit null, and that one must specify a MCP value.
    @Nullable
    public List<String> ats;
    @Nullable
    public List<String> sass;
    @Nullable
    public List<String> srgs;
    @Nullable
    public List<String> srg_lines;
    public String binpatches; //To be applied to joined.jar, remapped, and added to the classpath
    public Function binpatcher;
    public String patches;
    @Nullable
    public String sources;
    @Nullable
    public String universal; //Remapped and added to the classpath, Contains new classes and resources
    @Nullable
    public List<String> libraries; //Additional libraries.
    @Nullable
    public String inject;
    @Nullable
    public Map<String, RunConfig> runs;
    @Nullable
    public String sourceCompatibility;
    @Nullable
    public String targetCompatibility;


    public void addAT(String value) {
        if (this.ats == null) {
            this.ats = new ArrayList<>();
        }
        this.ats.add(value);
    }
    public List<String> getATs() {
        return this.ats == null ? Collections.emptyList() : this.ats;
    }
    public void addSAS(String value) {
        if (this.sass == null) {
            this.sass = new ArrayList<>();
        }
        this.sass.add(value);
    }
    public List<String> getSASs() {
        return this.sass == null ? Collections.emptyList() : this.sass;
    }
    public void addSRG(String value) {
        if (this.srgs == null) {
            this.srgs = new ArrayList<>();
        }
        this.srgs.add(value);
    }
    public void addSRGLine(String value) {
        if (this.srg_lines == null) {
            this.srg_lines = new ArrayList<>();
        }
        this.srg_lines.add(value);
    }
    public void addLibrary(String value) {
        if (this.libraries == null)
            this.libraries = new ArrayList<>();
        this.libraries.add(value);
    }
    public void addRun(String name, RunConfig value) {
        if (this.runs == null)
            this.runs = new HashMap<>();
        this.runs.put(name, value);
    }

    public String getSourceCompatibility() {
        return sourceCompatibility == null ? "1.8" : sourceCompatibility;
    }
    public String getTargetCompatibility() {
        return targetCompatibility == null ? "1.8" : targetCompatibility;
    }
}
