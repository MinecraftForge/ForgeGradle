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

package net.minecraftforge.gradle.patcher;

import org.gradle.api.Project;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.util.RunConfig;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PatcherExtension {

    public Project parent;
    public File cleanSrc;
    public File patchedSrc;
    public File patches;
    public String mcVersion;
    public boolean srgPatches = true;
    private String mappings;
    private List<Object> extraMappings = new ArrayList<>();
    private List<Object> extraExcs = new ArrayList<>();
    private List<File> accessTransformers = new ArrayList<>();
    private List<File> excs = new ArrayList<>();
    private RunConfig.Container runs = new RunConfig.Container();

    @Inject
    public PatcherExtension(Project project) {
        RunConfig clientRun = new RunConfig();
        clientRun.setMain("mcp.client.Start");
        try {
            clientRun.setWorkingDirectory(project.file("run").getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        runs.getRuns().put(project.getName() + "_client", clientRun);

        RunConfig serverRun = new RunConfig();
        serverRun.setMain("net.minecraft.server.MinecraftServer");
        try {
            serverRun.setWorkingDirectory(project.file("run").getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        runs.getRuns().put(project.getName() + "_server", serverRun);
    }

    public List<Object> getExtraMappings() {
        return extraMappings;
    }

    public List<Object> getExtraExcs() {
        return extraExcs;
    }

    // mappings channel: 'snapshot', version: '20180101'
    public void mappings(Map<String, String> map) {
        String channel = map.get("channel");
        String version = map.get("version");
        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify mappings channel and version");
        }

        if (!version.contains("-")) version = version + "-+";
        setMappings("de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip");
    }
    public void setMappings(String value) {
        mappings = value;
    }
    public String getMappings() {
        return mappings;
    }

    public void extraMapping(Object mapping) {
        if (mapping instanceof String || mapping instanceof File) {
            extraMappings.add(mapping);
        } else {
            throw new IllegalArgumentException("Extra mappings must be a file or a string!");
        }
    }

    public void extraExc(Object exc) {
        extraExcs.add(exc); // TODO: Type check!
    }

    void copyFrom(PatcherExtension other) {
        if (mappings == null) {
            this.setMappings(other.getMappings());
        }
        if (this.mcVersion == null) {
            this.mcVersion = other.mcVersion;
        }
    }

    public void accessTransformer(File file) {
        this.setAccessTransformer(file);
    }
    public void setAccessTransformer(File file) {
        this.accessTransformers.add(file);
    }
    public void setAccessTransformers(List<File> files) {
         this.accessTransformers.clear();
         this.accessTransformers.addAll(files);
    }
    public List<File> getAccessTransformers() {
        return accessTransformers;
    }


    public void exc(File file) {
        this.setExc(file);
    }
    public void setExc(File file) {
        this.excs.add(file);
    }
    public void setExc(List<File> files) {
         this.excs.clear();
         this.excs.addAll(files);
    }
    public List<File> getExcs() {
        return excs;
    }

    public void runs(Closure<? super RunConfig.Container> value) {
        value.setResolveStrategy(Closure.DELEGATE_FIRST);
        value.setDelegate(runs);
        value.call();
    }
    public Map<String, RunConfig> getRuns() {
        return this.runs.getRuns();
    }
}
