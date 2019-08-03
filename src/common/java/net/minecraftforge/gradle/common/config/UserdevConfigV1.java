/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.common.config.MCPConfigV1.Function;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;

public class UserdevConfigV1 extends Config {
    public static UserdevConfigV1 get(InputStream stream) {
        return Utils.fromJson(stream, UserdevConfigV1.class);
    }
    public static UserdevConfigV1 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    public String mcp;    // Do not specify this unless there is no parent.
    public String parent; // To fully resolve, we must walk the parents until we hit null, and that one must specify a MCP value.
    public List<String> ats;
    public List<String> sass;
    public List<String> srgs;
    public List<String> srg_lines;
    public String binpatches; //To be applied to joined.jar, remapped, and added to the classpath
    public Function binpatcher;
    public String patches;
    public String sources;
    public String universal; //Remapped and added to the classpath, Contains new classes and resources
    public List<String> libraries; //Additional libraries.
    public String inject;
    public Map<String, RunConfig> runs;
    public String sourceCompatibility;
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
