/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

// Quick reader class for the v1.0 bundler format used by the server installers in 1.18+
class BundlerUtils {
    private static final Attributes.Name BUNDLER_FORMAT = new Attributes.Name("Bundler-Format");

    static Path extractMainJar(Path raw, Path target) throws IOException {

        try (FileSystem fs = FileSystems.newFileSystem(raw, (ClassLoader)null)) {
            String format = getBundlerVersion(fs.getPath("META-INF", "MANIFEST.MF"));
            if (format == null) {
                Files.copy(raw, target);
                return target;
            }

            if (!"1.0".equals(format))
                throw new UnsupportedOperationException("Unsupported bundler format " + format + " in " + raw + " only 1.0 is supported");
            FileList versions = FileList.read(fs.getPath("META-INF", "versions.list"));

            FileList.Entry entry = null;
            for (FileList.Entry e : versions.entries) {
                if (e.path.endsWith(".jar")) {
                    entry = e;
                    break;
                }
            }

            if (entry == null)
                throw new IOException("Could not find main jar in versions.list from " + raw);

            extractFile("versions", fs, entry, target);
        }
        return target;
    }

    private static String getBundlerVersion(Path manifest) throws IOException {
        if (!Files.exists(manifest))
            return null;

        Manifest mf = null;
        try (InputStream is = Files.newInputStream(manifest)) {
            mf = new Manifest(is);
        }
        String format = mf.getMainAttributes().getValue(BUNDLER_FORMAT);
        if (format == null)
            return null;

        return format;
    }

    private static void extractFile(String group, FileSystem fs, FileList.Entry entry, Path output) throws IOException {
        if (Files.exists(output)) {
            if (Files.isDirectory(output))
                throw new IOException("Can not bundled jar to directory: " + output);

            String existing = HashFunction.SHA256.hash(output);
            if (existing.equals(entry.hash)) {
                log("File already exists, and hash verified");
                return;
            }

            log("Existing file's hash does not match");
            log("Expected: " + entry.hash);
            log("Actual:   " + existing);
        }

        if (!output.toFile().getParentFile().exists())
            output.toFile().getParentFile().mkdirs();

        Files.copy(fs.getPath("META-INF", group, entry.path), output, StandardCopyOption.REPLACE_EXISTING);

        String extracted = HashFunction.SHA256.hash(output);
        if (!extracted.equals(entry.hash)) {
            throw new IOException(
                "Failed to extract: " + group + '/' + entry.path + " Hash mismatch\n" +
                "Expected: " + entry.hash + '\n' +
                "Actual:   " + extracted
            );
        } else {
            log("Extracted: " + group + '/' + entry.path);
        }
    }

    private static void log(String line) {
        // System.out.println(line);
    }

    static class FileList {
        static FileList read(Path path) throws IOException {
            List<Entry> ret = new ArrayList<>();
            for (String line : Files.readAllLines(path)) {
                String[] pts = line.split("\t");
                if (pts.length != 3)
                    throw new IllegalStateException("Invalid file list line: " + line);
                ret.add(new Entry(pts[0], pts[1], pts[2]));
            }
            return new FileList(ret);

        }

        final List<Entry> entries;

        private FileList(List<Entry> entries) {
            this.entries = entries;
        }

        static class Entry {
            final String hash;
            final String id;
            final String path;
            private Entry(String hash, String id, String path) {
                this.hash = hash;
                this.id = id;
                this.path = path;
            }
        }
    }
}
