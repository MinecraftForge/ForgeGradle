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

package net.minecraftforge.gradle.userdev;


import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtraPropertiesExtension;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.cloudbees.diff.PatchException;
import com.google.common.collect.Maps;
import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.diff.ContextualPatch;
import net.minecraftforge.gradle.common.diff.ContextualPatch.PatchReport;
import net.minecraftforge.gradle.common.diff.HunkReport;
import net.minecraftforge.gradle.common.diff.PatchFile;
import net.minecraftforge.gradle.common.diff.ZipContext;
import net.minecraftforge.gradle.common.util.*;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import net.minecraftforge.gradle.userdev.tasks.AccessTrasnformJar;
import net.minecraftforge.gradle.userdev.tasks.ApplyBinPatches;
import net.minecraftforge.gradle.userdev.tasks.ApplyMCPFunction;
import net.minecraftforge.gradle.userdev.tasks.RenameJar;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;

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
     *   [Version]_mapped_[mapping]_at_[AtHash]
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

    public void validate() {
        getParents();
        if (mcp == null)
            throw new IllegalStateException("Invalid minecraft dependency: " + GROUP + ":" + NAME + ":" + VERSION);
        ExtraPropertiesExtension ext = project.getExtensions().getExtraProperties();
        ext.set("MC_VERSION", mcp.getMCVersion());
        ext.set("MCP_VERSION", mcp.getArtifact().getVersion());
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
    private File cacheAT(String classifier, String ext) {
        return cache(GROUP.replace('.', File.separatorChar), NAME, getVersionAT(), NAME + '-' + getVersionAT() + '-' + classifier + '.' + ext);
    }

    public String getDependencyString() {
        String ret = GROUP + ':' + NAME + ':' + VERSION;
        if (MAPPING != null)
            ret += "_mapped_" + MAPPING;
        if (AT_HASH != null)
            ret += "_at_" + AT_HASH;
        //ret = "rnd." + (new Random().nextInt()) + "." + ret; //Stupid hack to make gradle always try and ask for this file. This should be removed once we figure out why the hell gradle just randomly decides to not try to resolve us!
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
    private String getVersionAT() {
        if (AT_HASH == null) return VERSION;
        return VERSION + "_at_" + AT_HASH;
    }

    private Patcher getParents() {
        if (!loadedParents) {
            String artifact = isPatcher ? (GROUP + ':' + NAME + ':' + VERSION + ':' + "userdev") :
                                        ("de.oceanlabs.mcp:mcp_config:" + VERSION + "@zip");
            boolean patcher = isPatcher;
            Patcher last = null;
            while (artifact != null) {
                debug("    Parent: " + artifact);
                File dep = Utils.downloadMaven(project, Artifact.from(artifact), false);
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

        if ((AT_HASH == null && athash != null) || (AT_HASH != null && !AT_HASH.equals(athash)))
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
                case "":        return findRaw(mappings);
                case "sources": return findSource(mappings);
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
        if (AT_HASH != null)
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
        return Utils.downloadMaven(project, Artifact.from(desc), false);
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

        if (!cache.isSame() || !pom.exists()) {
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

        File recomp = cacheMapped(mapping, "recomp", "jar");
        if (recomp.exists()) { //Recompiled binary, use this first if valid!
            cache.load(cacheMapped(mapping, "recomp", "jar.input"));
            debug("  Finding recomp: " + cache.isSame() + " " + recomp);
            if (cache.isSame())
                return recomp;
        }

        File bin = cacheMapped(mapping, "jar");
        cache.load(cacheMapped(mapping, "jar.input"));
        if (!cache.isSame() || !bin.exists()) {
            StringBuilder baseAT = new StringBuilder();

            for (Patcher patcher = parent; patcher != null; patcher = patcher.parent) {
                if (patcher.getATData() != null && !patcher.getATData().isEmpty()) {
                    if (baseAT.length() != 0)
                        baseAT.append("\n===========================================================\n");
                    baseAT.append(patcher.getATData());
                }
            }
            boolean hasAts = baseAT.length() != 0 || !ATS.isEmpty();

            File srged = null;
            if (parent == null) { //Raw minecraft
                srged = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + mcp.getVersion() + ":srg", true); //Download vanilla in srg name
            } else { // Needs binpatches
                File joined = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + mcp.getVersion() + ":srg", true); //Download vanilla in srg name
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
                Map<String, List<String>> servicesLists = new HashMap<>();
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
                                    String name = entry.getName();
                                    if (added.contains(name))
                                        continue;
                                    if (name.startsWith("META-INF/services/") && !entry.isDirectory())
                                    {
                                        List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                                        if (existing.size() > 0) existing.add("");
                                        existing.add(String.format("# %s - %s", patcher.artifact, patcher.getUniversal().getCanonicalFile().getName()));
                                        existing.addAll(IOUtils.readLines(zin));
                                    }
                                    else
                                    {
                                        ZipEntry _new = new ZipEntry(name);
                                        _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
                                        zip.putNextEntry(_new);
                                        IOUtils.copy(zin, zip);
                                        added.add(name);
                                    }
                                }
                            }
                        }
                        // Dev time specific files, such as launch helper.
                        if (patcher.getInject() != null) {
                            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getZip()))) {
                                ZipEntry entry;
                                while ((entry = zin.getNextEntry()) != null) {
                                    if (!entry.getName().startsWith(patcher.getInject()) || entry.getName().length() <= patcher.getInject().length())
                                        continue;
                                    String name = entry.getName().substring(patcher.getInject().length());
                                    if (added.contains(name))
                                        continue;
                                    if (name.startsWith("META-INF/services/") && !entry.isDirectory())
                                    {
                                        List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                                        if (existing.size() > 0) existing.add("");
                                        existing.add(String.format("# %s - %s", patcher.artifact, patcher.getZip().getCanonicalFile().getName()));
                                        existing.addAll(IOUtils.readLines(zin));
                                    }
                                    else
                                    {
                                        ZipEntry _new = new ZipEntry(name);
                                        _new.setTime(0);
                                        zip.putNextEntry(_new);
                                        IOUtils.copy(zin, zip);
                                        added.add(name);
                                    }
                                }
                            }
                        }
                        patcher = patcher.getParent();
                    }

                    for(Map.Entry<String, List<String>> kv : servicesLists.entrySet())
                    {
                        String name = kv.getKey();
                        ZipEntry _new = new ZipEntry(name);
                        _new.setTime(0);
                        zip.putNextEntry(_new);
                        IOUtils.writeLines(kv.getValue(), "\n", zip);
                        added.add(name);
                    }
                }
            }

            File mcinject = cacheRaw("mci", "jar");

            //Apply MCInjector so we can compile against this jar
            ApplyMCPFunction mci = project.getTasks().create("_mciJar_" + new Random().nextInt() + "_", ApplyMCPFunction.class);
            mci.setFunctionName("mcinject");
            mci.setHasLog(false);
            mci.setInput(srged);
            mci.setMCP(mcp.getZip());
            mci.setOutput(mcinject);
            mci.apply();

            if (hasAts) {
                if (bin.exists()) bin.delete(); // AT lib throws an exception if output file already exists

                AccessTrasnformJar at = project.getTasks().create("_atJar_"+ new Random().nextInt() + "_", AccessTrasnformJar.class);
                at.setInput(mcinject);
                at.setOutput(bin);
                at.setAts(ATS);

                if (baseAT.length() != 0) {
                    File parentAT = project.file("build/" + at.getName() + "/parent_at.cfg");
                    if (!parentAT.getParentFile().exists())
                        parentAT.getParentFile().mkdirs();
                    Files.write(parentAT.toPath(), baseAT.toString().getBytes());
                    at.setAts(parentAT);
                }

                at.apply();
            }

            if (mapping == null) { //They didn't ask for MCP names, so serve them SRG!
                FileUtils.copyFile(mcinject, bin);
            } else if (hasAts) {
                //Remap library to MCP names, in place, sorta hacky with ATs but it should work.
                RenameJarInPlace rename = project.getTasks().create("_rename_" + new Random().nextInt() + "_", RenameJarInPlace.class);
                rename.setHasLog(false);
                rename.setInput(bin);
                rename.setMappings(findSrgToMcp(mapping, names));
                rename.apply();
            } else {
                //Remap library to MCP names
                RenameJar rename = project.getTasks().create("_rename_" + new Random().nextInt() + "_", RenameJar.class);
                rename.setHasLog(false);
                rename.setInput(mcinject);
                rename.setOutput(bin);
                rename.setMappings(findSrgToMcp(mapping, names));
                rename.apply();
            }

            Utils.updateHash(bin, HashFunction.SHA1);
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

    private File findSrgToMcp(String mapping, File names) throws IOException {
        File root = cache(mcp.getArtifact().getGroup().replace('.', '/'), mcp.getArtifact().getName(), mcp.getArtifact().getVersion());
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

            srg_to_named.write(MappingFile.Format.TSRG, srg, false);
            cache.save();
        }

        return srg;
    }

    private File findDecomp() throws IOException {
        HashStore cache = commonHash(null);

        File decomp = cacheAT("decomp", "jar");
        debug("    Finding Decomp: " + decomp);
        cache.load(cacheAT("decomp", "jar.input"));

        if (!cache.isSame() || !decomp.exists()) {
            File output = mcp.getStepOutput("joined", null);
            FileUtils.copyFile(output, decomp);
            cache.save();
            Utils.updateHash(decomp, HashFunction.SHA1);
        }
        return decomp;
    }

    private File findPatched() throws IOException {
        File decomp = findDecomp();
        if (decomp == null) return null;
        if (parent == null) return decomp;

        HashStore cache = commonHash(null).add("decomp", decomp);

        File patched = cacheAT("patched", "jar");
        debug("    Finding patched: " + decomp);
        cache.load(cacheAT("patched", "jar.input"));

        if (!cache.isSame() || !patched.exists()) {
            LinkedList<Patcher> parents = new LinkedList<>();
            Patcher patcher = parent;
            while (patcher != null) {
                parents.addFirst(patcher);
                patcher = patcher.getParent();
            }

            try (ZipFile zip = new ZipFile(decomp)) {
                ZipContext context = new ZipContext(zip);

                boolean failed = false;
                for (Patcher p : parents) {
                    Map<String, PatchFile> patches = p.getPatches();
                    if (!patches.isEmpty()) {
                        debug("      Apply Patches: " + p.artifact);
                        List<String> keys = new ArrayList<>(patches.keySet());
                        Collections.sort(keys);
                        for (String key : keys) {
                            ContextualPatch patch = ContextualPatch.create(patches.get(key), context);
                            patch.setCanonialization(true, false);
                            patch.setMaxFuzz(0);
                            try {
                                debug("        Apply Patch: " + key);
                                List<PatchReport> result = patch.patch(false);
                                for (int x = 0; x < result.size(); x++) {
                                    PatchReport report = result.get(x);
                                    if (!report.getStatus().isSuccess()) {
                                        log.error("  Failed to apply patch: " + p.artifact + ": " + key);
                                        failed = true;
                                        for (int y = 0; y < report.hunkReports().size(); y++) {
                                            HunkReport hunk = report.hunkReports().get(y);
                                            if (hunk.hasFailed()) {
                                                if (hunk.failure == null) {
                                                    log.error("    Hunk #" + hunk.hunkID + " Failed @" + hunk.index + " Fuzz: " + hunk.fuzz);
                                                } else {
                                                    log.error("    Hunk #" + hunk.hunkID + " Failed: " + hunk.failure.getMessage());
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (PatchException e) {
                                log.error("  Apply Patch: " + p.artifact + ": " + key);
                                log.error("    " + e.getMessage());
                            }
                        }
                    }
                }
                if (failed)
                    throw new RuntimeException("Failed to apply patches to source file, see log for details: " + decomp);

                Set<String> added = new HashSet<>();
                try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(patched))) {
                    context.save(zout);

                    // Walk parents and combine from bottom up so we get any overridden files.
                    patcher = parent;
                    while (patcher != null) {
                        if (patcher.getSources() != null) {
                            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getSources()))) {
                                ZipEntry entry;
                                while ((entry = zin.getNextEntry()) != null) {
                                    if (added.contains(entry.getName()) || entry.getName().startsWith("patches/")) //Skip patches, as they are included in src for reference.
                                        continue;
                                    ZipEntry _new = new ZipEntry(entry.getName());
                                    _new.setTime(0); //SHOULD be the same time as the main entry, but NOOOO _new.setTime(entry.getTime()) throws DateTimeException, so you get 0, screw you!
                                    zout.putNextEntry(_new);
                                    IOUtils.copy(zin, zout);
                                    added.add(entry.getName());
                                }
                            }
                        }
                        patcher = patcher.getParent();
                    }
                }

                cache.save();
                Utils.updateHash(patched, HashFunction.SHA1);
            }
        }
        return patched;
    }

    private File findSource(String mapping) throws IOException {
        File patched = findPatched();
        if (patched == null) return null;
        if (mapping == null) return patched;

        File names = findMapping(mapping);
        if (names == null) return null;

        HashStore cache = commonHash(names);

        File sources = cacheMapped(mapping, "sources", "jar");
        debug("    Finding Source: " + sources);
        cache.load(cacheMapped(mapping, "sources", "jar.input"));
        if (!cache.isSame() || !sources.exists()) {
            McpNames map = McpNames.load(names);

            if (!sources.getParentFile().exists())
                sources.getParentFile().mkdirs();

            try(ZipInputStream zin = new ZipInputStream(new FileInputStream(patched));
                ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(sources))) {
                ZipEntry _old;
                while ((_old = zin.getNextEntry()) != null) {
                    ZipEntry _new = new ZipEntry(_old.getName());
                    _new.setTime(0);
                    zout.putNextEntry(_new);

                    if (_old.getName().endsWith(".java")) {
                        String mapped = map.rename(zin, true);
                        IOUtils.write(mapped, zout);
                    } else {
                        IOUtils.copy(zin, zout);
                    }
                }
            }

            Utils.updateHash(sources, HashFunction.SHA1);
            cache.save();
        }
        return sources;
    }

    private static class Patcher {
        private final File data;
        private final File universal;
        private final File sources;
        private final Artifact artifact;
        private final UserdevConfigV1 config;
        private Patcher parent;
        private String ATs = null;
        private Map<String, PatchFile> patches;

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
                    universal = Utils.downloadMaven(project, Artifact.from(config.universal), false);
                    if (universal == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve universal: " + universal);
                } else {
                    universal = null;
                }

                if (config.sources != null) {
                    sources = MavenArtifactDownloader.gradle(project, config.sources, false);
                    if (sources == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve sources: " + sources);
                } else {
                    sources = null;
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
        public File getSources() {
            return sources;
        }

        public String getInject() {
            return config.inject;
        }

        public Map<String, PatchFile> getPatches() throws IOException {
            if (config.patches == null)
                return Collections.emptyMap();

            if (patches == null) {
                patches = new HashMap<>();
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(getZip()))) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        if (!entry.getName().startsWith(config.patches) || !entry.getName().endsWith(".patch"))
                            continue;
                        byte[] data = IOUtils.toByteArray(zin);
                        patches.put(entry.getName().substring(0, entry.getName().length() - 6), PatchFile.from(data));
                    }
                }
            }
            return patches;
        }
    }

    private class MCP {
        private final MCPWrapper wrapper;
        private final Artifact artifact;

        private MCP(File data, String artifact) {
            this.artifact = Artifact.from(artifact);
            try {
                File mcp_dir = MinecraftUserRepo.this.cache("mcp", this.artifact.getVersion());
                this.wrapper = new MCPWrapper(data, mcp_dir) {
                    public MCPRuntime getRuntime(Project project, String side) {
                        MCPRuntime ret = runtimes.get(side);
                        if (ret == null) {
                            File dir = new File(wrapper.getRoot(), side);
                            String AT_HASH = MinecraftUserRepo.this.AT_HASH;
                            List<File> ATS = MinecraftUserRepo.this.ATS;

                            AccessTransformerFunction function = new AccessTransformerFunction(project, ATS);
                            boolean empty = true;
                            if (AT_HASH != null) {
                                dir = new File(dir, AT_HASH);
                                empty = false;
                            }

                            Patcher patcher = MinecraftUserRepo.this.parent;
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

                            ret = new MCPRuntime(project, data, getConfig(), side, dir, preDecomps);
                            runtimes.put(side, ret);
                        }
                        return ret;
                    }
                };
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

        public File getStepOutput(String side, String step) throws IOException {
            MCPRuntime runtime = wrapper.getRuntime(project, side);
            try {
                return runtime.execute(log, step);
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                e.printStackTrace();
                log.lifecycle(e.getMessage());
                if (e instanceof RuntimeException) throw (RuntimeException)e;
                throw new RuntimeException(e);
            }
        }
    }
}
