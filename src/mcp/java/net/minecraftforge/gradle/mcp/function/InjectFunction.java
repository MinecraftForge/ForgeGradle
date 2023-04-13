/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import net.minecraftforge.srgutils.MinecraftVersion;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

class InjectFunction implements MCPFunction {
    private static final MinecraftVersion v1_14_4 = MinecraftVersion.from("1.14.4");

    private String inject;
    private String template;
    private Map<String, byte[]> added;

    @Override
    public void loadData(Map<String, String> data) {
        inject = data.get("inject");
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        added = zip.stream().filter(e -> !e.isDirectory() && e.getName().startsWith(inject))
        .collect(Collectors.toMap(e -> e.getName().substring(inject.length()), e -> {
            try {
                return IOUtils.toByteArray(zip.getInputStream(e));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
        if (added.containsKey("package-info-template.java")) {
            template = new String(added.get("package-info-template.java"), StandardCharsets.UTF_8);
            added.remove("package-info-template.java");
        }
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = (File)environment.getArguments().get("input");
        File output = environment.getFile("output.jar");

        File hashFile = environment.getFile("lastinput.sha1");
        HashStore hashStore = new HashStore(environment.project).load(hashFile);
        if (hashStore.isSame(input) && output.exists()) return output;

        if (output.exists()) output.delete();
        if (!output.getParentFile().exists()) output.getParentFile().mkdirs();
        output.createNewFile();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(input));
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(output)) ) {

            Set<String> visited = new HashSet<>();

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                zos.putNextEntry(entry);
                IOUtils.copyLarge(zis, zos);
                zos.closeEntry();
                if (template != null) {
                    String pkg = entry.isDirectory() && !entry.getName().endsWith("/") ? entry.getName() : entry.getName().indexOf('/') == -1 ? "" : entry.getName().substring(0, entry.getName().lastIndexOf('/'));
                    if (visited.add(pkg)) {
                        if (!pkg.startsWith("net/minecraft/") &&
                            (!pkg.startsWith("com/mojang/") || environment.getMinecraftVersion().compareTo(v1_14_4) <= 0)) //Add com/mojang package-infos in 1.15+, could probably get away without the version check
                            continue;
                        zos.putNextEntry(Utils.getStableEntry(pkg + "/package-info.java"));
                        zos.write(template.replace("{PACKAGE}", pkg.replaceAll("/", ".")).getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }

            for (Entry<String, byte[]> add : added.entrySet()) {
                boolean filter = "server".equals(environment.side) ? add.getKey().contains("/client/") : add.getKey().contains("/server/");
                if (filter)
                    continue;
                ZipEntry info = new ZipEntry(add.getKey());
                info.setTime(0);
                zos.putNextEntry(info);
                zos.write(add.getValue());
                zos.closeEntry();
            }
        }

        hashStore.save(hashFile);
        return output;
    }

    @Override
    public  void cleanup(MCPEnvironment environment) {
        if (this.added != null) {
            this.added.clear();
        }
    }

}
