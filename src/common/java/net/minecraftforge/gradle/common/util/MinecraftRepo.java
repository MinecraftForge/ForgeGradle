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

package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.util.VersionJson.Download;
import net.minecraftforge.gradle.common.util.VersionJson.OS;

public class MinecraftRepo extends BaseRepo {
    private static MinecraftRepo INSTANCE;
    private static final String GROUP = "net.minecraft";
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/";
    public static final String CURRENT_OS = OS.getCurrent().getName();

    private final Repository repo;
    private MinecraftRepo(File cache, Logger log) {
        super(cache, log);
        this.repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .filter(ArtifactIdentifier.groupEquals(GROUP))
            .filter(ArtifactIdentifier.nameMatches("^(client|server)$"))
            .provide(this)
        );
    }

    private static MinecraftRepo getInstance(Project project) {
        if (INSTANCE == null)
            INSTANCE = new MinecraftRepo(Utils.getCache(project, "minecraft_repo"), project.getLogger());
        return INSTANCE;
    }

    public static void attach(Project project) {
        MinecraftRepo instance = getInstance(project);
        GradleRepositoryAdapter.add(project.getRepositories(), "MINECRAFT_DYNAMIC", instance.getCacheRoot(), instance.repo);
    }

    public static ArtifactProvider<ArtifactIdentifier> create(Project project) {
        return getInstance(project);
    }

    protected String getMappings(String version) {
        if (!version.contains("_mapped_")) {
            return null;
        }
        return version.split("_mapped_")[0];
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String side = artifact.getName();

        if (!artifact.getGroup().equals(GROUP) || (!"client".equals(side) && !"server".equals(side)))
            return null;

        String version = artifact.getVersion();
        String mappings = getMappings(version);
        if (mappings != null) {
            version = version.substring(0, version.length() - mappings.length() + "_mapped_".length());
            return null; //We do not support mappings
        }
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension();

        File json = findVersion(version);
        if (json == null)
            return null; //Not a vanilla version, MCP?

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + side + ":" + version + ":" + classifier + "@" + ext);

        if ("pom".equals(ext)) {
            return findPom(side, version, json);
        } else if ("json".equals(ext)) {
            if ("".equals(classifier))
                return findVersion(version);
        } else {
            switch (classifier) {
                case "":       return findRaw(side, version, json);
                case "slim":   return findSlim(side, version, json);
                case "data":   return findData(side, version, json);
                case "extra":  return findExtra(side, version, json);
            }
        }
        return null;
    }

    private File findMcp(String version) throws IOException {
        net.minecraftforge.gradle.common.util.Artifact mcp = net.minecraftforge.gradle.common.util.Artifact.from("de.oceanlabs.mcp:mcp_config:" + version + "@zip");
        File zip = cache("versions", version, "mcp.zip");
        if (!zip.exists()) {
            FileUtils.copyURLToFile(new URL(FORGE_MAVEN + mcp.getPath()), zip);
            Utils.updateHash(zip);
        }
        return zip;
    }

    private File findMappings(String version) throws IOException {
        File mappings = cache("versions", version, "mappings.txt");
        if (!mappings.exists()) {
            File mcp = findMcp(version);
            MCPWrapperSlim wrapper = new MCPWrapperSlim(mcp);
            wrapper.extractData(mappings, "mappings");
            Utils.updateHash(mappings);
        }
        return mappings;
    }

    private File findVersion(String version) throws IOException {
        File manifest = cache("versions/manifest.json");
        if (!Utils.downloadEtag(new URL(MANIFEST_URL), manifest))
            return null;
        Utils.updateHash(manifest);

        File json = cache("versions", version, "version.json");
        URL url =  Utils.loadJson(manifest, ManifestJson.class).getUrl(version);
        if (url == null)
            throw new RuntimeException("Missing version from manifest: " + version);

        if (!Utils.downloadEtag(url, json))
            return null;
        Utils.updateHash(json);
        return json;
    }

    protected File findPom(String side, String version, File json) throws IOException {
        File pom = cache("versions", version, side + ".pom");
        HashStore cache = new HashStore(this.getCacheRoot()).load(cache("versions", version, side + ".pom.input"));

        if ("client".equals(side)) {
            cache.add(json);
        }

        //log("POM Cache: " + cache.isSame()  + " " + pom.exists());
        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, side, version);
            if ("client".equals(side)) {
                VersionJson meta = Utils.loadJson(json, VersionJson.class);
                for (VersionJson.Library lib : meta.libraries) {
                    //TODO: Filter?
                    builder.dependencies().add(lib.name, "compile");
                    if (lib.downloads.classifiers != null) {
                        if (lib.downloads.classifiers.containsKey("test")) {
                            builder.dependencies().add(lib.name, "test").withClassifier("test");
                        }
                        if (lib.natives != null && lib.natives.containsKey(CURRENT_OS) && !lib.getArtifact().getName().contains("java-objc-bridge")) {
                            builder.dependencies().add(lib.name, "runtime").withClassifier(lib.natives.get(CURRENT_OS));
                        }
                    }
                }
            } else {
                builder.dependencies().add(GROUP + ":" + side + ":" + version, "compile").withClassifier("extra"); //Compile right?
            }
            builder.dependencies().add(GROUP + ":" + side + ":" + version, "compile").withClassifier("data");
            builder.dependencies().add("com.google.code.findbugs:jsr305:3.0.1", "compile"); //TODO: Pull this from MCPConfig.

            String ret = builder.tryBuild();
            if (ret == null) {
                return null;
            }
            FileUtils.writeByteArrayToFile(pom, ret.getBytes());
            cache.save();
            Utils.updateHash(pom);
        }

        return pom;
    }

    private File findRaw(String side, String version, File json_file) throws IOException {
        VersionJson json = Utils.loadJson(json_file, VersionJson.class);
        if (json.downloads == null || !json.downloads.containsKey(side)) {
            throw new IllegalStateException(version +".json missing download for " + side);
        }
        Download dl = json.downloads.get(side);
        File raw = cache("versions", version, side + ".jar");
        if (!raw.exists() || !HashFunction.SHA1.hash(raw).equals(dl.sha1)) {
            FileUtils.copyURLToFile(dl.url, raw);
            Utils.updateHash(raw);
        }
        return raw;
    }

    private File findExtra(String side, String version, File json) throws IOException {
        File raw = findRaw(side, version, json);
        File mappings = findMappings(version);
        File extra = cache("versions", version, side + "-extra.jar");
        HashStore cache = new HashStore(this.getCacheRoot()).load(cache("versions", version, side + "-extra.input"))
                .add("raw", raw)
                .add("mappings", mappings);

        if (!cache.isSame() || !extra.exists()) {
            splitJar(raw, mappings, extra, false);
            cache.save();
        }

        return extra;
    }
    private File findSlim(String side, String version, File json) throws IOException {
        File raw = findRaw(side, version, json);
        File mappings = findMappings(version);
        File extra = cache("versions", version, side + "-slim.jar");
        HashStore cache = new HashStore(this.getCacheRoot()).load(cache("versions", version, side + "-slim.input"))
                .add("raw", raw)
                .add("mappings", mappings);

        if (!cache.isSame() || !extra.exists()) {
            splitJar(raw, mappings, extra, true);
            cache.save();
        }


        return extra;
    }

    private void splitJar(File raw, File mappings, File output, boolean notch) throws IOException {
        try (ZipFile zin = new ZipFile(raw);
             FileOutputStream fos = new FileOutputStream(output);
             ZipOutputStream out = new ZipOutputStream(fos)) {

            Set<String> whitelist = new HashSet<>();
            List<String> lines = Files.lines(Paths.get(mappings.getAbsolutePath())).map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList()); //Strip comments and empty lines
            lines.stream()
            .filter(line -> !line.startsWith("\t") || (line.indexOf(':') != -1 && line.startsWith("CL:"))) // Class lines only
            .map(line -> line.indexOf(':') != -1 ? line.substring(4).split(" ") : line.split(" ")) //Convert to: OBF SRG
            .filter(pts -> pts.length == 2 && !pts[0].endsWith("/")) //Skip packages
            .forEach(pts -> whitelist.add(pts[0]));

            for (Enumeration<? extends ZipEntry> entries = zin.entries(); entries.hasMoreElements();) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    boolean isNotch = whitelist.contains(name.substring(0, name.length() - 6 /*.class*/));
                    if (notch == isNotch) {
                        ZipEntry _new = new ZipEntry(name);
                        _new.setTime(0);
                        out.putNextEntry(_new);
                        try (InputStream ein = zin.getInputStream(entry)) {
                            IOUtils.copy(ein, out);
                        }
                        out.closeEntry();
                    }
                }
            }
        }
        Utils.updateHash(output);
    }

    private File findData(String side, String version, File json) throws IOException {
        File raw = findRaw(side, version, json);
        File extra = cache("versions", version, side + "-data.jar");
        HashStore cache = new HashStore(this.getCacheRoot()).load(cache("versions", version, side + "-extra.input"))
                .add("raw", raw);

        if (!cache.isSame() || !extra.exists()) {
            try (ZipFile zin = new ZipFile(raw);
                 FileOutputStream fos = new FileOutputStream(extra);
                 ZipOutputStream out = new ZipOutputStream(fos)) {

                for (Enumeration<? extends ZipEntry> entries = zin.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        ZipEntry _new = new ZipEntry(name);
                        _new.setTime(0);
                        out.putNextEntry(_new);
                        try (InputStream ein = zin.getInputStream(entry)) {
                            IOUtils.copy(ein, out);
                        }
                        out.closeEntry();
                    }
                }
            }

            cache.save();
            Utils.updateHash(extra);
        }
        return extra;
    }


    private static class MCPWrapperSlim {
        private final File data;
        private final MCPConfigV1 config;
        public MCPWrapperSlim(File data) throws IOException {
            this.data = data;
            byte[] cfg_data = Utils.getZipData(data, "config.json");
            int spec = Config.getSpec(cfg_data);
            if (spec != 1)
                throw new IllegalStateException("Could not load MCP config, Unknown Spec: " + spec + " File: " + data);
            this.config = MCPConfigV1.get(cfg_data);
        }

        public void extractData(File target, String... path) throws IOException {
            String name = config.getData(path);
            if (name == null)
                throw new IOException("Unknown MCP Entry: " + Joiner.on("/").join(path));

            try (ZipFile zip = new ZipFile(data)) {
                Utils.extractFile(zip, name, target);
            }
        }
    }
}
