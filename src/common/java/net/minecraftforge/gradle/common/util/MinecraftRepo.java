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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
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
import net.minecraftforge.srgutils.MinecraftVersion;

public class MinecraftRepo extends BaseRepo {
    private static MinecraftRepo INSTANCE;
    private static final String GROUP = "net.minecraft";
    public static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String CURRENT_OS = OS.getCurrent().getName();
    private static int CACHE_BUSTER = 1;
    private static final MinecraftVersion v1_14_4 = MinecraftVersion.from("1.14.4");
    private static final Pattern MCP_CONFIG_VERSION = Pattern.compile("\\d{8}\\.\\d{6}"); //Timestamp: YYYYMMDD.HHMMSS

    private final Repository repo;
    private final boolean offline;
    private MinecraftRepo(File cache, Logger log, boolean offline) {
        super(cache, log);
        this.repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .filter(ArtifactIdentifier.groupEquals(GROUP))
            .filter(ArtifactIdentifier.nameMatches("^(client|server)$"))
            .provide(this)
        );
        this.offline = offline;
    }

    private static MinecraftRepo getInstance(Project project) {
        if (INSTANCE == null)
            INSTANCE = new MinecraftRepo(Utils.getCache(project, "minecraft_repo"), project.getLogger(), project.getGradle().getStartParameter().isOffline());
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

    private HashStore commonCache(File file) throws IOException {
        HashStore ret = new HashStore(this.getCacheRoot()).load(new File(file.getAbsolutePath() + ".input"));
        ret.bust(CACHE_BUSTER);
        return ret;
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String side = artifact.getName();

        if (!artifact.getGroup().equals(GROUP) || (!"client".equals(side) && !"server".equals(side)))
            return null;

        String version = artifact.getVersion();
        boolean forceStable = version.endsWith("-stable");
        if (forceStable)
            version = version.substring(0, version.length() - 7);
        String mappings = getMappings(version);
        if (mappings != null)
            return null; //We do not support mappings
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension();

        File json = findVersion(getMCVersion(version));
        if (json == null)
            return null; //Not a vanilla version, MCP?

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + side + ":" + version + ":" + classifier + "@" + ext);

        /* MCP Repo will take care and add the extra libraries.
        if ("pom".equals(ext)) {
            return findPom(side, version, json);
        } else
         */
        if ("json".equals(ext)) {
            if ("".equals(classifier))
                return findVersion(getMCVersion(version));
        } else {
            switch (classifier) {
                case "":         return findRaw(side, version, json);
                case "slim":     return findSlim(side, version, forceStable, json); //Deprecated - Use MCP Specific version
                case "data":     return findData(side, version, forceStable, json); //Deprecated - Use extra
                case "extra":    return findExtra(side, version, forceStable, json); //Deprecated - Use MCP Specific version
                case "mappings": return findMappings(side, version, json);
            }
        }
        return null;
    }

    private File findMcp(String version) throws IOException {
        net.minecraftforge.gradle.common.util.Artifact mcp = net.minecraftforge.gradle.common.util.Artifact.from("de.oceanlabs.mcp:mcp_config:" + version + "@zip");
        File zip = cache("versions", version, "mcp.zip");
        if (!zip.exists()) {
            FileUtils.copyURLToFile(new URL(Utils.FORGE_MAVEN + mcp.getPath()), zip);
            Utils.updateHash(zip);
        }
        return zip;
    }

    private File findMcpMappings(String version) throws IOException {
        File mcp = findMcp(version);
        if (mcp == null)
            return null;

        File mappings = cache("versions", version, "mcp_mappings.tsrg");
        HashStore cache = commonCache(cache("versions", version, "mcp_mappings.tsrg"));
        cache.add(mcp);

        if (!cache.isSame() || !mappings.exists()) {
            MCPWrapperSlim wrapper = new MCPWrapperSlim(mcp);
            wrapper.extractData(mappings, "mappings");
            cache.save();
            Utils.updateHash(mappings);
        }

        return mappings;
    }

    public static String getMCVersion(String version) {
        int idx = version.lastIndexOf('-');
        if (idx != -1 && MCP_CONFIG_VERSION.matcher(version.substring(idx + 1)).matches()) {
            return version.substring(0, idx);
        }
        return version;
    }

    private File findVersion(String version) throws IOException {
        File manifest = cache("versions/manifest.json");
        if (!Utils.downloadEtag(new URL(MANIFEST_URL), manifest, offline))
            return null;
        Utils.updateHash(manifest, HashFunction.SHA1);

        File json = cache("versions", version, "version.json");
        URL url =  Utils.loadJson(manifest, ManifestJson.class).getUrl(version);
        if (url == null)
            throw new RuntimeException("Missing version from manifest: " + version);

        if (!Utils.downloadEtag(url, json, offline))
            return null;
        Utils.updateHash(json, HashFunction.SHA1);
        return json;
    }

    protected File findPom(String side, String version, File json) throws IOException {
        File pom = cache("versions", version, side + ".pom");
        HashStore cache = commonCache(cache("versions", version, side + ".pom"));

        if ("client".equals(side)) {
            cache.add(json);
        }

        //log("POM Cache: " + cache.isSame()  + " " + pom.exists());
        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, side, version);
            if ("client".equals(side)) {
                VersionJson meta = Utils.loadJson(json, VersionJson.class);
                for (VersionJson.Library lib : meta.libraries) {
                    if (lib.isAllowed()) {
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
                }
            }
            builder.dependencies().add(GROUP + ":" + side + ":" + version, "compile").withClassifier("extra");
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

    private File findMappings(String side, String version, File json_file) throws IOException {
        return findDownloadEntry(side + "_mappings", cache("versions", getMCVersion(version), side + "_mappings.txt"), getMCVersion(version), json_file);
    }

    private File findRaw(String side, String version, File json_file) throws IOException {
        return findDownloadEntry(side, cache("versions", getMCVersion(version), side + ".jar"), getMCVersion(version), json_file);
    }

    private File findDownloadEntry(String key, File target, String version, File json_file) throws IOException {
        VersionJson json = Utils.loadJson(json_file, VersionJson.class);
        if (json.downloads == null || !json.downloads.containsKey(key))
            throw new IllegalStateException(version + ".json missing download for " + key);

        Download dl = json.downloads.get(key);
        if (!target.exists() || !HashFunction.SHA1.hash(target).equals(dl.sha1)) {
            FileUtils.copyURLToFile(dl.url, target);
            Utils.updateHash(target, HashFunction.SHA1);
        }
        return target;
    }

    private File findExtra(String side, String version, boolean forceStable, File json) throws IOException {
        boolean stable = v1_14_4.compareTo(MinecraftVersion.from(getMCVersion(version))) < 0;
        File raw = findRaw(side, version, json);
        File mappings = findMcpMappings(version);
        File extra = cache("versions", version, side + "-extra" + (forceStable && !stable ? "-stable" : "") + ".jar");
        HashStore cache = commonCache(cache("versions", version, side + "-extra" + (forceStable && !stable ? "-stable" : "") + ".jar"))
                .add("raw", raw)
                .add("mappings", mappings)
                .add("codever", "1");

        if (!cache.isSame() || !extra.exists()) {
            splitJar(raw, mappings, extra, false, stable || forceStable);
            cache.save();
        }

        return extra;
    }

    private File findSlim(String side, String version, boolean forceStable, File json) throws IOException {
        boolean stable = v1_14_4.compareTo(MinecraftVersion.from(getMCVersion(version))) < 0;
        File raw = findRaw(side, version, json);
        File mappings = findMcpMappings(version);
        File extra = cache("versions", version, side + "-slim" + (forceStable && !stable ? "-stable" : "") + ".jar");
        HashStore cache = commonCache(cache("versions", version, side + "-slim" + (forceStable && !stable ? "-stable" : "") + ".jar"))
                .add("raw", raw)
                .add("mappings", mappings)
                .add("codever", "1");

        if (!cache.isSame() || !extra.exists()) {
            splitJar(raw, mappings, extra, true, stable || forceStable);
            cache.save();
        }


        return extra;
    }

    private static void splitJar(File raw, File mappings, File output, boolean slim, boolean stable) throws IOException {
        try (FileInputStream input = new FileInputStream(mappings)) {
            splitJar(raw, input, output, slim, stable);
        }
    }

    public static void splitJar(File raw, InputStream mappings, File output, boolean slim, boolean stable) throws IOException {
        try (ZipFile zin = new ZipFile(raw);
             FileOutputStream fos = new FileOutputStream(output);
             ZipOutputStream out = new ZipOutputStream(fos)) {

            Set<String> whitelist = new HashSet<>();
            List<String> lines = Utils.lines(mappings).map(line -> line.split("#")[0]).filter(l -> !Strings.isNullOrEmpty(l.trim())).collect(Collectors.toList()); //Strip comments and empty lines
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
                    if (slim == isNotch) {
                        ZipEntry _new = Utils.getStableEntry(name, stable ? Utils.ZIPTIME : 0);
                        out.putNextEntry(_new);
                        try (InputStream ein = zin.getInputStream(entry)) {
                            IOUtils.copy(ein, out);
                        }
                        out.closeEntry();
                    }
                } else {
                    if (!slim) {
                        ZipEntry _new = Utils.getStableEntry(name, stable ? Utils.ZIPTIME : 0);
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

    private File findData(String side, String version, boolean forceStable, File json) throws IOException {
        boolean stable = v1_14_4.compareTo(MinecraftVersion.from(getMCVersion(version))) < 0 || forceStable;
        File raw = findRaw(side, version, json);
        File extra = cache("versions", version, side + "-data" + (forceStable && !stable ? "-stable" : "") + ".jar");
        HashStore cache = commonCache(cache("versions", version, side + "-data" + (forceStable && !stable ? "-stable" : "") + ".jar"))
                .add("raw", raw)
                .add("codever", "1");

        if (!cache.isSame() || !extra.exists()) {
            try (ZipFile zin = new ZipFile(raw);
                 FileOutputStream fos = new FileOutputStream(extra);
                 ZipOutputStream out = new ZipOutputStream(fos)) {

                for (Enumeration<? extends ZipEntry> entries = zin.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!name.endsWith(".class")) {
                        ZipEntry _new = Utils.getStableEntry(name, stable || forceStable ? Utils.ZIPTIME : 0);
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
