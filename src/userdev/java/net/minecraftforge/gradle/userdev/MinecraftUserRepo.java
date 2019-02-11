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

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.repository.Repository;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.cloudbees.diff.PatchException;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.minecraftforge.gradle.common.config.Config;
import net.minecraftforge.gradle.common.config.UserdevConfigV1;
import net.minecraftforge.gradle.common.diff.ContextualPatch;
import net.minecraftforge.gradle.common.diff.ContextualPatch.PatchReport;
import net.minecraftforge.gradle.common.diff.HunkReport;
import net.minecraftforge.gradle.common.diff.PatchFile;
import net.minecraftforge.gradle.common.diff.ZipContext;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.HashFunction;
import net.minecraftforge.gradle.common.util.HashStore;
import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.McpNames;
import net.minecraftforge.gradle.common.util.POMBuilder;
import net.minecraftforge.gradle.common.util.RunConfig;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.util.MCPRuntime;
import net.minecraftforge.gradle.mcp.util.MCPWrapper;
import net.minecraftforge.gradle.userdev.tasks.AccessTransformJar;
import net.minecraftforge.gradle.userdev.tasks.ApplyBinPatches;
import net.minecraftforge.gradle.userdev.tasks.ApplyMCPFunction;
import net.minecraftforge.gradle.userdev.tasks.RenameJar;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.internal.OverlappingOutputs;
import org.gradle.api.internal.TaskExecutionHistory;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.annotation.Nullable;

public class MinecraftUserRepo extends BaseRepo {
    private static final boolean CHANGING_USERDEV = true; //Used when testing to update the userdev cache every 30 seconds.
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

    @Override
    protected File getCacheRoot() {
        if (this.AT_HASH == null)
            return super.getCacheRoot();
        return project.file("build/fg_cache/");
    }

