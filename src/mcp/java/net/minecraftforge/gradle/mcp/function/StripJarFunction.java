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

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

public class StripJarFunction implements MCPFunction {

    private String mappings;
    private Set<String> filter;

    @Override
    public void loadData(Map<String, String> data) {
        mappings = data.get("mappings");
    }

    @Override
    public void initialize(MCPEnvironment environment, ZipFile zip) throws IOException {
        // Read valid file names from mapping
        BufferedReader br = new BufferedReader(new InputStreamReader(zip.getInputStream(zip.getEntry(mappings))));
        filter = br.lines().filter(l -> !l.startsWith("\t")).map(s -> s.split(" ")[0] + ".class").collect(Collectors.toSet());
        br.close();
    }

    @Override
    public File execute(MCPEnvironment environment) throws Exception {
        File input = (File)environment.getArguments().get("input");
        File output = environment.getFile("output.jar");
        boolean whitelist = ((String)environment.getArguments().getOrDefault("mode", "whitelist")).equalsIgnoreCase("whitelist");

        File hashFile = environment.getFile("lastinput.sha1");
        HashStore hashStore = new HashStore(environment.project).load(hashFile);
        if (hashStore.isSame(input) && output.exists()) return output;

        Utils.createEmpty(output);
        strip(input, output, whitelist);

        hashStore.save(hashFile);
        return output;
    }

    private void strip(File input, File output, boolean whitelist) throws IOException {
        JarInputStream is = new JarInputStream(new FileInputStream(input));
        JarOutputStream os = new JarOutputStream(new FileOutputStream(output));

        // Ignore any entry that's not allowed
        JarEntry entry;
        while ((entry = is.getNextJarEntry()) != null) {
            if (!isEntryValid(entry, whitelist)) continue;
            os.putNextEntry(entry);
            IOUtils.copyLarge(is, os);
            os.closeEntry();
        }

        os.close();
        is.close();
    }

    private boolean isEntryValid(JarEntry entry, boolean whitelist) {
        return !entry.isDirectory() && filter.contains(entry.getName()) == whitelist;
    }

}
