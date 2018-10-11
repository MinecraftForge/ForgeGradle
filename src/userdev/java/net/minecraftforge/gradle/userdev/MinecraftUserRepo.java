package net.minecraftforge.gradle.userdev;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.google.common.collect.Maps;

import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import net.minecraftforge.gradle.userdev.tasks.ApplyBinPatches;
import net.minecraftforge.gradle.userdev.tasks.RenameJar;

public class MinecraftUserRepo extends BaseRepo {
    private final Project project;
    private final String GROUP;
    private final String NAME;
    private final String VERSION;
    private final List<File> ATS;
    private final String AT_HASH;
    private final String MAPPING;
    private final boolean isPatcher;
    private final Map<String, McpNames> mapCache = new HashMap<>();
    private boolean loadedParents = false;
    private Patcher parent;
    private MCP mcp;
    @SuppressWarnings("unused")
    private Repository repo;

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
     *
     * Version Setup:
     *   [Version]_mapped_
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

        repo = SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class)
            .filter(ArtifactIdentifier.groupEquals(GROUP))
            .filter(ArtifactIdentifier.nameEquals(NAME))
            .provide(this)
        );
    }

    @SuppressWarnings("unused")
    private File cacheRaw(String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '.' + ext);
    }
    private File cacheRaw(String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, VERSION, NAME + '-' + VERSION + '-' + classifier + '.' + ext);
    }
    private File cacheMapped(String mapping, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '.' + ext);
    }
    private File cacheMapped(String mapping, String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersion(mapping), NAME + '-' + getVersion(mapping) + '-' + classifier + '.' + ext);
    }

    public String getDependencyString() {
        String ret = GROUP + ':' + NAME + ':' + VERSION;
        if (MAPPING != null)
            ret += "_mapped_" + MAPPING;
        if (AT_HASH != null)
            ret += "_at_" + AT_HASH;
        ret = "rnd." + (new Random().nextInt()) + "." + ret; //Stupid hack to make gradle always try and ask for this file. This should be removed once we figure out why the hell gradle just randomly decides to not try to resolve us!
        return ret;
    }

    private String getATHash(String version) {
        if (!version.contains("_at_"))
            return null;
        return version.split("_at_")[1];
    }
    private String getMappings(String version) {
        if (!version.contains("_mapped_"))
            return null;
        return version.split("_mapped_")[1];
    }

    private String getVersion(String mappings) {
        return mappings == null ? VERSION : VERSION + "_mapped_" + mappings;
    }
    private String getVersionWithAT(String mappings) {
        if (AT_HASH == null) return getVersion(mappings);
        return getVersion(mappings) + "_at_" + AT_HASH;
    }

    private Patcher getParents() {
        if (!loadedParents) {
            String artifact = isPatcher ? (GROUP + ':' + NAME + ':' + VERSION + ':' + "userdev") :
                                        ("de.oceanlabs.mcp:mcp_config:" + VERSION + "@zip");
            boolean patcher = isPatcher;
            Patcher last = null;
            while (artifact != null) {
                debug("    Parent: " + artifact);
                File dep = MavenArtifactDownloader.single(project, artifact);
                if (dep == null)
                    throw new IllegalStateException("Could not resolve dependency: " + artifact);
                if (patcher) {
                    Patcher _new = new Patcher(project, dep, artifact);
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
            loadedParents = mcp != null;
        }
        return parent;
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String group = artifact.getGroup();
        String rand = "";
        if (group.startsWith("rnd.")) {
            rand = group.substring(0, group.indexOf('.', 4));
            group = group.substring(group.indexOf('.', 4) + 1);
        }
        String version = artifact.getVersion();
        String athash = getATHash(version); //There is no way to reverse the ATs from the hash, so this is just to make Gradle request a new file if they change.
        if (athash != null)
            version = version.substring(0, version.length() - (athash.length() + "_at_".length()));

        String mappings = getMappings(version);
        if (mappings != null)
            version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));

        if (!group.equals(GROUP) || !artifact.getName().equals(NAME) || !version.equals(VERSION))
            return null;

        if ((AT_HASH == null && athash != null) || (!AT_HASH.equals(athash)))
            return null;

        if (!isPatcher && mappings == null) //net.minecraft in obf names. We don't do that.
            return null;

        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();
        String ext = artifact.getExtension().split("\\.")[0];

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            return findPom(mappings, rand);
        } else {
            switch (classifier) {
                case "":       return findRaw(mappings);
            }
        }
        return null;
    }

    private HashStore commonHash(File mapping) {
        getParents();
        HashStore ret = new HashStore(this.cache);
        ret.add(mcp.artifact.getDescriptor(), mcp.getZip());
        Patcher patcher = parent;
        while (patcher != null) {
            ret.add(parent.artifact.getDescriptor(), parent.data);
            patcher = patcher.getParent();
        }
        if (mapping != null)
            ret.add("mapping", mapping);
        ret.add("ats", AT_HASH);

        return ret;
    }

    private File findMapping(String mapping) {
        if (mapping == null)
            return null;

        int idx = mapping.lastIndexOf('_');
        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);
        String desc = "de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip";
        debug("    Mapping: " + desc);
        //Artifact artifact = Artifact.from(desc);

        File central = MavenArtifactDownloader.single(project, desc);
        //TODO: Stick in cache?
        return central;
    }

    private File findPom(String mapping, String rand) throws IOException {
        getParents(); //Download parents
        if (mcp == null || mapping == null)
            return null;

        File pom = cacheMapped(mapping, "pom");
        if (!rand.isEmpty()) {
            rand += '.';
            pom = cacheMapped(mapping, rand + "pom");
        }

        debug("  Finding pom: " + pom);
        HashStore cache = commonHash(null).load(new File(pom.getAbsolutePath() + ".input"));

        if (!cache.isSame() || !pom.exists() || "".equals("")) {
            POMBuilder builder = new POMBuilder(rand + GROUP, NAME, getVersionWithAT(mapping) );

            builder.dependencies().add(rand + GROUP + ':' + NAME + ':' + getVersionWithAT(mapping), "compile");
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
            Utils.updateHash(pom, HashFunction.SHA1);
        }

        return pom;
    }

    private File findBinPatches() throws IOException {
        File ret = cacheRaw("binpatches", "lzma");
        HashStore cache = new HashStore().load(cacheRaw("binpatches", "lzma.input"))
                .add("parent", parent.getZip());

        if (!cache.isSame() || !ret.exists()) {
            try (ZipFile zip = new ZipFile(parent.getZip())) {
                Utils.extractFile(zip, parent.getConfig().binpatches, ret);
                cache.save();
            }
        }

        return ret;
    }

    private File findRaw(String mapping) throws IOException {
        File names = findMapping(mapping);
        HashStore cache = commonHash(names);

        File recomp = cacheMapped(mapping, "recomp", ".jar");
        if (recomp.exists()) { //Recompiled binary, use this first if valid!
            cache.load(cacheMapped(mapping, "recomp", ".jar.input"));
            debug("  Finding recomp: " + cache.isSame() + " " + recomp);
            if (cache.isSame())
                return recomp;
        }

        File bin = cacheMapped(mapping, ".jar");
        cache.load(cacheMapped(mapping, ".jar.input"));
        if (!cache.isSame() || !bin.exists() || "".equals("")) {

            File srged = null;
            if (parent == null) { //Raw minecraft
                srged = MavenArtifactDownloader.single(project, "net.minecraft:joined:" + mcp.getVersion() + ":srg"); //Download vanilla in srg name
            } else { // Needs binpatches
                File joined = MavenArtifactDownloader.single(project, "net.minecraft:joined:" + mcp.getVersion() + ":srg"); //Download vanilla in srg name
                File binpatched = cacheRaw("binpatched", "jar");

                //Apply bin patches to vanilla
                ApplyBinPatches apply = project.getTasks().create("_" + new Random().nextInt() + "_", ApplyBinPatches.class);
                apply.setHasLog(false);
                apply.setTool(parent.getConfig().binpatcher.getVersion());
                apply.setArgs(parent.getConfig().binpatcher.getArgs());
                apply.setClean(joined);
                apply.setPatch(findBinPatches());
                apply.setOutput(binpatched);
                apply.apply();

                srged = cacheRaw("srg", "jar");
                //Combine all universals and vanilla together.
                Set<String> added = new HashSet<>();
                try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(srged))) {

                    //Add binpatched, then vanilla first, overrides any other entries added
                    for (File file : new File[] {binpatched, joined}) {
                        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(file))) {
                            ZipEntry entry;
                            while ((entry = zin.getNextEntry()) != null) {
                                if (added.contains(entry.getName()))
                                    continue;
                                ZipEntry _new = new ZipEntry(entry.getName());
                                _new.setTime(entry.getTime()); //Should be stable, but keeping time.
                                zip.putNextEntry(_new);
                                IOUtils.copy(zin, zip);
                                added.add(entry.getName());
                            }
                        }
                    }

                    // Walk parents and combine from bottom up so we get any overridden files.
                    Patcher patcher = parent;
                    while (patcher != null) {
                        if (patcher.getUniversal() != null) {
                            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getUniversal()))) {
                                ZipEntry entry;
                                while ((entry = zin.getNextEntry()) != null) {
                                    if (added.contains(entry.getName()))
                                        continue;
                                    ZipEntry _new = new ZipEntry(entry.getName());
                                    _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
                                    zip.putNextEntry(_new);
                                    IOUtils.copy(zin, zip);
                                    added.add(entry.getName());
                                }
                            }
                        }
                        patcher = patcher.getParent();
                    }
                }
            }
            if (mapping == null) { //They didn't ask for MCP names, so serve them SRG!
                FileUtils.copyFile(srged, bin);
            } else {
                //Remap library to MCP names
                RenameJar rename = project.getTasks().create("_" + new Random().nextInt() + "_", RenameJar.class);
                rename.setHasLog(false);
                rename.setInput(srged);
                rename.setOutput(bin);
                rename.setMappings(findSrgToMcp(mapping));
                rename.apply();
            }

            //TODO: Apply ATs
            Utils.updateHash(bin);
            cache.save();
        }
        return bin;
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

    private File findObfToMcp(String mapping) throws IOException {
        File root = cache(mcp.getArtifact().getGroup().replace('.', '/'), mcp.getArtifact().getName(), mcp.getArtifact().getVersion());
        File names = findMapping(mapping);
        String srg_name = (mapping == null ? "obf_to_srg" : "obf_to_" + mapping) + ".tsrg";
        File srg = new File(root, srg_name);

        HashStore cache = new HashStore()
            .add("mcp", mcp.getZip())
            .add("mapping", names)
            .load(new File(root, srg_name + ".input"));

        if (!cache.isSame() || !srg.exists()) {
            info("Creating Obf -> MCP SRG");
            byte[] data = mcp.getData("mappings");
            String mapped = loadMCPNames(mapping, names).rename(new ByteArrayInputStream(data), false);
            Files.write(srg.toPath(), mapped.getBytes(StandardCharsets.UTF_8));
            cache.save();
        }

        return srg;
    }

    private File findSrgToMcp(String mapping) throws IOException {
        File root = cache(mcp.getArtifact().getGroup().replace('.', '/'), mcp.getArtifact().getName(), mcp.getArtifact().getVersion());
        File names = findMapping(mapping);
        String srg_name = "srg_to_" + mapping + ".tsrg";
        File srg = new File(root, srg_name);

        HashStore cache = new HashStore()
            .add("mcp", mcp.getZip())
            .add("mapping", names)
            .load(new File(root, srg_name + ".input"));

        if (!cache.isSame() || !srg.exists()) {
            info("Creating SRG -> MCP TSRG");
            byte[] data = mcp.getData("mappings");
            McpNames mcp_names = loadMCPNames(mapping, names);
            MappingFile obf_to_srg = MappingFile.load(new ByteArrayInputStream(data));
            MappingFile srg_to_named = new MappingFile();

            obf_to_srg.getPackages().forEach(e -> srg_to_named.addPackage(e.getMapped(), e.getMapped()));
            obf_to_srg.getClasses().forEach(cls -> {
               srg_to_named.addClass(cls.getMapped(), cls.getMapped());
               MappingFile.Cls _cls = srg_to_named.getClass(cls.getMapped());
               cls.getFields().forEach(fld -> _cls.addField(fld.getMapped(), mcp_names.rename(fld.getMapped())));
               cls.getMethods().forEach(mtd -> _cls.addMethod(mtd.getMapped(), mtd.getMappedDescriptor(), mcp_names.rename(mtd.getMapped())));
            });

            List<String> lines = srg_to_named.write(MappingFile.Format.TSRG, false);
            if (!srg.getParentFile().exists())
                srg.getParentFile().mkdirs();
            try (FileOutputStream out = new FileOutputStream(srg)){
                for (String line : lines) {
                    out.write(line.getBytes());
                    out.write('\n');
                }
            }
            cache.save();
        }

        return srg;
    }

    private static class Patcher {
        private final File data;
        private final File universal;
        private final Artifact artifact;
        private final UserdevConfigV1 config;
        private Patcher parent;
        private String ATs = null;

        private Patcher(Project project, File data, String artifact) {
            this.data = data;
            this.artifact = Artifact.from(artifact);

            try {
                byte[] cfg_data = Utils.getZipData(data, "config.json");
                int spec = Config.getSpec(cfg_data);
                if (spec != 1)
                    throw new IllegalStateException("Could not load Patcher config, Unknown Spec: " + spec + " Dep: " + artifact);
                this.config = UserdevConfigV1.get(cfg_data);
                if (getParentDesc() == null)
                    throw new IllegalStateException("Invalid patcher dependency, missing MCP or parent: " + artifact);

                if (config.universal != null) {
                    universal = MavenArtifactDownloader.single(project, config.universal);
                    if (universal == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve universal: " + universal);
                } else {
                    universal = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public UserdevConfigV1 getConfig() {
            return config;
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

        public File getZip() {
            return data;
        }

        public File getUniversal() {
            return universal;
        }
    }

    private class MCP {
        private final MCPWrapper wrapper;
        private final Artifact artifact;
        private MCPRuntime runtime;

        private MCP(File data, String artifact) {
            this.artifact = Artifact.from(artifact);
            try {
                File mcp_dir = MinecraftUserRepo.this.cache("mcp", this.artifact.getVersion());
                this.wrapper = new MCPWrapper(data, mcp_dir);
            } catch (IOException e) {
                throw new RuntimeException("Invalid patcher dependency: " + artifact, e);
            }
        }

        public byte[] getData(String... path) throws IOException {
            return wrapper.getData(path);
        }

        public Artifact getArtifact() {
            return artifact;
        }

        public File getZip() {
            return wrapper.getZip();
        }

        public String getVersion() {
            return artifact.getVersion();
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
