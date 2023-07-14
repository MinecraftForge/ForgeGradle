/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp.function;

import net.minecraftforge.gradle.mcp.util.MCPEnvironment;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.tools.ant.types.FileList;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.OutputStreamWriter;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class ListLibrariesFunction implements MCPFunction {
    private static final Attributes.Name FORMAT = new Attributes.Name("Bundler-Format");
    private final int spec;

    ListLibrariesFunction(int spec) {
        this.spec = spec;
    }

    @Override
    public File execute(MCPEnvironment environment) {
        File output = (File)environment.getArguments().computeIfAbsent("output", (key) -> environment.getFile("libraries.txt"));
        File bundle = (File) environment.getArguments().get("bundle");
        if (bundle != null && this.spec < 3) {
            throw new IllegalArgumentException("Invalid MCP Config: Listing bundle libraries is only supported for MCPConfig spec 3 or higher, found spec: " + this.spec);
        }

        try (FileSystem bundleFs = bundle == null ? null : FileSystems.newFileSystem(bundle.toPath(), (ClassLoader)null)) {
            Set<String> artifacts;
            if (bundleFs == null) {
                artifacts = listDownloadJsonLibraries(environment, output);
            } else {
                artifacts = listBundleLibraries(environment, bundleFs, output);
            }

            Set<File> libraries = new HashSet<>();
            for (String artifact : artifacts) {
                File lib = MavenArtifactDownloader.gradle(environment.project, artifact, false);
                if (lib == null)
                    throw new RuntimeException("Could not resolve download: " + artifact);

                libraries.add(lib);
            }

            // Write the list
            if (output.exists()) output.delete();
            output.getParentFile().mkdirs();
            output.createNewFile();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), StandardCharsets.UTF_8));
            for (File file : libraries) {
                writer.println("-e=" + file.getAbsolutePath());
            }
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return output;
    }

    private Set<String> listBundleLibraries(MCPEnvironment environment, FileSystem bundleFs, File output) throws IOException {
        Path mfp = bundleFs.getPath("META-INF", "MANIFEST.MF");
        if (!Files.exists(mfp))
            throw new RuntimeException("Input archive does not contain META-INF/MANIFEST.MF");

        Manifest mf;
        try (InputStream is = Files.newInputStream(mfp)) {
            mf = new Manifest(is);
        }
        String format = mf.getMainAttributes().getValue(FORMAT);
        if (format == null)
            throw new RuntimeException("Invalid bundler archive; missing format entry from manifest");

        if (!"1.0".equals(format))
            throw new RuntimeException("Unsupported bundler format " + format + "; only 1.0 is supported");

        FileList libraries = FileList.read(bundleFs.getPath("META-INF", "libraries.list"));
        Set<String> artifacts = new HashSet<>();
        for (FileList.Entry entry : libraries.entries) {
            artifacts.add(entry.id);
        }

        return artifacts;
    }

    private Set<String> listDownloadJsonLibraries(MCPEnvironment environment, File output) throws IOException {
        Gson gson = new Gson();
        Reader reader = new FileReader(environment.getStepOutput("downloadJson"));
        JsonObject json = gson.fromJson(reader, JsonObject.class);
        reader.close();

        // Gather all the libraries
        Set<String> artifacts = new HashSet<>();
        for (JsonElement libElement : json.getAsJsonArray("libraries")) {
            JsonObject library = libElement.getAsJsonObject();
            String name = library.get("name").getAsString();

            if (library.has("downloads")) {
                JsonObject downloads = library.get("downloads").getAsJsonObject();
                if (downloads.has("artifact"))
                    artifacts.add(name);
                if (downloads.has("classifiers"))
                    downloads.get("classifiers").getAsJsonObject().keySet().forEach(cls -> artifacts.add(name + ':' + cls));
            }
        }

        return artifacts;
    }

    private static class FileList {
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
        private final List<Entry> entries;

        private FileList(List<Entry> entries) {
            this.entries = entries;
        }

        private static class Entry {
            private final String hash;
            @SuppressWarnings("unused")
            private final String id;
            private final String path;
            Entry(String hash, String id, String path) {
                this.hash = hash;
                this.id = id;
                this.path = path;
            }
        }
    }
}
