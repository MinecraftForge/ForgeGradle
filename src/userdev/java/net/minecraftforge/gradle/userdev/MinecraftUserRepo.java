package net.minecraftforge.gradle.userdev;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.common.collect.Maps;
import com.google.gson.JsonObject;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.POMBuilder.Dependencies.Dependency;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson.OS;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPConfig;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;

public class MinecraftUserRepo implements ArtifactProvider<ArtifactIdentifier> {
    private final Project project;
    private final Logger log;
    private final String GROUP;
    private final String NAME;
    private final String VERSION;
    private final List<File> ATS;
    private final String MAPPING;
    private final File cache;
    private final boolean isPatcher;

    private boolean loadedParents = false;
    private Patcher parent;
    private MCP mcp;

    /* TODO:
     * Steps to produce each dep:
     *
     * bin:
     *   join jar using MCP Config
     *   If Patcher.srg:
     *     remap joined jar to SRG
     *   apply bin patches to joined jar
     *   remap binpatched jar
     *   remap universal jar
     *   remap universal jar of every parent
     *
     *
     * src:
     *   decompile using MCPConfig with all AT's applied (Already set this up in MCP.getSrcRuntime)
     *   for each parent:
     *     Apply patches
     *     Inject source
     *   remap source
     *   recompile
     *
     *
     * Natives:
     *   Extract natives to a central folder
     *
     * Start:
     *   Build general GradleStart that injects parent access transformers and natives folder.
     */