    public void validate(Configuration cfg, Map<String, RunConfig> runs, File natives, File assets) {
        getParents();
        if (mcp == null)
            throw new IllegalStateException("Invalid minecraft dependency: " + GROUP + ":" + NAME + ":" + VERSION);
        ExtraPropertiesExtension ext = project.getExtensions().getExtraProperties();
        ext.set("MC_VERSION", mcp.getMCVersion());
        ext.set("MCP_VERSION", mcp.getArtifact().getVersion());

        //Maven POMs can't self-reference apparently, so we have to add any deps that are self referential.
        Patcher patcher = parent;
        while (patcher != null) {
            patcher.getLibraries().stream().map(Artifact::from)
            .filter(e -> GROUP.equals(e.getGroup()) && NAME.equals(e.getName()))
            .forEach(e -> {
                String dep = getDependencyString();
                if (e.getClassifier() != null)
                    dep += ":" + e.getClassifier();
                if (e.getExtension() != null && !"jar".equals(e.getExtension()))
                    dep += "@" + e.getExtension();

                debug("    New Self Dep: " + dep);
                ExternalModuleDependency _dep = (ExternalModuleDependency)project.getDependencies().create(dep);
                if (CHANGING_USERDEV) {
                    _dep.setChanging(true);
                }
                cfg.getDependencies().add(_dep);
            });
            patcher = patcher.getParent();
        }

        if (parent != null && parent.getConfig().runs != null) { // There might be no patchers to parent, so it may only be a vanilla mcp dependency
            Map<String, String> vars = new HashMap<>();
            vars.put("assets_root", assets.getAbsolutePath());
            vars.put("natives", natives.getAbsolutePath());
            vars.put("mc_version", mcp.getMCVersion());
            vars.put("mcp_version", mcp.getArtifact().getVersion());
            vars.put("mcp_mappings", MAPPING);

            parent.getConfig().runs.forEach((name, dev) -> {
                runs.computeIfAbsent(name, k -> new RunConfig()).merge(dev, false, vars);
            });
        }
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
            String artifact = isPatcher ? (GROUP + ":" + NAME +":" + VERSION + ':' + "userdev") :
                                        ("de.oceanlabs.mcp:mcp_config:" + VERSION + "@zip");
            boolean patcher = isPatcher;
            Patcher last = null;
            while (artifact != null) {
                debug("    Parent: " + artifact);
                File dep = MavenArtifactDownloader.manual(project, artifact, CHANGING_USERDEV);
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
        String ext = artifact.getExtension();

        debug("  " + REPO_NAME + " Request: " + artifact.getGroup() + ":" + artifact.getName() + ":" + version + ":" + classifier + "@" + ext + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            return findPom(mappings, rand);
        } else {
            switch (classifier) {
                case "":        return findRaw(mappings);
                case "sources": return findSource(mappings, true);
                default:        return findExtraClassifier(mappings, classifier, ext);
            }
        }
    }

    private HashStore commonHash(File mapping) {
        getParents();
        HashStore ret = new HashStore(this.getCacheRoot());
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
        return MavenArtifactDownloader.manual(project, desc, CHANGING_USERDEV);
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

            //builder.dependencies().add(rand + GROUP + ':' + NAME + ':' + getVersionWithAT(mapping), "compile"); //Normal poms dont reference themselves...
            builder.dependencies().add("net.minecraft:client:" + mcp.getMCVersion() + ":extra", "compile"); //Client as that has all deps as external list
            builder.dependencies().add("net.minecraft:client:" + mcp.getMCVersion() + ":data", "compile");
            mcp.getLibraries().forEach(e -> builder.dependencies().add(e, "compile"));

            if (mapping != null) {
                int idx = mapping.lastIndexOf('_');
                String channel = mapping.substring(0, idx);
                String version = mapping.substring(idx + 1);
                builder.dependencies().add("de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip", "compile"); //Runtime?
            }

            Patcher patcher = parent;
            while (patcher != null) {
                for (String lib : patcher.getLibraries()) {
                    Artifact af = Artifact.from(lib);
                    //Gradle only allows one dependency with the same group:name. So if we depend on any claissified deps, repackage it ourselves.
                    // Gradle also seems to not be able to reference itself. So we add it elseware.
                    if (GROUP.equals(af.getGroup()) && NAME.equals(af.getName()) && VERSION.equals(af.getVersion())) {
                        builder.dependencies().add(rand + GROUP, NAME, getVersionWithAT(mapping), af.getClassifier(), af.getExtension(), "compile");
                    } else {
                        builder.dependencies().add(lib, "compile");
                    }
                }
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

        File recomp = findRecomp(mapping, false);
        if (recomp != null)
            return recomp;

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
            File joined = MavenArtifactDownloader.generate(project, "net.minecraft:" + (isPatcher? "joined" : NAME) + ":" + mcp.getVersion() + ":srg", true); //Download vanilla in srg name
            if (joined == null || !joined.exists()) {
                project.getLogger().error("MinecraftUserRepo: Failed to get Minecraft Joined SRG. Should not be possible.");
                return null;
            }

            //Gather vanilla packages, so we can only inject the proper package-info classes.
            Set<String> packages = new HashSet<>();
            try (ZipFile tmp = new ZipFile(joined)) {
                packages = tmp.stream()
                .map(ZipEntry::getName)
                .filter(e -> e.endsWith(".class"))
                .map(e -> e.indexOf('/') == -1 ? "" : e.substring(0, e.lastIndexOf('/')))
                .collect(Collectors.toSet());
            }

            if (parent == null) { //Raw minecraft
                srged = joined;
            } else { // Needs binpatches
                File binpatched = cacheRaw("binpatched", "jar");

                //Apply bin patches to vanilla
                ApplyBinPatches apply = project.getTasks().create("_" + new Random().nextInt() + "_applyBinPatches", ApplyBinPatches.class);
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

                    copyResources(zip, added, true);
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

            //Build and inject MCP injected sources
            File inject_src = cacheRaw("inject_src", "jar");
            try (ZipInputStream zin = new ZipInputStream(new FileInputStream(mcp.getZip()));
                 ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(inject_src)) ) {
                String prefix = mcp.wrapper.getConfig().getData("inject");
                String template = null;
                ZipEntry entry = null;
                while ((entry = zin.getNextEntry()) != null) {
                    if (!entry.getName().startsWith(prefix) || entry.isDirectory())
                        continue;
                    String name = entry.getName().substring(prefix.length());
                    if ("package-info-template.java".equals(name)) {
                        template = new String(IOUtils.toByteArray(zin), StandardCharsets.UTF_8);
                    } else {
                        ZipEntry _new = new ZipEntry(name);
                        _new.setTime(0);
                        zos.putNextEntry(_new);
                        IOUtils.copy(zin, zos);
                        zos.closeEntry();
                    }
                }

                if (template != null) {
                    for (String pkg : packages) {
                        ZipEntry _new = new ZipEntry(pkg + "/package-info.java");
                        _new.setTime(0);
                        zos.putNextEntry(_new);
                        zos.write(template.replace("{PACKAGE}", pkg.replace("/", ".")).getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                }
            }

            File compiled = compileJava(inject_src, mcinject);
            if (compiled == null)
                return null;

            File injected = cacheRaw("injected", "jar");
            //Combine mci, and our recompiled MCP injected classes.
            try (ZipInputStream zmci = new ZipInputStream(new FileInputStream(mcinject));
                 ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(injected))) {
                ZipEntry entry = null;
                while ((entry = zmci.getNextEntry()) != null) {
                    ZipEntry _new = new ZipEntry(entry.getName());
                    _new.setTime(0);
                    zout.putNextEntry(_new);
                    IOUtils.copy(zmci, zout);
                    zout.closeEntry();
                }
                Files.walkFileTree(compiled.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try (InputStream fin = Files.newInputStream(file)) {
                            ZipEntry _new = new ZipEntry(compiled.toPath().relativize(file).toString().replace('\\', '/'));
                            _new.setTime(0);
                            zout.putNextEntry(_new);
                            IOUtils.copy(fin, zout);
                            zout.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            if (hasAts) {
                if (bin.exists()) bin.delete(); // AT lib throws an exception if output file already exists

                AccessTransformJar at = project.getTasks().create("_atJar_"+ new Random().nextInt() + "_", AccessTransformJar.class);
                at.setInput(injected);
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
                FileUtils.copyFile(injected, bin);
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
                rename.setInput(injected);
                rename.setOutput(bin);
                rename.setMappings(findSrgToMcp(mapping, names));
                rename.apply();
            }

            Utils.updateHash(bin, HashFunction.SHA1);
            cache.save();
        }
        return bin;
    }

    private void copyResources(ZipOutputStream zip, Set<String> added, boolean includeClasses) throws IOException {
        Map<String, List<String>> servicesLists = new HashMap<>();
        Predicate<String> filter = (name) -> added.contains(name) || (!includeClasses && name.endsWith(".class")) || (name.startsWith("META-INF") && (name.endsWith(".DSA") || name.endsWith(".SF")));
        // Walk parents and combine from bottom up so we get any overridden files.
        Patcher patcher = parent;
        while (patcher != null) {
            if (patcher.getUniversal() != null) {
                try (ZipInputStream zin = new ZipInputStream(new FileInputStream(patcher.getUniversal()))) {
                    ZipEntry entry;
                    while ((entry = zin.getNextEntry()) != null) {
                        String name = entry.getName();
                        if (filter.test(name))
                            continue;

                        if (name.startsWith("META-INF/services/") && !entry.isDirectory()) {
                            List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                            if (existing.size() > 0) existing.add("");
                            existing.add(String.format("# %s - %s", patcher.artifact, patcher.getUniversal().getCanonicalFile().getName()));
                            existing.addAll(IOUtils.readLines(zin));
                        } else {
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
                        if (filter.test(name))
                            continue;

                        if (name.startsWith("META-INF/services/") && !entry.isDirectory()) {
                            List<String> existing = servicesLists.computeIfAbsent(name, k -> new ArrayList<>());
                            if (existing.size() > 0) existing.add("");
                            existing.add(String.format("# %s - %s", patcher.artifact, patcher.getZip().getCanonicalFile().getName()));
                            existing.addAll(IOUtils.readLines(zin));
                        } else {
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

        for(Map.Entry<String, List<String>> kv : servicesLists.entrySet()) {
            String name = kv.getKey();
            ZipEntry _new = new ZipEntry(name);
            _new.setTime(0);
            zip.putNextEntry(_new);
            IOUtils.writeLines(kv.getValue(), "\n", zip);
            added.add(name);
        }
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

    private File findDecomp(boolean generate) throws IOException {
        HashStore cache = commonHash(null);

        File decomp = cacheAT("decomp", "jar");
        debug("    Finding Decomp: " + decomp);
        cache.load(cacheAT("decomp", "jar.input"));

        if ((!cache.isSame() && (cache.exists() || generate)) || (!decomp.exists() && generate)) {
            File output = mcp.getStepOutput(isPatcher ? "joined" : NAME, null);
            FileUtils.copyFile(output, decomp);
            cache.save();
            Utils.updateHash(decomp, HashFunction.SHA1);
        }
        return decomp.exists() ? decomp : null;
    }

    private File findPatched(boolean generate) throws IOException {
        File decomp = findDecomp(generate);
        if (decomp == null) return null;
        if (parent == null) return decomp;

        HashStore cache = commonHash(null).add("decomp", decomp);

        File patched = cacheAT("patched", "jar");
        debug("    Finding patched: " + decomp);
        cache.load(cacheAT("patched", "jar.input"));

        if ((!cache.isSame() && (cache.exists() || generate)) || (!patched.exists() && generate)) {
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
                    added.addAll(context.save(zout));

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
        return patched.exists() ? patched : null;
    }

    private File findSource(String mapping, boolean generate) throws IOException {
        File patched = findPatched(generate);
        if (patched == null) return null;
        if (mapping == null) return patched;

        File names = findMapping(mapping);
        if (names == null) return null;

        HashStore cache = commonHash(names);

        File sources = cacheMapped(mapping, "sources", "jar");
        debug("    Finding Source: " + sources);
        cache.load(cacheMapped(mapping, "sources", "jar.input"));
        if ((!cache.isSame() && (cache.exists() || generate)) || (!sources.exists() && generate)) {
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
        return sources.exists() ? sources : null;
    }

    private File findRecomp(String mapping, boolean generate) throws IOException {
        File source = findSource(mapping, generate);
        File names = findMapping(mapping);
        if (source == null || names == null)
            return null;

        HashStore cache = commonHash(names);
        cache.add("source", source);
        cache.load(cacheMapped(mapping, "recomp", "jar.input"));

        File recomp = cacheMapped(mapping, "recomp", "jar");

        if (!cache.isSame() || !recomp.exists()) {
            debug("  Finding recomp: " + cache.isSame() + " " + recomp);

            File compiled = compileJava(source);
            if (compiled == null)
                return null;

            Set<String> added = new HashSet<>();
            try (ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(recomp))) {
                //Add all compiled code
                Files.walkFileTree(compiled.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        try (InputStream fin = Files.newInputStream(file)) {
                            String name = compiled.toPath().relativize(file).toString().replace('\\', '/');
                            ZipEntry _new = new ZipEntry(name);
                            _new.setTime(0);
                            zout.putNextEntry(_new);
                            IOUtils.copy(fin, zout);
                            zout.closeEntry();
                            added.add(name);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
                copyResources(zout, added, false);
            }
            Utils.updateHash(recomp, HashFunction.SHA1);
            cache.save();
        }
        return recomp;
    }

    private File findExtraClassifier(String mapping, String classifier, String extension) throws IOException {
        //These are extra classifiers shipped by the normal repo. Except that gradle doesn't allow two artifacts with the same group:name
        // but different version. For good reason. So we change their version to ours. And provide them as is.

        File target = cacheMapped(mapping, classifier, extension);
        debug("    Finding Classified: " + target);

        File original = MavenArtifactDownloader.manual(project, Artifact.from(GROUP, NAME, VERSION, classifier, extension).getDescriptor(), CHANGING_USERDEV);
        HashStore cache = commonHash(null); //TODO: Remap from SRG?
        if (original != null)
            cache.add("original", original);

        cache.load(cacheMapped(mapping, classifier, extension + ".input"));
        if (!cache.isSame() || !target.exists()) {
            if (original == null) {
                debug("      Failed to download original artifact.");
                return null;
            }
            try {
                FileUtils.copyFile(original, target);
            } catch (IOException e) { //Something screwed up, nuke the file incase its invalid and return nothing.
                if (target.exists())
                    target.delete();
            }
            cache.save();
            Utils.updateHash(target, HashFunction.SHA1);
        }
        return target;
    }

    private int compileTaskCount = 1;
    private File compileJava(File source, File... extraDeps) {
        JavaCompile compile = project.getTasks().create("_compileJava_" + compileTaskCount++, JavaCompile.class);
        try {
            File output = project.file("build/" + compile.getName() + "/");
            if (output.exists()) {
                FileUtils.cleanDirectory(output);
            } else {
                // Due to the weird way that we invoke JavaCompile,
                // we need to ensure that the output directory already exists
                output.mkdirs();
            }
            Configuration cfg = project.getConfigurations().create(compile.getName());
            List<String> deps = new ArrayList<>();
            deps.add("net.minecraft:client:" + mcp.getMCVersion() + ":extra");
            deps.add("net.minecraft:client:" + mcp.getMCVersion() + ":data");
            deps.addAll(mcp.getLibraries());
            Patcher patcher = parent;
            while (patcher != null) {
                deps.addAll(patcher.getLibraries());
                patcher = patcher.getParent();
            }
            deps.forEach(dep -> cfg.getDependencies().add(project.getDependencies().create(dep)));
            Set<File> files = cfg.resolve();
            for (File ext : extraDeps)
                files.add(ext);
            compile.setClasspath(project.files(files));
            if (parent != null) {
                compile.setSourceCompatibility(parent.getConfig().getSourceCompatibility());
                compile.setTargetCompatibility(parent.getConfig().getTargetCompatibility());
            } else {
                final JavaPluginConvention java = project.getConvention().findPlugin(JavaPluginConvention.class);
                if (java != null) {
                    compile.setSourceCompatibility(java.getSourceCompatibility().toString());
                    compile.setTargetCompatibility(java.getTargetCompatibility().toString());
                }
            }
            compile.setDestinationDir(output);
            compile.setSource(source.isDirectory() ? project.fileTree(source) : project.zipTree(source));

            // What follows is a horrible hack to allow us to call JavaCompile
            // from our dependency resolver.
            // As described in https://github.com/MinecraftForge/ForgeGradle/issues/550,
            // invoking Gradle tasks in the normal way can lead to deadlocks
            // when done from a dependency resolver.

            // To avoid these issues, we invoke the 'compile' method on JavaCompile
            // using reflection.

            // Normally, the output history is set by Gradle. Since we're bypassing
            // the normal gradle task infrastructure, we need to do it ourselves.
            compile.getOutputs().setHistory(new TaskExecutionHistory() {

                @Override
                public Set<File> getOutputFiles() {
                    // We explicitly clear the output directory
                    // ourselves, so it's okay that this is totally wrong.
                    return Sets.newHashSet();
                }

                @Nullable
                @Override
                public OverlappingOutputs getOverlappingOutputs() {
                    return null;
                }

                @Nullable
                @Override
                public OriginTaskExecutionMetadata getOriginExecutionMetadata() {
                    return null;
                }
            });

            Method compileMethod = JavaCompile.class.getDeclaredMethod("compile");
            compileMethod.setAccessible(true);
            compileMethod.invoke(compile);
            return output;
        } catch (Exception e) { //Compile errors...?
            e.printStackTrace();
            return null;
        } finally {
            project.getTasks().remove(compile);
        }
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
                    universal = MavenArtifactDownloader.manual(project, config.universal, CHANGING_USERDEV);
                    if (universal == null)
                        throw new IllegalStateException("Invalid patcher dependency, could not resolve universal: " + universal);
                } else {
                    universal = null;
                }

                if (config.sources != null) {
                    sources = MavenArtifactDownloader.manual(project, config.sources, CHANGING_USERDEV);
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
