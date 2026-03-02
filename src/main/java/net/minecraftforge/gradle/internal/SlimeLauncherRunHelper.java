/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Supplier;

class SlimeLauncherRunHelper {
    // Legacy replacement tokens. See https://github.com/MinecraftForge/ForgeGradle/issues/1048
    static Map<String, Supplier<String>> buildTokens(SlimeLauncherRunTask task, SlimeLauncherOptionsInternal options, Function<SourceSet, Set<String>> sourceOutputs) {
        var ret = new HashMap<String, Supplier<String>>();
        // Should be taken care of by SlimeLauncher
        ret.put("asset_index", () -> "{asset_index}");
        ret.put("assets_root", () -> "{assets_root}");
        ret.put("natives",  () -> "{natives}");

        ret.put("mcp_mappings", task.getMappingChannel().zip(task.getMappingVersion(), (c, v) -> c + '_' + v)::get);
        var minecraft = getClasspath(task.getMinecraftClasspath());
        var runtime = getClasspath(task.getRuntimeClasspath());
        var modules = getClasspath(task.getPatcherModules());
        ret.put("minecraft_classpath", getClasspath(minecraft));
        ret.put("runtime_classpath", getClasspath(runtime));
        ret.put("modules", getClasspath(modules));
        ret.put("minecraft_classpath_file", getClasspathFile(task, "minecraft", minecraft));
        ret.put("runtime_classpath_file", getClasspathFile(task, "runtime_" + task.getSourceSetName().get(), runtime));
        ret.put("mc_version", task.getMinecraftVersion()::get);
        ret.put("mcp_version", task.getMCPVersion()::get);
        ret.put("source_roots", getSourceRoots(task, options, sourceOutputs));
        // Despite the name this is set to createSrgToMcp.getOutput().get().getAsFile().getAbsolutePath() so.. Srg -> MCP .srg mapping file.
        //ret.put("mcp_to_srg", getSrgToMcp().getAsFile().map(File::getAbsolutePath)::get);
        return ret;
    }

    private static Supplier<List<String>> getClasspath(FileCollection files) {
        return new Lazy<>(() -> {
            var ret = new ArrayList<String>(files.getFiles().size());
            for (var file : files.getFiles())
                ret.add(file.getAbsolutePath());
            return ret;
        });
    }

    private static Supplier<String> getClasspath(Supplier<List<String>> files) {
        return new Lazy<>(() -> String.join(File.pathSeparator, files.get()));
    }

    private static Supplier<String> getClasspathFile(SlimeLauncherRunTask task, String name, Supplier<List<String>> files) {
        var file = task.getLocalCacheDir().file(name + "_classpath.txt").get().getAsFile();
        return new Lazy<>(() -> {
            try {
                Files.writeString(file.toPath(), String.join(System.lineSeparator(), files.get()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Error when writing classpath file: " + file.getAbsolutePath(), e);
            }
            return file.getAbsolutePath();
        });
    }

    private static List<SourceSet> getDefaultSourceSets(SlimeLauncherRunTask task) {
        var java = task.getProject().getExtensions().findByType(JavaPluginExtension.class);
        if (java == null)
            return List.of();

        var ret = new ArrayList<SourceSet>();
        var main = java.getSourceSets().findByName(SourceSet.MAIN_SOURCE_SET_NAME);
        if (main != null)
            ret.add(main);

        var taskName = task.getSourceSetName().getOrNull();
        if (taskName != null && !SourceSet.MAIN_SOURCE_SET_NAME.equals(taskName)) {
            var other = java.getSourceSets().findByName(taskName);
            if (other != null)
                ret.add(other);
        }

        return ret;
    }

    // Gets a list of all output directories for
    static Set<String> getOutputs(SourceSet sourceSet) {
        var ret = new LinkedHashSet<String>();
        if (sourceSet.getOutput().getResourcesDir() != null)
            ret.add(sourceSet.getOutput().getResourcesDir().getAbsolutePath());
        for (var file :  sourceSet.getOutput().getFiles())
            ret.add(file.getAbsolutePath());
        return ret;
    }

    static Supplier<String> getSourceRoots(SlimeLauncherRunTask task, SlimeLauncherOptionsInternal options, Function<SourceSet, Set<String>> sourceOutputs) {
        return new Lazy<>(() -> {
            var mods = new TreeMap<String, List<SourceSet>>();
            var defaults = getDefaultSourceSets(task);

            // If there are no mods defined, try and get the main sourceset
            if (options.getMods().isEmpty()) {
                mods.put("", defaults);
            } else {
                for (var mod : options.getMods())
                    mods.put(mod.getName(), mod.getSources().isEmpty() ? defaults : mod.getSources());
            }

            var ret = new StringBuilder();

            for (var entry : mods.entrySet()) {
                var entries = new ArrayList<String>();
                var prefix = entry.getKey().isEmpty() ? "" : entry.getKey() + "%%";
                for (var source : entry.getValue()) {
                    for (var dir : sourceOutputs.apply(source))
                        entries.add(prefix + dir);
                }

                if (entries.size() == 1) // FML Requires at least 2 directories, so duplicate, hacky, but got to love legacy shit
                    entries.add(entries.get(0));

                if (!ret.isEmpty())
                    ret.append(File.pathSeparatorChar);
                ret.append(String.join(File.pathSeparator, entries));
            }

            return ret.toString();
        });
    }

    static void configure(SlimeLauncherRunTask task, MinecraftDependencyInternal mcdep, FileCollection runtimeClasspath) {
        var inst = mcdep.getMavenizerInstance();
        task.getRuntimeClasspath().setFrom(runtimeClasspath); // main classpath gets polluted by Slimelauncher so keep a copy
        task.getMinecraftClasspath().setFrom(mcdep.getMinecraftDependencies());
        task.getPatcherModules().setFrom(mcdep.getPatcherModules());
        task.getMinecraftVersion().set(inst.getMinecraftVersion());
        task.getMCPVersion().set(inst.getMCPVersion());
        task.getMappingChannel().set(inst.getMappingChannel());
        task.getMappingVersion().set(inst.getMappingVersion());
        // We need a way to reverse this file, cuz we want srg->mcp and this is mcp->srg
        //task.getSrgToMcp().set(project.file(inst.getToSrgFile()));
    }
}
