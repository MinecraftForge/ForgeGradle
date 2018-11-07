/*
 * ForgeGradle
 * Copyright (C) 2018.
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

package net.minecraftforge.gradle.common.diff;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;

import net.minecraftforge.gradle.common.diff.ContextualPatch.Mode;
import net.minecraftforge.gradle.common.util.Utils;

public class ZipContext implements PatchContextProvider {

    private final ZipFile zip;
    private final Map<String, List<String>> modified = new HashMap<>();
    private final Set<String> delete = new HashSet<>();
    private final Map<String, byte[]> binary = new HashMap<>();

    public ZipContext(ZipFile zip) {
        this.zip = zip;
    }

    @Override
    public List<String> getData(ContextualPatch.SinglePatch patch) throws IOException {
        if (modified.containsKey(patch.targetPath))
            return modified.get(patch.targetPath);

        ZipEntry entry = zip.getEntry(patch.targetPath);
        if (entry == null || patch.binary)
            return null;

        try (InputStream input = zip.getInputStream(entry)) {
            return new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8)).lines().collect(Collectors.toList());
        }
    }

    @Override
    public void setData(ContextualPatch.SinglePatch patch, List<String> data) throws IOException {
        if (patch.mode == Mode.DELETE || (patch.binary && patch.hunks.length == 0)) {
            delete.add(patch.targetPath);
            binary.remove(patch.targetPath);
            modified.remove(patch.targetPath);
        } else {
            delete.remove(patch.targetPath);
            if (patch.binary) {
                binary.put(patch.targetPath, Utils.base64DecodeStringList(patch.hunks[0].lines));
                modified.remove(patch.targetPath);
            } else {
                if (!patch.noEndingNewline) {
                    data.add("");
                }
                modified.put(patch.targetPath, data);
                binary.remove(patch.targetPath);
            }
        }
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
            save(out);
        }
    }

    public void save(ZipOutputStream out) throws IOException {
        Set<String> files = new HashSet<>();
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            files.add(entries.nextElement().getName());
        }
        files.addAll(modified.keySet());
        files.addAll(delete);
        files.addAll(binary.keySet());
        List<String> sorted = new ArrayList<>(files);
        Collections.sort(sorted);

        for (String key : sorted) {
            if (delete.contains(key)) {
                continue; // It's Deleted, so NOOP
            }
            putNextEntry(out, key);
            if (binary.containsKey(key)) {
                out.write(binary.get(key));
            } else if (modified.containsKey(key)) {
                out.write(String.join("\n", modified.get(key)).getBytes(StandardCharsets.UTF_8));
            } else {
                try (InputStream ein = zip.getInputStream(zip.getEntry(key))) {
                    IOUtils.copy(ein, out);
                }
            }
            out.closeEntry();
        }
    }

    private void putNextEntry(ZipOutputStream zip, String name) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(0);
        zip.putNextEntry(entry);
    }
}
