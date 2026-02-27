/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.file.FileCollection;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

class SlimeLauncherRunHelper {
    // Legacy replacement tokens. See https://github.com/MinecraftForge/ForgeGradle/issues/1048
    static Map<String, Supplier<String>> buildTokens(SlimeLauncherRunTask task) {
        var ret = new HashMap<String, Supplier<String>>();
        // Should be taken care of by SlimeLauncher
        ret.put("asset_index", () -> "{asset_index}");
        ret.put("assets_root", () -> "{assets_root}");
        ret.put("natives",  () -> "{natives}");


        ret.put("mcp_mappings", task.getMappingChannel().zip(task.getMappingVersion(), (c, v) -> c + '_' + v)::get);
        var minecraft = getClasspath(task.getMinecraftClasspath());
        var runtime = getClasspath(task.getRuntimeClasspath());
        ret.put("minecraft_classpath", getClasspath(minecraft));
        ret.put("runtime_classpath", getClasspath(runtime));
        ret.put("minecraft_classpath_file", getClasspathFile(task, "minecraft", minecraft));
        ret.put("runtime_classpath_file", getClasspathFile(task, "runtime_" + task.getSourceSetName().get(), runtime));
        // Despite the name this is set to createSrgToMcp.getOutput().get().getAsFile().getAbsolutePath() so.. Srg -> MCP .srg mapping file.
        //ret.put("mcp_to_srg", getSrgToMcp().getAsFile().map(File::getAbsolutePath)::get);

        // Pending:
        //mc_version    The simple vanilla Minecraft Version - 1.21.11
        //mcp_version   The full MCP Config version - 1.21.11-000000000.000000
        //modules       The classpath of a detached configuration containing the dependencies from UserDevV2.modules
        //source_roots  The destination directories of each sourceset added to this run config. This is the old hacky thing for people who don't merge their sourcesets. Or use older versions that manually builds them. May not actually need this.
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
}
