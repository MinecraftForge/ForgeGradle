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

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.gradle.common.config.UserdevConfigV2.DataFunction;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.common.util.RunConfig;
import org.gradle.api.Project;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PatcherExtension extends MinecraftExtension {

    public static final String EXTENSION_NAME = "patcher";

    public Project parent;

    public File cleanSrc, patchedSrc, patches;
    public String mcVersion;

    public boolean srgPatches = true;
    private boolean notchObf = false;

    private List<File> excs;
    private List<Object> extraExcs, extraMappings;

    private DataFunction processor;
    private Map<String, File> processorData;

    public PatcherExtension(@Nonnull final Project project) {
        super(project);

        ImmutableMap.<String, String>builder()
                .put(project.getName() + "_client", "mcp.client.Start")
                .put(project.getName() + "_server", "net.minecraft.server.MinecraftServer")
                .build().forEach((name, main) -> {
            RunConfig run = new RunConfig(project, name);

            run.setTaskName(name);
            run.setMain(main);

            try {
                run.setWorkingDirectory(project.file("run").getCanonicalPath());
            } catch (IOException e) {
                e.printStackTrace();
            }

            getRuns().add(run);
        });
    }

    public void setParent(Project parent) {
        this.parent = parent;
    }

    public void parent(Project parent) {
        setParent(parent);
    }

    public Project getParent() {
        return parent;
    }

    public void setCleanSrc(File cleanSrc) {
        this.cleanSrc = cleanSrc;
    }

    public void cleanSrc(File cleanSrc) {
        setCleanSrc(cleanSrc);
    }

    public File getCleanSrc() {
        return cleanSrc;
    }

    public void setPatchedSrc(File patchedSrc) {
        this.patchedSrc = patchedSrc;
    }

    public void patchedSrc(File patchedSrc) {
        setPatchedSrc(patchedSrc);
    }

    public File getPatchedSrc() {
        return patchedSrc;
    }

    public void setPatches(File patches) {
        this.patches = patches;
    }

    public void patches(File patches) {
        setPatches(patches);
    }

    public File getPatches() {
        return patches;
    }

    public void setMcVersion(String mcVersion) {
        this.mcVersion = mcVersion;
    }

    public void mcVersion(String mcVersion) {
        setMcVersion(mcVersion);
    }

    public String getMcVersion() {
        return mcVersion;
    }

    public void setSrgPatches(boolean srgPatches) {
        this.srgPatches = srgPatches;
    }

    public void srgPatches(boolean srgPatches) {
        setSrgPatches(srgPatches);
    }

    public void srgPatches() {
        setSrgPatches(true);
    }

    public boolean isSrgPatches() {
        return srgPatches;
    }

    public void setNotchObf(boolean value) {
        this.notchObf = value;
    }

    public boolean getNotchObf() {
        return this.notchObf;
    }

    public void setExcs(List<File> excs) {
        this.excs = new ArrayList<>(excs);
    }

    public void setExcs(File... excs) {
        setExcs(Arrays.asList(excs));
    }

    public void setExc(File exc) {
        setExcs(exc);
    }

    public void excs(File... excs) {
        getExcs().addAll(Arrays.asList(excs));
    }

    public void exc(File exc) {
        excs(exc);
    }

    @Nonnull
    public List<File> getExcs() {
        if (excs == null) {
            excs = new ArrayList<>();
        }

        return excs;
    }

    public void setExtraExcs(List<Object> extraExcs) {
        this.extraExcs = new ArrayList<>(extraExcs);
    }

    public void extraExcs(Object... excs) {
        getExtraExcs().addAll(Arrays.asList(excs)); // TODO: Type check!
    }

    public void extraExc(Object exc) {
        extraExcs(exc); // TODO: Type check!
    }

    @Nonnull
    public List<Object> getExtraExcs() {
        if (extraExcs == null) {
            extraExcs = new ArrayList<>();
        }

        return extraExcs;
    }

    public void extraMapping(Object mapping) {
        if (mapping instanceof String || mapping instanceof File) {
            getExtraMappings().add(mapping);
        } else {
            throw new IllegalArgumentException("Extra mappings must be a file or a string!");
        }
    }

    public void setExtraMappings(List<Object> extraMappings) {
        this.extraMappings = new ArrayList<>(extraMappings);
    }

    @Nonnull
    public List<Object> getExtraMappings() {
        if (extraMappings == null) {
            extraMappings = new ArrayList<>();
        }

        return extraMappings;
    }

    public DataFunction getProcessor() {
        return this.processor;
    }
    public void setProcessor(Map<String, Object> map) {
        processor(map);
    }

    public Map<String, File> getProcessorData() {
        return this.processorData == null ? Collections.emptyMap() : this.processorData;
    }

    @SuppressWarnings("unchecked")
    public void processor(Map<String, Object> map) {
        this.processor = new DataFunction();
        map.forEach((key, value) -> {
            if ("tool".equals(key)) {
                if (!(value instanceof String))
                    throw new IllegalArgumentException("'tool' must be a string");
                this.processor.setVersion((String)value);
            } else if ("args".equals(key)) {
                if (value instanceof String)
                    this.processor.setArgs(Arrays.asList((String)value));
                else if (value instanceof String[])
                    this.processor.setArgs(Arrays.asList((String[])value));
                else if (value instanceof Collection)
                    this.processor.setArgs(new ArrayList<>((Collection<String>)value));
                else
                    throw new IllegalArgumentException("'args' must be a String, or array of Strings");
            } else if ("repo".equals(key)) {
                if (!(value instanceof String))
                    throw new IllegalArgumentException("'repo' must be a string");
                this.processor.setRepo((String)value);
            } else if ("data".equals(key)) {
                if (!(value instanceof Map))
                    throw new IllegalArgumentException("'data' must be a map of string -> file");
                this.processorData = (Map<String, File>)value;
            } else {
                throw new IllegalArgumentException("Invalid processor key " + key);
            }
        });
    }

    void copyFrom(PatcherExtension other) {
        if (mapping_channel == null) {
            setMappingChannel(other.getMappingChannel());
        }
        if (mapping_version == null) {
            setMappingVersion(other.getMappingVersion());
        }

        if (mcVersion == null) {
            setMcVersion(other.getMcVersion());
        }
    }
}