    public MinecraftUserRepo(Project project, String group, String name, String version, List<File> ats, String mapping) {
        this.project = project;
        this.log = project.getLogger();
        this.GROUP = group;
        this.NAME = name;
        this.VERSION = version;
        this.ATS = ats.stream().filter(File::exists).collect(Collectors.toList());
        this.MAPPING = mapping;
        this.cache = Utils.getCache(project, "minecraft_user_repo");
        this.isPatcher = !"net.minecraft".equals(GROUP);

        GradleRepositoryAdapter.add(project.getRepositories(), "MINECRAFT_USER_DEV", "http://minecraft_user_dev.fake/",
            SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
                .filter(ArtifactIdentifier.groupEquals(GROUP))
                .filter(ArtifactIdentifier.nameEquals(NAME))
                .provide(this)
            )
        );
    }

    private File cache(String... path) {
        return new File(cache, String.join(File.separator, path));
    }
    @SuppressWarnings("unused")
    private File cacheRaw(String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '.' + ext);
    }
    @SuppressWarnings("unused")
    private File cacheRaw(String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '-' + classifier + '.' + ext);
    }
    private File cacheMapped(String mapping, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '.' + ext);
    }
    @SuppressWarnings("unused")
    private File cacheMapped(String mapping, String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '-' + classifier + '.' + ext);
    }

    private String getATHash() {
        if (ATS.isEmpty())
            return null;

        MessageDigest sha = HashFunction.SHA1.get();
        byte[] buf = new byte[1024];

        for (File at : ATS) {
            try (FileInputStream fin = new FileInputStream(at)) {
                int count = -1;
                while ((count = fin.read(buf)) != -1)
                    sha.update(buf, 0, count);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return HashFunction.SHA1.pad(new BigInteger(1, sha.digest()).toString(16));
    }

    public String getDependencyString() {
        String hash = getATHash();
        String ret = GROUP + ':' + NAME + ':' + VERSION;
        if (MAPPING != null)
            ret += "_mapped_" + MAPPING;
        if (hash != null)
            ret += ':' + hash;
        return ret;
    }

    private String getMappings(String version) {
        if (!version.contains("_mapped_")) {
            return null;
        }
        return version.split("_mapped_")[1];
    }

    private String getVersion(String mappings) {
        return mappings == null ? VERSION : VERSION + "_mapped_" + mappings;
    }

    private void log(String line) {
        this.log.lifecycle(line);
    }

    private Patcher getParents() {
        if (!loadedParents) {
            loadedParents = true;
            String artifact = isPatcher ? (GROUP + ':' + NAME + ':' + VERSION + ':' + "userdev") :
                                        ("de.oceanlabs.mcp:mcp_config:" + VERSION + "@zip");
            boolean patcher = isPatcher;
            Patcher last = null;
            while (artifact != null) {
                File dep = MavenArtifactDownloader.single(project, artifact);
                if (dep == null)
                    throw new IllegalStateException("Could not resolve dependency: " + artifact);
                if (patcher) {
                    Patcher _new = new Patcher(dep, artifact);
                    if (parent == null)
                        parent = _new;
                    if (last != null)
                        last.setParent(_new);
                    last = _new;

                    patcher = !_new.parentIsMcp();
                    artifact = _new.getParentDesc();
                } else {
                    mcp = new MCP(dep, artifact);
                    break;
                }
            }
        }
        return parent;
    }

    @Override
    public Artifact getArtifact(ArtifactIdentifier artifact) {
        try {
            String version = artifact.getVersion();
            String mappings = getMappings(version);
            if (mappings != null)
                version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));
            //log("MinecraftUserRepo Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + artifact.getClassifier() + "@" + artifact.getExtension());

            if (!artifact.getGroup().equals(GROUP) || !artifact.getName().equals(NAME) || !version.equals(VERSION))
                return Artifact.none();

            String classifier = artifact.getClassifier() == null ? null : artifact.getClassifier();
            String ext = artifact.getExtension().split("\\.")[0];

            log("MinecraftUserRepo Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

            File ret = null;
            if ("pom".equals(ext)) {
                ret = findPom(mappings);
            } else {
                switch (classifier) {
                    case "":       ret = parent == null ? findMCP(mappings) : null /*findRaw(mappings)*/; break;
                }
            }
            if (ret != null) {
                return provideFile(artifact, ret);
            }
            return Artifact.none();
        } catch (Throwable e) {
            e.printStackTrace();
            log(e.getMessage());
            if (e instanceof RuntimeException) throw (RuntimeException)e;
            throw new RuntimeException(e);
        }
    }

    private HashStore commonHash() {
        HashStore ret = new HashStore(this.cache);
        ret.add(mcp.artifact.getDescriptor(), mcp.data);
        Patcher patcher = parent;
        while (patcher != null) {
            ret.add(parent.artifact.getDescriptor(), parent.data);
            patcher = patcher.getParent();
        }
        return ret;
    }

    private File findPom(String mapping) throws IOException {
        getParents(); //Download parents
        if (mcp == null)
            return null;

        File pom = cacheMapped(mapping, "pom");
        log("Finding pom: " + pom);
        HashStore cache = commonHash().load(cacheMapped(mapping, "pom.input"));

        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, NAME, getVersion(mapping));

            Dependency us = builder.dependencies().add(GROUP + ':' + NAME + ':' + getVersion(mapping), "compile");
            if (getATHash() != null)
                us.withClassifier(getATHash());

            builder.dependencies().add("net.minecraft:client:" + mcp.getMCVersion(), "compile").withClassifier("extra"); //Client as that has all deps as external list
            builder.dependencies().add("net.minecraft:client:" + mcp.getMCVersion(), "compile").withClassifier("data");
            mcp.getLibraries().forEach(e -> builder.dependencies().add(e, "compile"));

            Patcher patcher = parent;
            while (patcher != null) {
                patcher.getLibraries().forEach(e -> builder.dependencies().add(e, "compile"));
                patcher = patcher.getParent();
            }

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

    private File findMCP(String mapping) throws Exception {
        getParents();
        if (mcp == null)
            return null;

        HashStore cache = commonHash();
        File bin = cacheMapped(mapping, "mcp-recmop", "jar"); //Use decompile/recompiled binary if it exists already.
        cache.load(cacheMapped(mapping, "mcp-recomp", "jar.input"));

        if (cache.isSame() && bin.exists())
            return bin;

        bin = cacheMapped(mapping, "mcp-bin", "jar");
        cache.load(cacheMapped(mapping, "mcp-bin", "jar.input"));
        if (!cache.isSame() || !bin.exists()) {
            File output = mcp.getBinRuntime().execute(log);
            FileUtils.copyFile(output, bin);
            cache.save();
        }
        return bin;
    }

    private File findRaw(Patcher patcher, String mapping) throws IOException {


        return null;
    }

    private Artifact provideFile(ArtifactIdentifier artifact, File file) {
        ArtifactType type = ArtifactType.OTHER;
        if ("sources".equals(artifact.getClassifier())) {
            type = ArtifactType.SOURCE;
        } else if ("jar".equals(artifact.getExtension())) {
            type = ArtifactType.BINARY;
        }

        String[] pts  = artifact.getExtension().split("\\.");
        if (pts.length == 1) {
            //log(clean(artifact) + " " + file);
            return StreamableArtifact.ofFile(artifact, type, file);
        } else if (pts.length == 2) {
            File hash = new File(file.getAbsolutePath() + "." + pts[1]);
            if (hash.exists()) {
                //log(clean(artifact) + " " + hash);
                return StreamableArtifact.ofFile(artifact, type, hash);
            }
        }
        return Artifact.none();
    }

    @SuppressWarnings("unused")
    private String clean(ArtifactIdentifier art) {
        return art.getGroup() + ":" + art.getName() + ":" + art.getVersion() + ":" + art.getClassifier() + "@" + art.getExtension();
    }

    private static class Patcher {
        private final File data;
        private final net.minecraftforge.gradle.common.util.Artifact artifact;
        private final UserdevConfigV1 config;
        private Patcher parent;

        private Patcher(File data, String artifact) {
            this.data = data;
            this.artifact = net.minecraftforge.gradle.common.util.Artifact.from(artifact);
            try (ZipFile zip = new ZipFile(data)) {
                ZipEntry entry = zip.getEntry("config.json");
                if (entry == null)
                    throw new IllegalStateException("Invalid patcher dependency: " + artifact + " Missing config.json");

                int spec = Config.getSpec(zip.getInputStream(entry));
                if (spec == 1) {
                    config = Utils.GSON.fromJson(new InputStreamReader(zip.getInputStream(entry)), UserdevConfigV1.class);
                } else {
                    throw new IllegalStateException("Invalid patcher dependency: " + artifact + " Unknown spec: " + spec);
                }
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public boolean parentIsMcp() {
            return this.config.mcp != null;
        }

        public void setParent(Patcher value) {
            this.parent = value;
        }
        public Patcher getParent() {
            return this.parent;
        }

        public String getParentDesc() {
            return this.config.mcp != null ? this.config.mcp : this.config.parent;
        }

        public List<String> getLibraries() {
            return this.config.libraries == null ? Collections.emptyList() : this.config.libraries;
        }
    }

    private class MCP {
        private final File data;
        private final net.minecraftforge.gradle.common.util.Artifact artifact;
        private final MCPConfigV1 config;
        private final byte[] config_data;
        private MCPConfig parsed;
        private MCPRuntime runtime_bin;
        private MCPRuntime runtime_src;

        private MCP(File data, String artifact) {
            this.data = data;
            this.artifact = net.minecraftforge.gradle.common.util.Artifact.from(artifact);
            try (ZipFile zip = new ZipFile(data)) {
                ZipEntry entry = zip.getEntry("config.json");
                if (entry == null)
                    throw new IllegalStateException("Invalid MCP dependency: " + artifact + " Missing config.json");

                config_data = IOUtils.toByteArray(zip.getInputStream(entry));
                int spec = Config.getSpec(new ByteArrayInputStream(config_data));
                if (spec == 1) {
                    config = Utils.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(config_data)), MCPConfigV1.class);
                } else {
                    throw new IllegalStateException("Invalid MCP dependency: " + artifact + " Unknown spec: " + spec);
                }
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public String getMCVersion() {
            return config.version;
        }

        public List<String> getLibraries() {
            return config.getLibraries("joined");
        }

        public MCPRuntime getBinRuntime() throws IOException {
            if (runtime_bin == null) {
                Project project = MinecraftUserRepo.this.project;
                File dir = MinecraftUserRepo.this.cache("mcp", config.version, "joined");

                Map<String, MCPFunction> preDecomps = Maps.newHashMap();
                List<File> ats = MinecraftUserRepo.this.ATS; // We do not include patcher ATs as they are applied at runtime...
                if (!ats.isEmpty()) {
                    preDecomps.put("AccessTransformers", new AccessTransformerFunction(project, ats));
                    dir = new File(dir, MinecraftUserRepo.this.getATHash());
                }

                JsonObject obj = Utils.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(config_data)), JsonObject.class);
                MCPConfig cfg = MCPConfig.deserialize(project, data, obj, "joined");
                runtime_bin = new MCPRuntime(project, cfg, dir, false, preDecomps);
            }
            return runtime_bin;
        }

        public MCPRuntime getSrcRuntime() throws IOException {
            if (runtime_src == null) {
                Project project = MinecraftUserRepo.this.project;
                File dir = MinecraftUserRepo.this.cache("mcp", config.version, "joined");

                Map<String, MCPFunction> preDecomps = Maps.newHashMap();
                List<File> ats = MinecraftUserRepo.this.ATS;
                if (!ats.isEmpty()) {
                    preDecomps.put("AccessTransformers", new AccessTransformerFunction(project, ats));
                    dir = new File(dir, MinecraftUserRepo.this.getATHash());
                }
                //TODO: read parent ATs as string and add them to the function as a string.

                JsonObject obj = Utils.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(config_data)), JsonObject.class);
                MCPConfig cfg = MCPConfig.deserialize(project, data, obj, "joined");
                runtime_bin = new MCPRuntime(project, cfg, dir, false, preDecomps);
                runtime_src = new MCPRuntime(project, cfg, dir, true, preDecomps);
            }
            return runtime_src;
        }
    }
}
