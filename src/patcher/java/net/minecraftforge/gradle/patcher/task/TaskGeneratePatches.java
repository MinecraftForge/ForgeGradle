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

package net.minecraftforge.gradle.patcher.task;

import org.apache.commons.io.IOUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.cloudbees.diff.Diff;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TaskGeneratePatches extends DefaultTask {
    private String originalPrefix = "a/";
    private String modifiedPrefix = "b/";
    private File clean;
    private File modified;
    private File patches;

    @TaskAction
    public void generatePatches() throws IOException {
        Set<Path> paths = new HashSet<>();
        Files.walk(getPatches().toPath()).filter(Files::isRegularFile).forEach(paths::add);
        try (ZipFile clean = new ZipFile(getClean());
             ZipFile dirty = new ZipFile(getModified())) {
            Set<String> _old = Collections.list(clean.entries()).stream().filter(e -> !e.isDirectory()).map(e -> e.getName()).collect(Collectors.toSet());
            Set<String> _new = Collections.list(dirty.entries()).stream().filter(e -> !e.isDirectory()).map(e -> e.getName()).collect(Collectors.toSet());
            for (String o : _old) {
                ZipEntry newEntry = dirty.getEntry(o);
                String diff = makePatch(o, clean.getInputStream(clean.getEntry(o)), newEntry == null ? null : dirty.getInputStream(newEntry));
                _new.remove(o);
                if (diff != null) {
                    File patch = new File(getPatches(), o + ".patch");
                    writePatch(patch, diff);
                    paths.remove(patch.toPath());
                }
            }
            for (String n : _new) {
                String diff = makePatch(n, null, dirty.getInputStream(dirty.getEntry(n)));
                if (diff != null) {
                    File patch = new File(getPatches(), n + ".patch");
                    writePatch(patch, diff);
                    paths.remove(patch.toPath());
                }
            }
        }
        paths.forEach(p -> p.toFile().delete());
        List<File> dirs = Files.walk(getPatches().toPath()).filter(Files::isDirectory).map(p -> p.toFile()).collect(Collectors.toList());
        Collections.reverse(dirs);
        dirs.forEach(p -> {
           if (p.list().length == 0)
               p.delete();
        });

    }

    private void writePatch(File patch, String diff) throws IOException {
        File parent = patch.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream fos = new FileOutputStream(patch)) {
            IOUtils.write(diff, fos, StandardCharsets.UTF_8);
        }
    }

    private String makePatch(String relative, InputStream original, InputStream modified) throws IOException {
        String originalRelative = original == null ? "/dev/null" : originalPrefix + relative;
        String modifiedRelative = modified == null ? "/dev/null" : modifiedPrefix + relative;

        String originalData = original == null ? "" : new String(IOUtils.toByteArray(original), StandardCharsets.UTF_8);
        String modifiedData = modified == null ? "" : new String(IOUtils.toByteArray(modified), StandardCharsets.UTF_8);

        final Diff diff = Diff.diff(new StringReader(originalData), new StringReader(modifiedData), false);

        if (!diff.isEmpty()) {
            return diff.toUnifiedDiff(originalRelative, modifiedRelative,
                    new StringReader(originalData), new StringReader(modifiedData), 3)
                    .replaceAll("\r?\n", "\n");
        }
        return null;
    }

    @InputFile
    public File getClean() {
        return clean;
    }

    @InputFile
    public File getModified() {
        return modified;
    }

    @Input
    public String getOriginalPrefix() {
    	return this.originalPrefix;
    }

    @Input
    public String getModifiedPrefix() {
    	return this.modifiedPrefix;
    }

    @OutputDirectory
    public File getPatches() {
        return patches;
    }

    public void setClean(File clean) {
        this.clean = clean;
    }

    public void setModified(File modified) {
        this.modified = modified;
    }

    public void setOriginalPrefix(String value) {
    	this.originalPrefix = value;
    }

    public void setModifiedPrefix(String value) {
    	this.modifiedPrefix = value;
    }

    public void setPatches(File patches) {
        this.patches = patches;
    }

}
