/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.tasks.fernflower;

import com.google.common.base.Charsets;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

class ArtifactSaver implements IResultSaver {
    private final Map<String, ZipOutputStream> mapArchiveStreams = new HashMap<>();
    private final Map<String, Set<String>> mapArchiveEntries = new HashMap<>();
    private final File root;
    public ArtifactSaver(File tempDir) {
        this.root = tempDir;
    }

    private String getAbsolutePath(String path) {
        return new File(root, path).getAbsolutePath();
      }


    @Override
    public void saveFolder(String path) {
        File dir = new File(getAbsolutePath(path));
        if (!(dir.mkdirs() || dir.isDirectory())) {
            throw new RuntimeException("Cannot create directory " + dir);
        }
    }

    @Override
    public void copyFile(String source, String path, String entryName) {
        try {
            InterpreterUtil.copyFile(new File(source), new File(getAbsolutePath(path), entryName));
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
        }
    }

    @Override
    public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
        File file = new File(getAbsolutePath(path), entryName);
        try {
            try (Writer out = new OutputStreamWriter(new FileOutputStream(file), Charsets.UTF_8))
            {
                out.write(content);
            }
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot write class file " + file, ex);
        }
    }

    @Override
    public void createArchive(String path, String archiveName, Manifest manifest) {
        File file = new File(getAbsolutePath(path), archiveName);
        try {
            if (!(file.createNewFile() || file.isFile())) {
                throw new IOException("Cannot create file " + file);
            }

            FileOutputStream fileStream = new FileOutputStream(file);
            ZipOutputStream zipStream = manifest != null ? new JarOutputStream(fileStream, manifest) : new ZipOutputStream(fileStream);
            mapArchiveStreams.put(file.getPath(), zipStream);
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot create archive " + file, ex);
        }
    }

    @Override
    public void saveDirEntry(String path, String archiveName, String entryName) {
        saveClassEntry(path, archiveName, null, entryName, null);
    }

    @Override
    public void copyEntry(String source, String path, String archiveName, String entryName) {
        String file = new File(getAbsolutePath(path), archiveName).getPath();

        if (!checkEntry(entryName, file)) {
            return;
        }


        try (ZipFile srcArchive = new ZipFile(new File(source))) {
            ZipEntry entry = srcArchive.getEntry(entryName);
            if (entry != null) {
                try (InputStream in = srcArchive.getInputStream(entry)) {
                    ZipOutputStream out = mapArchiveStreams.get(file);
                    try {
                        out.putNextEntry(new ZipEntry(entryName));
                        InterpreterUtil.copyStream(in, out);
                    } finally {
                        out.closeEntry();
                    }
                }
            }
        } catch (IOException ex) {
            String message = "Cannot copy entry " + entryName + " from " + source + " to " + file;
            DecompilerContext.getLogger().writeMessage(message, ex);
        }
    }

    @Override
    public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
        String file = new File(getAbsolutePath(path), archiveName).getPath();

        if (!checkEntry(entryName, file)) {
            return;
        }

        try {
            ZipOutputStream out = mapArchiveStreams.get(file);
            out.putNextEntry(new ZipEntry(entryName));
            if (content != null) {
                out.write(content.getBytes(Charsets.UTF_8));
            }
        } catch (IOException ex) {
            String message = "Cannot write entry " + entryName + " to " + file;
            DecompilerContext.getLogger().writeMessage(message, ex);
        }
    }

    private boolean checkEntry(String entryName, String file) {
        Set<String> set = mapArchiveEntries.computeIfAbsent(file, k -> new HashSet<>());

        boolean added = set.add(entryName);
        if (!added) {
            String message = "Zip entry " + entryName + " already exists in " + file;
            DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
        }
        return added;
    }

    @Override
    public void closeArchive(String path, String archiveName) {
        String file = new File(getAbsolutePath(path), archiveName).getPath();
        try {
            mapArchiveEntries.remove(file);
            mapArchiveStreams.remove(file).close();
        } catch (IOException ex) {
            DecompilerContext.getLogger().writeMessage("Cannot close " + file, IFernflowerLogger.Severity.WARN);
        }
    }

    boolean areAnyArchiveStreamsOpen() {
        return !mapArchiveStreams.isEmpty();
    }
}
