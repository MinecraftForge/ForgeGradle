package net.minecraftforge.gradle.userdev;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.google.common.collect.Maps;
import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.POMBuilder.Dependencies.Dependency;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;

public class MinecraftUserRepo extends BaseRepo {
    private final Project project;
    private final String GROUP;
    private final String NAME;
    private final String VERSION;
    private final List<File> ATS;
    private final String AT_HASH;
    private final String MAPPING;
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
        super(Utils.getCache(project, "minecraft_user_repo"), project.getLogger());
        this.project = project;
        this.GROUP = group;
        this.NAME = name;
        this.VERSION = version;
        this.ATS = ats.stream().filter(File::exists).collect(Collectors.toList());
        try {
            this.AT_HASH = ATS.isEmpty() ? null : HashFunction.SHA1.hash(ATS);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.MAPPING = mapping;
        this.isPatcher = !"net.minecraft".equals(GROUP);

        /*
        GradleRepositoryAdapter.add(project.getRepositories(), "MINECRAFT_USER_DEV", "http://minecraft_user_dev.fake/",
            SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
                .filter(ArtifactIdentifier.groupEquals(GROUP))
                .filter(ArtifactIdentifier.nameEquals(NAME))
                .provide(this)
            )
        );
        */
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

    public String getDependencyString() {
        String ret = GROUP + ':' + NAME + ':' + VERSION;
        if (MAPPING != null)
            ret += "_mapped_" + MAPPING;
        if (AT_HASH != null)
            ret += ':' + AT_HASH;
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
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String version = artifact.getVersion();
        String mappings = getMappings(version);
        if (mappings != null)
            version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));

        if (!artifact.getGroup().equals(GROUP) || !artifact.getName().equals(NAME) || !version.equals(VERSION))
            return null;

        String classifier = artifact.getClassifier() == null ? null : artifact.getClassifier();
        String ext = artifact.getExtension().split("\\.")[0];

        debug(REPO_NAME + " Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            return findPom(mappings);
        } else {
            switch (classifier) {
                //case "":       return parent == null ? findMCP(mappings) : null /*findRaw(mappings)*/;
            }
        }
        return null;
    }

    private HashStore commonHash() {
        HashStore ret = new HashStore(this.cache);
        ret.add(mcp.artifact.getDescriptor(), mcp.getZip());
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
        debug("  Finding pom: " + pom);
        HashStore cache = commonHash().load(cacheMapped(mapping, "pom.input"));

        if (!cache.isSame() || !pom.exists()) {
            POMBuilder builder = new POMBuilder(GROUP, NAME, getVersion(mapping));

            Dependency us = builder.dependencies().add(GROUP + ':' + NAME + ':' + getVersion(mapping), "compile");
            if (AT_HASH != null)
                us.withClassifier(AT_HASH);

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

    private static class Patcher {
        private final File data;
        private final net.minecraftforge.gradle.common.util.Artifact artifact;
        private final UserdevConfigV1 config;
        private Patcher parent;
        private String ATs = null;

        private Patcher(File data, String artifact) {
            this.data = data;
            this.artifact = net.minecraftforge.gradle.common.util.Artifact.from(artifact);

            try {
                byte[] cfg_data = Utils.getZipData(data, "config.json");
                int spec = Config.getSpec(cfg_data);
                if (spec != 1)
                    throw new IllegalStateException("Could not load Patcher config, Unknown Spec: " + spec + " Dep: " + artifact);
                this.config = UserdevConfigV1.get(cfg_data);
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

        public String getATData() {
            if (config.getATs().isEmpty())
                return null;

            if (ATs == null) {
                StringBuilder buf = new StringBuilder();
                try (ZipFile zip = new ZipFile(data)) {
                    for (String at : config.getATs()) {
                        ZipEntry entry = zip.getEntry(at);
                        if (entry == null)
                            throw new IllegalStateException("Invalid Patcher config, Missing Access Transformer: " + at + " Zip: " + data);
                        buf.append("# ").append(artifact).append(" - ").append(at).append('\n');
                        buf.append(IOUtils.toString(zip.getInputStream(entry), Charsets.UTF_8));
                        buf.append('\n');
                    }
                    ATs = buf.toString();
                } catch (IOException e) {
                    throw new RuntimeException("Invalid patcher config: " + artifact, e);
                }
            }
            return ATs;
        }
    }

    private class MCP {
        private final MCPWrapper wrapper;
        private final net.minecraftforge.gradle.common.util.Artifact artifact;
        private MCPRuntime runtime;

        private MCP(File data, String artifact) {
            this.artifact = net.minecraftforge.gradle.common.util.Artifact.from(artifact);
            try {
                File mcp_dir = MinecraftUserRepo.this.cache("mcp", this.artifact.getVersion());
                this.wrapper = new MCPWrapper(data, mcp_dir);
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public File getZip() {
            return wrapper.getZip();
        }

        public String getMCVersion() {
            return wrapper.getConfig().getVersion();
        }

        public List<String> getLibraries() {
            return wrapper.getConfig().getLibraries("joined");
        }

        public MCPRuntime getRuntime(Patcher patcher) throws IOException {
            if (runtime == null) {
                Project project = MinecraftUserRepo.this.project;
                File dir = new File(wrapper.getRoot(), "joined");
                String AT_HASH = MinecraftUserRepo.this.AT_HASH;
                List<File> ATS = MinecraftUserRepo.this.ATS;

                AccessTransformerFunction function = new AccessTransformerFunction(project, ATS);
                boolean empty = true;
                if (AT_HASH != null) {
                    dir = new File(dir, AT_HASH);
                    empty = false;
                }


                while (patcher != null) {
                    String at = patcher.getATData();
                    if (at != null && !at.isEmpty()) {
                        function.addTransformer(at);
                        empty = false;
                    }
                    patcher = patcher.getParent();
                }

                Map<String, MCPFunction> preDecomps = Maps.newHashMap();
                if (!empty)
                    preDecomps.put("AccessTransformer", function);

                runtime = new MCPRuntime(project, wrapper.getZip(), wrapper.getConfig(), "joined", dir, preDecomps);
            }
            return runtime;
        }
    }
}
