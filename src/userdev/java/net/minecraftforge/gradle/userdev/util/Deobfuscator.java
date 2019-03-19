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

package net.minecraftforge.gradle.userdev.util;

import net.minecraftforge.gradle.common.util.*;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Deobfuscator {
    private final Project project;
    private File cacheRoot;

    public Deobfuscator(Project project, File cacheRoot) {
        this.project = project;
        this.cacheRoot = cacheRoot;
    }

    public File deobfBinary(File original, String mappings, String... cachePath) throws IOException {
        project.getLogger().debug("Deobfuscating binary file {} with mappings {}", original.getName(), mappings);

        File names = findMapping(mappings);
        if (names == null || !names.exists()) {
            return null;
        }

        File output = getCacheFile(cachePath);
        File input = new File(output.getParent(), output.getName() + ".input");

        HashStore cache = new HashStore()
                .load(input)
                .add("names", names)
                .add("orig", original);

        if (!cache.isSame() || !output.exists()) {
            SrgJarRenamer.rename(original, output, names);

            Utils.updateHash(output, HashFunction.SHA1);
            cache.save();
        }

        return output;
    }

    public File deobfSources(File original, String mappings, String... cachePath) throws IOException {
        project.getLogger().debug("Deobfuscating sources file {} with mappings {}", original.getName(), mappings);

        File names = findMapping(mappings);
        if (names == null || !names.exists()) {
            return null;
        }

        File output = getCacheFile(cachePath);

        File input = new File(output.getParent(), output.getName() + ".input");

        HashStore cache = new HashStore()
                .load(input)
                .add("names", names)
                .add("orig", original);

        if (!cache.isSame() || !output.exists()) {
            McpNames map = McpNames.load(names);

            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output))) {
                ZipEntry _old;
                while ((_old = zin.getNextEntry()) != null) {
                    ZipEntry _new = new ZipEntry(_old.getName());
                    _new.setTime(0);
                    zout.putNextEntry(_new);

                    if (_old.getName().endsWith(".java")) {
                        String mapped = map.rename(zin, true);
                        IOUtils.write(mapped, zout);
                    } else {
                        IOUtils.copy(zin, zout);
                    }
                }
            }

            Utils.updateHash(output, HashFunction.SHA1);
            cache.save();
        }

        return output;
    }

    private File getCacheFile(String... cachePath) {
        File cacheFile = new File(cacheRoot, String.join(File.separator, cachePath));
        cacheFile.getParentFile().mkdirs();
        return cacheFile;
    }

    private File findMapping(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
        return MavenArtifactDownloader.manual(project, desc, false);
    }
}
