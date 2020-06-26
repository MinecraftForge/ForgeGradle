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

package net.minecraftforge.gradle.mcp;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.common.collect.Maps;

import de.siegmar.fastcsv.writer.CsvWriter;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.ManifestJson;
import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.MappingFile.Cls;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipOutputStream;

/**
 * Provides the following artifacts:
 *
 * net.minecraft:
 *   client:
 *     MCPVersion:
 *       srg - Srg named SLIM jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *   server:
 *     MCPVersion:
 *       srg - Srg named SLIM jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *   joined:
 *     MCPVersion:
 *       .pom - Pom meta linking against net.minecraft:client:extra and net.minecraft:client:data
 *       '' - Notch named merged jar file
 *       srg - Srg named jar file.
 *       srg-sources - Srg named decompiled/patched code.
 *   mappings_{channel}:
 *     MCPVersion|MCVersion:
 *       .zip - A zip file containing SRG -> Human readable field and method mappings.
 *         Current supported channels:
 *         'stable', 'snapshot': MCP's crowdsourced mappings.
 *         'official': Official mappings released by Mojang.
 *
 *   Note: It does NOT provide the Obfed named jars for server and client, as that is provided by MinecraftRepo.
 */
public class MCPRepo extends BaseRepo {
    private static MCPRepo INSTANCE = null;
    private static final String GROUP_MINECRAFT = "net.minecraft";
    private static final String NAMES_MINECRAFT = "^(client|server|joined|mappings_[a-z_]+)$";
    private static final String GROUP_MCP = "de.oceanlabs.mcp";
    private static final String NAMES_MCP = "^(mcp_config)$";
    private static final String STEP_MERGE = "merge"; //TODO: Design better way to get steps output, for now hardcode
    private static final String STEP_RENAME = "rename";

    //This is the artifact we expose that is a zip containing SRG->Official fields and methods.
    public static final String MAPPING_DEP = "net.minecraft:mappings_{CHANNEL}:{VERSION}@zip";
    public static String getMappingDep(String channel, String version) {
        return MAPPING_DEP.replace("{CHANNEL}", channel).replace("{VERSION}", version);
    }

    private final Project project;
    private final Repository repo;
    private final Map<String, MCPWrapper> wrappers = Maps.newHashMap();
    private final Map<String, McpNames> mapCache = new HashMap<>();

    private MCPRepo(Project project, File cache, Logger log) {
        super(cache, log);
        this.project = project;
        this.repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .provide(this)
        );
    }

    private static MCPRepo getInstance(Project project) {
        if (INSTANCE == null)
            INSTANCE = new MCPRepo(project, Utils.getCache(project, "mcp_repo"), project.getLogger());
        return INSTANCE;
    }
    public static void attach(Project project) {
        MCPRepo instance = getInstance(project);
        GradleRepositoryAdapter.add(project.getRepositories(), "MCP_DYNAMIC", instance.getCacheRoot(), instance.repo);
    }

    public static ArtifactProvider<ArtifactIdentifier> create(Project project) {
        return getInstance(project);
    }

    private File cacheMC(String side, String version, String classifier, String ext) {
        if (classifier != null)
            return cache("net", "minecraft", side, version, side + '-' + version + '-' + classifier + '.' + ext);
        return cache("net", "minecraft", side, version, side + '-' + version + '.' + ext);
    }

    private File cacheMCP(String version, String classifier, String ext) {
        if (classifier != null)
            return cache("de", "oceanlabs", "mcp", "mcp_config", version, "mcp_config-" + version + '-' + classifier + '.' + ext);
        return cache("de", "oceanlabs", "mcp", "mcp_config", version, "mcp_config-" + version + '.' + ext);
    }
    private File cacheMCP(String version) {
        return cache("de", "oceanlabs", "mcp", "mcp_config", version);
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String name = artifact.getName();
        String group = artifact.getGroup();

        if (group.equals(GROUP_MCP)) {
            if (!name.matches(NAMES_MCP))
                return null;
        } else if (group.equals(GROUP_MINECRAFT)) {
            if (!name.matches(NAMES_MINECRAFT))
                return null;
        } else
            return null;

        String version = artifact.getVersion();
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension();

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + name + ":" + version + ":" + classifier + "@" + ext);

        if (group.equals(GROUP_MINECRAFT)) {
            if ("pom".equals(ext)) {
                return findPom(name, version);
            } else if (name.startsWith("mappings_")) {
                if ("zip".equals(ext)) {
                    return findNames(name.substring(9) + '_' + version);
                }
            } else {
                switch (classifier) {
                    case "":              return findRaw(name, version);
                    case "srg":           return findSrg(name, version);
                    case "extra":         return findExtra(name, version);
                }
            }
        } else if (group.equals(GROUP_MCP)) {
            /* Gradle fucks up caching for anything that isnt a zip or a jar, this is fucking annoying we can't do this.
            MappingFile.Format format = MappingFile.Format.get(ext);
            if (format != null) {
                classifier = classifier.replace('!', '.'); //We hack around finding the extension by using a invalid path character
                switch (classifier) {
                    case "obf-to-srg": return findRenames(classifier, format, version, false);
                    case "srg-to-obf": return findRenames(classifier, format, version, true);
                }
                if (classifier.startsWith("obf-to-")) return findRenames(classifier, format, version, classifier.substring(7), true, false);
                if (classifier.startsWith("srg-to-")) return findRenames(classifier, format, version, classifier.substring(7), false,  false);
                if (classifier.endsWith  ("-to-obf")) return findRenames(classifier, format, version, classifier.substring(0, classifier.length() - 7), true, true);
                if (classifier.endsWith  ("-to-srg")) return findRenames(classifier, format, version, classifier.substring(0, classifier.length() - 7), false, true);
            }
            */
        }
        return null;
    }

    private HashStore commonHash(File mcp) {
        return new HashStore(this.getCacheRoot())
            .add("mcp", mcp);
    }

    private File getMCP(String version) throws IOException {
        return MavenArtifactDownloader.manual(project, "de.oceanlabs.mcp:mcp_config:" + version + "@zip", false);
    }

    private File findVersion(String version) throws IOException {
        File manifest = cache("versions", "manifest.json");
        if (!Utils.downloadEtag(new URL(MinecraftRepo.MANIFEST_URL), manifest, project.getGradle().getStartParameter().isOffline()))
            return null;
        Utils.updateHash(manifest);
        File json = cache("versions", version, "version.json");

        URL url =  Utils.loadJson(manifest, ManifestJson.class).getUrl(version);
        if (url == null)
            throw new RuntimeException("Missing version from manifest: " + version);

        if (!Utils.downloadEtag(url, json, project.getGradle().getStartParameter().isOffline()))
            return null;
        Utils.updateHash(json);
        return json;
    }

    private File findPom(String side, String version) throws IOException {
        File mcp = getMCP(version);
        if (mcp == null)
            return null;

        File pom = cacheMC(side, version, null, "pom");
        debug("    Finding pom: " + pom);
        HashStore cache = commonHash(mcp).load(cacheMC(side, version, null, "pom.input"));
        File json = null;
        if (!"server".equals(side)) {
            json = findVersion(MinecraftRepo.getMCVersion(version));
            if (json == null) {
                project.getLogger().lifecycle("Could not make Minecraft POM. Missing version json");
                return null;
            }
            cache.add("json", json);
        }

        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP_MINECRAFT, side, version);
            if (!"server".equals(side)) {
                VersionJson meta = Utils.loadJson(json, VersionJson.class);
                for (VersionJson.Library lib : meta.libraries) {
                    if (lib.isAllowed()) {
                        builder.dependencies().add(lib.name, "compile");
                        if (lib.downloads.classifiers != null) {
                            if (lib.downloads.classifiers.containsKey("test")) {
                                builder.dependencies().add(lib.name, "test").withClassifier("test");
                            }
                            if (lib.natives != null && lib.natives.containsKey(MinecraftRepo.CURRENT_OS) && !lib.getArtifact().getName().contains("java-objc-bridge")) {
                                builder.dependencies().add(lib.name, "runtime").withClassifier(lib.natives.get(MinecraftRepo.CURRENT_OS));
                            }
                        }
                    }
                }
                builder.dependencies().add("net.minecraft:client:" + version, "compile").withClassifier("extra");
                //builder.dependencies().add("net.minecraft:client:" + getMCVersion(version), "compile").withClassifier("data");
            } else {
                builder.dependencies().add("net.minecraft:server:" + version, "compile").withClassifier("extra");
                //builder.dependencies().add("net.minecraft:server:" + getMCVersion(version), "compile").withClassifier("data");
            }

            MCPWrapper wrapper = getWrapper(version, mcp);
            wrapper.getConfig().getLibraries(side).forEach(e -> builder.dependencies().add(e, "compile"));

            String ret = builder.tryBuild();
            if (ret == null)
                return null;
            FileUtils.writeByteArrayToFile(pom, ret.getBytes());
            cache.save();
            Utils.updateHash(pom, HashFunction.SHA1);
        }

        return pom;
    }

    private File findRaw(String side, String version) throws IOException {
        if (!"joined".equals(side))
            return null; //MinecraftRepo provides these

        return findStepOutput(side, version, null, "jar", STEP_MERGE);
    }

    private File findSrg(String side, String version) throws IOException {
        return findStepOutput(side, version, "srg", "jar", STEP_RENAME);
    }

    private File findStepOutput(String side, String version, String classifier, String ext, String step) throws IOException {
        File mcp = getMCP(version);
        if (mcp == null)
            return null;
        File raw = cacheMC(side, version, classifier, ext);
        debug("  Finding " + step + ": " + raw);
        HashStore cache = commonHash(mcp).load(cacheMC(side, version, classifier, ext + ".input"));

        if (!cache.isSame() || !raw.exists()) {
            MCPWrapper wrapper = getWrapper(version, mcp);
            MCPRuntime runtime = wrapper.getRuntime(project, side);
            try {
                File output = runtime.execute(log, step);
                FileUtils.copyFile(output, raw);
                cache.save();
                Utils.updateHash(raw, HashFunction.SHA1);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                log.lifecycle(e.getMessage());
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new RuntimeException(e);
            }
        }
        return raw;
    }

    private synchronized MCPWrapper getWrapper(String version, File data) throws IOException {
        String hash = HashFunction.SHA1.hash(data);
        MCPWrapper ret = wrappers.get(version);
        if (ret == null  || !hash.equals(ret.getHash())) {
            ret = new MCPWrapper(hash, data, cacheMCP(version));
            wrappers.put(version, ret);
        }
        return ret;
    }

    private File findRenames(String classifier, MappingFile.Format format, String version, boolean toObf) throws IOException {
        String ext = format.name().toLowerCase();
        //File names = findNames(version));
        File mcp = getMCP(version);
        if (mcp == null)
            return null;

        File file = cacheMCP(version, classifier, ext);
        debug("    Finding Renames: " + file);
        HashStore cache = commonHash(mcp).load(cacheMCP(version, classifier, ext + ".input"));

        if (!cache.isSame() || !file.exists()) {
            MCPWrapper wrapper = getWrapper(version, mcp);
            byte[] data = wrapper.getData("mappings");
            MappingFile obf_to_srg = MappingFile.load(new ByteArrayInputStream(data));
            obf_to_srg.write(format, file, toObf);
            cache.save();
            Utils.updateHash(file, HashFunction.SHA1);
        }

        return file;
    }

    private File findNames(String mapping) throws IOException {
        int idx = mapping.lastIndexOf('_');
        if (idx == -1) return null; //Invalid format
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);

        if ("official".equals(channel)) {
            return findOfficialMapping(version);
        } else if ("snapshot".equals(channel) || "snapshot_nodoc".equals(channel) || "stable".equals(channel) || "stable_nodoc".equals(channel)) { //MCP
            String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
            debug("    Mapping: " + desc);
            return MavenArtifactDownloader.manual(project, desc, false);
        }
        //TODO? Yarn/Other crowdsourcing?
        throw new IllegalArgumentException("Unknown mapping provider: " + mapping);
    }

    private McpNames loadMCPNames(String name, File data) throws IOException {
        McpNames map = mapCache.get(name);
        String hash = HashFunction.SHA1.hash(data);
        if (map == null || !hash.equals(map.hash)) {
            map = McpNames.load(data);
            mapCache.put(name, map);
        }
        return map;
    }

    @SuppressWarnings("unused")
    private File findRenames(String classifier, MappingFile.Format format, String version, String mapping, boolean obf, boolean reverse) throws IOException {
        String ext = format.name().toLowerCase();
        File names = findNames(version);
        File mcp = getMCP(version);
        if (mcp == null || names == null)
            return null;

        File file = cacheMCP(version, classifier, ext);
        debug("    Finding Renames: " + file);
        HashStore cache = commonHash(mcp).load(cacheMCP(version, classifier, ext + ".input"));

        if (!cache.isSame() || !file.exists()) {
            MCPWrapper wrapper = getWrapper(version, mcp);
            byte[] data = wrapper.getData("mappings");
            MappingFile obf_to_srg = MappingFile.load(new ByteArrayInputStream(data));
            McpNames mcp_names = loadMCPNames(mapping, names);
            MappingFile ret = new MappingFile();
            if (obf) {
                obf_to_srg.getPackages().forEach(e -> ret.addPackage(e.getOriginal(), e.getMapped()));
                obf_to_srg.getClasses().forEach(cls -> {
                   ret.addClass(cls.getOriginal(), cls.getMapped());
                   MappingFile.Cls _cls = ret.getClass(cls.getOriginal());
                   cls.getFields().forEach(fld -> _cls.addField(fld.getOriginal(), mcp_names.rename(fld.getMapped())));
                   cls.getMethods().forEach(mtd -> _cls.addMethod(mtd.getOriginal(), mtd.getDescriptor(), mcp_names.rename(mtd.getMapped())));
                });
            } else {
                obf_to_srg.getPackages().forEach(e -> ret.addPackage(e.getMapped(), e.getMapped()));
                obf_to_srg.getClasses().forEach(cls -> {
                   ret.addClass(cls.getMapped(), cls.getMapped());
                   MappingFile.Cls _cls = ret.getClass(cls.getMapped());
                   cls.getFields().forEach(fld -> _cls.addField(fld.getMapped(), mcp_names.rename(fld.getMapped())));
                   cls.getMethods().forEach(mtd -> _cls.addMethod(mtd.getMapped(), mtd.getMappedDescriptor(), mcp_names.rename(mtd.getMapped())));
                });
            }

            ret.write(format, file, reverse);
            cache.save();
            Utils.updateHash(file, HashFunction.SHA1);
        }

        return file;
    }

    private File findExtra(String side, String version) throws IOException {
        File raw = findRaw(side, version);
        File mcp = getMCP(version);
        if (raw == null || mcp == null)
            return null;

        File extra = cacheMC(side, version, "extra", "jar");
        HashStore cache = commonHash(mcp).load(cacheMC(side, version, "extra", "jar.input"))
                .add("raw", raw)
                .add("mcp", mcp)
                .add("codever", "1");

        if (!cache.isSame() || !extra.exists()) {
            MCPWrapper wrapper = getWrapper(version, mcp);
            byte[] data = wrapper.getData("mappings");
            MinecraftRepo.splitJar(raw, new ByteArrayInputStream(data), extra, false, true);
            cache.save();
        }

        return extra;
    }

    private File findOfficialMapping(String version) throws IOException {
        String mcpversion = version;
        int idx = version.lastIndexOf('-');
        if (idx != -1 && version.substring(idx + 1).matches("\\d{8}\\.\\d{6}")) { //Timestamp, so lets assume that's the MCP part.
            //mcpversion = version.substring(idx);
            version = version.substring(0, idx);
        }
        File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + version + ":mappings@txt", true);
        File server = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + version + ":mappings@txt", true);
        if (client == null || server == null)
            throw new IllegalStateException("Could not create " + mcpversion + " official mappings due to missing ProGuard mappings.");

        File tsrg = findRenames("obf_to_srg", MappingFile.Format.TSRG, mcpversion, false);
        if (tsrg == null)
            throw new IllegalStateException("Could not create " + mcpversion + " official mappings due to missing MCP's tsrg");

        File mcp = getMCP(mcpversion);
        if (mcp == null)
            return null;

        File mappings = cacheMC("mapping", mcpversion, "mapping", "zip");
        HashStore cache = commonHash(mcp)
                .load( cacheMC("mapping", mcpversion, "mapping", "zip.input"))
                .add("pg_client", client)
                .add("pg_server", server)
                .add("tsrg", tsrg)
                .add("codever", "1");

        if (!cache.isSame() || !mappings.exists()) {
            MappingFile pg_client = MappingFile.load(client);
            MappingFile pg_server = MappingFile.load(server);

            //Verify that the PG files merge, merge in MCPConfig, but doesn't hurt to double check here.
            //And if we don't we need to write a handler to spit out correctly sided info.

            MappingFile srg = MappingFile.load(tsrg);

            Map<String, String> cfields = new TreeMap<>();
            Map<String, String> sfields = new TreeMap<>();
            Map<String, String> cmethods = new TreeMap<>();
            Map<String, String> smethods = new TreeMap<>();

            for (Cls cls : pg_client.getClasses()) {
                Cls obf = srg.getClass(cls.getMapped());
                for (Cls.Field fld : cls.getFields()) {
                    String name = obf.remap(fld.getMapped());
                    if (name.startsWith("field_"))
                        cfields.put(name, fld.getOriginal());
                }
                for (Cls.Method mtd : cls.getMethods()) {
                    String name = obf.remap(mtd.getMapped(), mtd.getMappedDescriptor());
                    if (name.startsWith("func_"))
                        cmethods.put(name, mtd.getOriginal());
                }
            }
            for (Cls cls : pg_server.getClasses()) {
                Cls obf = srg.getClass(cls.getMapped());
                for (Cls.Field fld : cls.getFields()) {
                    String name = obf.remap(fld.getMapped());
                    if (name.startsWith("field_"))
                        sfields.put(name, fld.getOriginal());
                }
                for (Cls.Method mtd : cls.getMethods()) {
                    String name = obf.remap(mtd.getMapped(), mtd.getMappedDescriptor());
                    if (name.startsWith("func_"))
                        smethods.put(name, mtd.getOriginal());
                }
            }

            String[] header = new String[] {"searge", "name", "side", "desc"};
            List<String[]> fields = new ArrayList<>();
            List<String[]> methods = new ArrayList<>();
            fields.add(header);
            methods.add(header);

            for (String name : cfields.keySet()) {
                String cname = cfields.get(name);
                String sname = sfields.get(name);
                if (cname.equals(sname)) {
                    fields.add(new String[]{name, cname, "2", ""});
                    sfields.remove(name);
                } else
                    fields.add(new String[]{name, cname, "0", ""});
            }

            for (String name : cmethods.keySet()) {
                String cname = cmethods.get(name);
                String sname = smethods.get(name);
                if (cname.equals(sname)) {
                    methods.add(new String[]{name, cname, "2", ""});
                    smethods.remove(name);
                } else
                    methods.add(new String[]{name, cname, "0", ""});
            }

            sfields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
            smethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));

            CsvWriter csv = new CsvWriter();

            if (!mappings.getParentFile().exists())
                mappings.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(mappings);
                 ZipOutputStream out = new ZipOutputStream(fos)) {

                out.putNextEntry(Utils.getStableEntry("fields.csv"));
                csv.write(new OutputStreamWriter(out), fields);
                out.closeEntry();

                out.putNextEntry(Utils.getStableEntry("methods.csv"));
                csv.write(new OutputStreamWriter(out), methods);
                out.closeEntry();
            }

            cache.save();
            Utils.updateHash(mappings, HashFunction.SHA1);
        }


        return mappings;
    }
}
