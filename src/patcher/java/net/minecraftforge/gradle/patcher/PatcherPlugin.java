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

package net.minecraftforge.gradle.patcher;

import codechicken.diffpatch.util.PatchMode;
import com.google.common.collect.Lists;
import net.minecraftforge.gradle.common.tasks.DownloadAssets;
import net.minecraftforge.gradle.common.tasks.DownloadMCMeta;
import net.minecraftforge.gradle.common.tasks.DynamicJarExec;
import net.minecraftforge.gradle.common.tasks.ExtractMCPData;
import net.minecraftforge.gradle.common.tasks.ExtractNatives;
import net.minecraftforge.gradle.common.tasks.ExtractZip;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionFactory;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.tasks.SetupMCP;
import net.minecraftforge.gradle.patcher.tasks.CreateFakeSASPatches;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import net.minecraftforge.gradle.patcher.tasks.GenerateBinPatches;
import net.minecraftforge.gradle.common.tasks.ApplyMappings;
import net.minecraftforge.gradle.patcher.tasks.ApplyPatches;
import net.minecraftforge.gradle.common.tasks.ApplyRangeMap;
import net.minecraftforge.gradle.patcher.tasks.CreateExc;
import net.minecraftforge.gradle.common.tasks.ExtractExistingFiles;
import net.minecraftforge.gradle.common.tasks.ExtractRangeMap;
import net.minecraftforge.gradle.patcher.tasks.BakePatches;
import net.minecraftforge.gradle.patcher.tasks.FilterNewJar;
import net.minecraftforge.gradle.patcher.tasks.GeneratePatches;
import net.minecraftforge.gradle.patcher.tasks.GenerateUserdevConfig;
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PatcherPlugin implements Plugin<Project> {
    private static final String MC_DEP_CONFIG = "minecraftImplementation";

    @Override
    public void apply(@Nonnull Project project) {
        Utils.checkEnvironment();

        final PatcherExtension extension = project.getExtensions().create(PatcherExtension.class, PatcherExtension.EXTENSION_NAME, PatcherExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        final File natives_folder = project.file("build/natives/");

        Configuration mcImplementation = project.getConfigurations().maybeCreate(MC_DEP_CONFIG);
        mcImplementation.setCanBeResolved(true);
        project.getConfigurations().getByName("implementation").extendsFrom(mcImplementation);

        Jar jarConfig = (Jar) project.getTasks().getByName("jar");
        JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName("compileJava");

        TaskProvider<DownloadMCPMappings> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappings.class);
        TaskProvider<DownloadMCMeta> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<ApplyPatches> applyPatches = project.getTasks().register("applyPatches", ApplyPatches.class);
        TaskProvider<ApplyMappings> toMCPConfig = project.getTasks().register("srg2mcp", ApplyMappings.class);
        TaskProvider<ExtractZip> extractMapped = project.getTasks().register("extractMapped", ExtractZip.class);
        TaskProvider<GenerateSRG> createMcp2Srg = project.getTasks().register("createMcp2Srg", GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcp2Obf = project.getTasks().register("createMcp2Obf", GenerateSRG.class);
        TaskProvider<GenerateSRG> createSrg2Mcp = project.getTasks().register("createSrg2Mcp", GenerateSRG.class);
        TaskProvider<CreateExc> createExc = project.getTasks().register("createExc", CreateExc.class);
        TaskProvider<ExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", ExtractRangeMap.class);
        TaskProvider<ApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", ApplyRangeMap.class);
        TaskProvider<ApplyRangeMap> applyRangeBaseConfig = project.getTasks().register("applyRangeMapBase", ApplyRangeMap.class);
        TaskProvider<GeneratePatches> genPatches = project.getTasks().register("genPatches", GeneratePatches.class);
        TaskProvider<BakePatches> bakePatches = project.getTasks().register("bakePatches", BakePatches.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);
        TaskProvider<ReobfuscateJar> reobfJar = project.getTasks().register("reobfJar", ReobfuscateJar.class);
        TaskProvider<GenerateBinPatches> genJoinedBinPatches = project.getTasks().register("genJoinedBinPatches", GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genClientBinPatches = project.getTasks().register("genClientBinPatches", GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genServerBinPatches = project.getTasks().register("genServerBinPatches", GenerateBinPatches.class);
        TaskProvider<DefaultTask> genBinPatches = project.getTasks().register("genBinPatches", DefaultTask.class);
        TaskProvider<FilterNewJar> filterNew = project.getTasks().register("filterJarNew", FilterNewJar.class);
        TaskProvider<Jar> sourcesJar = project.getTasks().register("sourcesJar", Jar.class);
        TaskProvider<Jar> universalJar = project.getTasks().register("universalJar", Jar.class);
        TaskProvider<Jar> userdevJar = project.getTasks().register("userdevJar", Jar.class);
        TaskProvider<GenerateUserdevConfig> userdevConfig = project.getTasks().register("userdevConfig", GenerateUserdevConfig.class, project);
        TaskProvider<DefaultTask> release = project.getTasks().register("release", DefaultTask.class);
        TaskProvider<DefaultTask> hideLicense = project.getTasks().register(MojangLicenseHelper.HIDE_LICENSE, DefaultTask.class);
        TaskProvider<DefaultTask> showLicense = project.getTasks().register(MojangLicenseHelper.SHOW_LICENSE, DefaultTask.class);

        //Add Known repos
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.FORGE_MAVEN);
            e.metadataSources(m -> {
                m.gradleMetadata();
                m.mavenPom();
                m.artifact();
            });
        });
        new BaseRepo.Builder()
            .add(MCPRepo.create(project))
            .add(MinecraftRepo.create(project))
            .attach(project);
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.MOJANG_MAVEN);
            e.metadataSources(MetadataSources::artifact);
        });

        hideLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.hide(project, extension.getMappingChannel(), extension.getMappingVersion());
            });
        });

        showLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.show(project, extension.getMappingChannel(), extension.getMappingVersion());
            });
        });

        release.configure(task -> {
            task.dependsOn(sourcesJar, universalJar, userdevJar);
        });
        dlMappingsConfig.configure(task -> {
            task.setMappings(extension.getMappings());
        });
        extractNatives.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.setMeta(dlMCMetaConfig.get().getOutput());
            task.setOutput(natives_folder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.setMeta(dlMCMetaConfig.get().getOutput());
        });
        applyPatches.configure(task -> {
            task.setOutput(project.file("build/" + task.getName() + "/output.zip"));
            task.setRejects(project.file("build/" + task.getName() + "/rejects.zip"));
            task.setPatches(extension.patches);
            task.setPatchMode(PatchMode.ACCESS);
            if (project.hasProperty("UPDATING")) {
                task.setPatchMode(PatchMode.FUZZY);
                task.setRejects(project.file("rejects/"));
                task.setFailOnError(false);
            }
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyPatches);
            task.setInput(applyPatches.get().getOutput());
            task.setMappings(dlMappingsConfig.get().getOutput());
            task.setLambdas(false);
        });
        extractMapped.configure(task -> {
            task.dependsOn(toMCPConfig);
            task.setZip(toMCPConfig.get().getOutput());
            task.setOutput(extension.patchedSrc);
        });
        extractRangeConfig.configure(task -> {
            task.dependsOn(jarConfig);
            task.setOnlyIf(t -> extension.patches != null);
            task.addDependencies(jarConfig.getArchiveFile().get().getAsFile());
        });
        createMcp2Srg.configure(task -> {
            task.setReverse(true);
        });
        createSrg2Mcp.configure(task -> {
            task.setReverse(false);
        });
        createMcp2Obf.configure(task -> {
            task.setNotch(true);
            task.setReverse(true);
        });
        createExc.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
        });

        applyRangeConfig.configure(task -> {
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
        });
        applyRangeBaseConfig.configure(task -> {
            task.setOnlyIf(t -> extension.patches != null);
            task.dependsOn(extractRangeConfig, createMcp2Srg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createMcp2Srg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
        });
        genPatches.configure(task -> {
            task.setOnlyIf(t -> extension.patches != null);
            task.setOutput(extension.patches);
        });
        bakePatches.configure(task -> {
            task.dependsOn(genPatches);
            task.setInput(extension.patches);
            task.setOutput(new File(task.getTemporaryDir(), "output.zip"));
        });

        reobfJar.configure(task -> {
            task.dependsOn(jarConfig, dlMappingsConfig);
            task.setInput(jarConfig.getArchiveFile().get().getAsFile());
            task.setClasspath(project.getConfigurations().getByName(MC_DEP_CONFIG));
        });
        genJoinedBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSide("joined");
        });
        genClientBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSide("client");
        });
        genServerBinPatches.configure(task -> {
            task.dependsOn(reobfJar);
            task.setDirtyJar(reobfJar.get().getOutput());
            task.addPatchSet(extension.patches);
            task.setSide("server");
        });
        genBinPatches.configure(task -> {
            task.dependsOn(genJoinedBinPatches.get(), genClientBinPatches.get(), genServerBinPatches.get());
        });
        filterNew.configure(task -> {
            task.dependsOn(reobfJar);
            task.setInput(reobfJar.get().getOutput());
        });
        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        sourcesJar.configure(task -> {
            task.dependsOn(applyRangeConfig);
            task.from(project.zipTree(applyRangeConfig.get().getOutput()));
            task.getArchiveClassifier().set("sources");
        });
        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        universalJar.configure(task -> {
            task.dependsOn(filterNew);
            task.from(project.zipTree(filterNew.get().getOutput()));
            task.from(javaConv.getSourceSets().getByName("main").getResources());
            task.getArchiveClassifier().set("universal");
        });
        /*UserDev:
         * config.json
         * joined.lzma
         * sources.jar
         * patches/
         *   net/minecraft/item/Item.java.patch
         * ats/
         *   at1.cfg
         *   at2.cfg
         */
        userdevJar.configure(task -> {
            task.dependsOn(userdevConfig, genJoinedBinPatches, sourcesJar, bakePatches);
            task.setOnlyIf(t -> extension.isSrgPatches());
            task.from(userdevConfig.get().getOutput(), e -> {
                e.rename(f -> "config.json");
            });
            task.from(genJoinedBinPatches.get().getOutput(), e -> {
                e.rename(f -> "joined.lzma");
            });
            task.from(project.zipTree(bakePatches.get().getOutput()), e -> {
                e.into("patches/");
            });
            task.getArchiveClassifier().set("userdev");
        });

        final boolean doingUpdate = project.hasProperty("UPDATE_MAPPINGS");
        final String updateVersion = doingUpdate ? (String)project.property("UPDATE_MAPPINGS") : null;
        final String updateChannel = doingUpdate
            ? (project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String)project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot")
            : null;
        if (doingUpdate) {
            TaskProvider<DownloadMCPMappings> dlMappingsNew = project.getTasks().register("downloadMappingsNew", DownloadMCPMappings.class);
            dlMappingsNew.get().setMappings(updateChannel + '_' + updateVersion);

            TaskProvider<ApplyMappings> toMCPNew = project.getTasks().register("srg2mcpNew", ApplyMappings.class);
            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew.get(), applyRangeConfig.get());
                task.setInput(applyRangeConfig.get().getOutput());
                task.setMappings(dlMappingsConfig.get().getOutput());
                task.setLambdas(false);
            });

            TaskProvider<ExtractExistingFiles> extractMappedNew = project.getTasks().register("extractMappedNew", ExtractExistingFiles.class);
            extractMappedNew.configure(task -> {
                task.dependsOn(toMCPNew.get());
                task.setArchive(toMCPNew.get().getOutput());
            });

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register("updateMappings", DefaultTask.class);
            updateMappings.get().dependsOn(extractMappedNew.get());
        }

        project.afterEvaluate(p -> {
            //Add PatchedSrc to a main sourceset and build range tasks
            SourceSet mainSource = javaConv.getSourceSets().getByName("main");
            applyRangeConfig.get().setSources(mainSource.getJava().getSrcDirs().stream().filter(f -> !f.equals(extension.patchedSrc)).collect(Collectors.toList()));
            applyRangeBaseConfig.get().setSources(extension.patchedSrc);
            mainSource.java(v -> {
                v.srcDir(extension.patchedSrc);
            });

            if (doingUpdate) {
                ExtractExistingFiles extract = (ExtractExistingFiles)p.getTasks().getByName("extractMappedNew");
                for (File dir : mainSource.getJava().getSrcDirs()) {
                    if (dir.equals(extension.patchedSrc)) //Don't overwrite the patched code, re-setup the project.
                        continue;
                    extract.addTarget(dir);
                }
            }

            //mainSource.resources(v -> {
            //}); //TODO: Asset downloading, needs asset index from json.
            //javaConv.getSourceSets().stream().forEach(s -> extractRangeConfig.get().addSources(s.getJava().getSrcDirs()));
            // Only add main source, as we inject the patchedSrc into it as a sourceset.
            extractRangeConfig.get().addSources(mainSource.getJava().getSrcDirs());
            extractRangeConfig.get().addDependencies(javaCompile.getClasspath());

            if (extension.patches != null && !extension.patches.exists()) { //Auto-make folders so that gradle doesnt explode some tasks.
                extension.patches.mkdirs();
            }

            if (extension.patches != null) {
                sourcesJar.get().dependsOn(genPatches);
                sourcesJar.get().from(genPatches.get().getOutput(), e -> {
                    e.into("patches/");
                });
            }
            TaskProvider<DynamicJarExec> procConfig = extension.getProcessor() == null ? null : project.getTasks().register("postProcess", DynamicJarExec.class);

            if (extension.parent != null) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                TaskContainer tasks = extension.parent.getTasks();
                MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (mcp != null) {
                    MojangLicenseHelper.displayWarning(p, extension.getMappingChannel(), extension.getMappingVersion(), updateChannel, updateVersion);
                    SetupMCP setupMCP = (SetupMCP) tasks.getByName("setupMCP");

                    if (procConfig != null) {
                        procConfig.get().dependsOn(setupMCP);
                        procConfig.get().setInput(setupMCP.getOutput());
                        procConfig.get().setTool(extension.getProcessor().getVersion());
                        procConfig.get().setArgs(extension.getProcessor().getArgs());
                        extension.getProcessorData().forEach((key, value) -> procConfig.get().setData(key, value));
                    }

                    if (extension.cleanSrc == null) {
                        if (procConfig != null) {
                            extension.cleanSrc = procConfig.get().getOutput();
                            applyPatches.get().dependsOn(procConfig);
                            genPatches.get().dependsOn(procConfig);
                        } else {
                            extension.cleanSrc = setupMCP.getOutput();
                            applyPatches.get().dependsOn(setupMCP);
                            genPatches.get().dependsOn(setupMCP);
                        }
                    }
                    if (applyPatches.get().getBase() == null) {
                        applyPatches.get().setBase(extension.cleanSrc);
                    }
                    if (genPatches.get().getBase() == null) {
                        genPatches.get().setBase(extension.cleanSrc);
                    }

                    DownloadMCPConfig dlMCP = (DownloadMCPConfig)tasks.getByName("downloadConfig");

                    if (createMcp2Srg.get().getSrg() == null) { //TODO: Make extractMCPData macro
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register("extractSrg", ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP, dlMappingsConfig);
                        ext.get().setConfig(dlMCP.getOutput());
                        createMcp2Srg.get().setSrg(ext.get().getOutput());
                        createMcp2Srg.get().dependsOn(ext);
                    }

                    if (createExc.get().getConfig() == null) {
                        createExc.get().dependsOn(dlMCP);
                        createExc.get().setConfig(dlMCP.getOutput());
                    }

                    if (createExc.get().getSrg() == null) {
                        createExc.get().setSrg(createMcp2Srg.get().getSrg());
                        createExc.get().dependsOn(createMcp2Srg);
                    }

                    if (createExc.get().getStatics() == null) {
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register("extractStatic", ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().setConfig(dlMCP.getOutput());
                        ext.get().setKey("statics");
                        ext.get().setAllowEmpty(true);
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setStatics(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }

                    if (createExc.get().getConstructors() == null) {
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register("extractConstructors", ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().setConfig(dlMCP.getOutput());
                        ext.get().setKey("constructors");
                        ext.get().setAllowEmpty(true);
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setConstructors(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }
                } else if (patcher != null) {
                    PatcherExtension pExt = extension.parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    ApplyPatches parentApply = (ApplyPatches) tasks.getByName(applyPatches.get().getName());
                    if (procConfig != null) {
                        procConfig.get().dependsOn(parentApply);
                        procConfig.get().setInput(parentApply.getOutput());
                        procConfig.get().setTool(extension.getProcessor().getVersion());
                        procConfig.get().setArgs(extension.getProcessor().getArgs());
                        extension.getProcessorData().forEach((key, value) -> procConfig.get().setData(key, value));
                    }

                    if (extension.cleanSrc == null) {
                        if (procConfig != null) {
                            extension.cleanSrc = procConfig.get().getOutput();
                            applyPatches.get().dependsOn(procConfig);
                            genPatches.get().dependsOn(procConfig);
                        } else {
                            extension.cleanSrc = parentApply.getOutput();
                            applyPatches.get().dependsOn(parentApply);
                            genPatches.get().dependsOn(parentApply);
                        }
                    }
                    if (applyPatches.get().getBase() == null) {
                        applyPatches.get().setBase(extension.cleanSrc);
                    }
                    if (genPatches.get().getBase() == null) {
                        genPatches.get().setBase(extension.cleanSrc);
                    }

                    if (createMcp2Srg.get().getSrg() == null) {
                        ExtractMCPData extract = ((ExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createMcp2Srg.get().setSrg(extract.getOutput());
                            createMcp2Srg.get().dependsOn(extract);
                        } else {
                            GenerateSRG task = (GenerateSRG)tasks.getByName(createMcp2Srg.get().getName());
                            createMcp2Srg.get().setSrg(task.getSrg());
                            createMcp2Srg.get().dependsOn(task);
                        }
                    }

                    if (createExc.get().getConfig() == null) {
                        TaskCreateExc task = (TaskCreateExc) tasks.getByName(createExc.get().getName());
                        createExc.get().setConfig(task.getConfig());
                        createExc.get().dependsOn(task);
                    }
                    if (createExc.get().getSrg() == null) { //TODO: Make a macro for Srg/Static/Constructors
                        ExtractMCPData extract = ((ExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createExc.get().setSrg(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setSrg(task.getSrg());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getStatics() == null) {
                        ExtractMCPData extract = ((ExtractMCPData) tasks.getByName("extractStatic"));
                        if (extract != null) {
                            createExc.get().setStatics(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc) tasks.getByName(createExc.get().getName());
                            createExc.get().setStatics(task.getStatics());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getConstructors() == null) {
                        ExtractMCPData extract = ((ExtractMCPData) tasks.getByName("extractConstructors"));
                        if (extract != null) {
                            createExc.get().setConstructors(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            CreateExc task = (CreateExc) tasks.getByName(createExc.get().getName());
                            createExc.get().setConstructors(task.getConstructors());
                            createExc.get().dependsOn(task);
                        }
                    }
                    for (TaskProvider<GenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                        GenerateBinPatches pgen = (GenerateBinPatches) tasks.getByName(task.get().getName());
                        for (File patches : pgen.getPatchSets()) {
                            task.get().addPatchSet(patches);
                        }
                    }

                    filterNew.get().dependsOn(tasks.getByName("jar"));
                    filterNew.get().addBlacklist(((Jar) tasks.getByName("jar")).getArchiveFile().get().getAsFile());
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }

                if (dlMappingsConfig.get().getMappings() == null) {
                    dlMappingsConfig.get().setMappings(extension.getMappings());
                }

                for (TaskProvider<GenerateSRG> genSrg : Arrays.asList(createMcp2Srg, createSrg2Mcp, createMcp2Obf)) {
                    genSrg.get().dependsOn(dlMappingsConfig);
                    if (genSrg.get().getMappings() == null) {
                        genSrg.get().setMappings(dlMappingsConfig.get().getMappings());
                    }
                }

                if (createMcp2Obf.get().getSrg() == null) {
                    createMcp2Obf.get().setSrg(createMcp2Srg.get().getSrg());
                    createMcp2Obf.get().dependsOn(createMcp2Srg);
                }

                if (createSrg2Mcp.get().getSrg() == null) {
                    createSrg2Mcp.get().setSrg(createMcp2Srg.get().getSrg());
                    createSrg2Mcp.get().dependsOn(createMcp2Srg);
                }
            }
            Project mcp = getMcpParent(project);
            if (mcp == null) {
                throw new IllegalStateException("Could not find MCP parent project, you must specify a parent chain to MCP.");
            }
            String mcp_version = mcp.getExtensions().findByType(MCPExtension.class).getConfig().getVersion();
            project.getDependencies().add(MC_DEP_CONFIG, "net.minecraft:client:" + mcp_version + ":extra"); //Needs to be client extra, to get the data files.
            project.getDependencies().add(MC_DEP_CONFIG, MCPRepo.getMappingDep(extension.getMappingChannel(), extension.getMappingVersion())); //Add mappings so that it can be used by reflection tools.

            if (dlMCMetaConfig.get().getMCVersion() == null) {
                dlMCMetaConfig.get().setMCVersion(extension.mcVersion);
            }

            if (!extension.getAccessTransformers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcp.getTasks().getByName("setupMCP");
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createAT(mcp, extension.getAccessTransformers(), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "AccessTransformer", function);
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("ats/"));
                    userdevConfig.get().addAT(f);
                });
            }

            if (!extension.getSideAnnotationStrippers().isEmpty()) {
                SetupMCP setupMCP = (SetupMCP) mcp.getTasks().getByName("setupMCP");
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createSAS(mcp, extension.getSideAnnotationStrippers(), Collections.emptyList());
                setupMCP.addPreDecompile(project.getName() + "SideStripper", function);
                extension.getSideAnnotationStrippers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("sas/"));
                    userdevConfig.get().addSAS(f);
                });
            }

            CreateFakeSASPatches fakePatches = null;
            PatcherExtension ext = extension;
            while (ext != null) {
                if (!ext.getSideAnnotationStrippers().isEmpty()) {
                    if (fakePatches == null)
                        fakePatches = project.getTasks().register("createFakeSASPatches", CreateFakeSASPatches.class).get();
                    ext.getSideAnnotationStrippers().forEach(fakePatches::addFile);
                }
                if (ext.parent != null)
                    ext = ext.parent.getExtensions().findByType(PatcherExtension.class);
            }

            if (fakePatches != null) {
                for (TaskProvider<GenerateBinPatches> task : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                    task.get().dependsOn(fakePatches);
                    task.get().addPatchSet(fakePatches.getOutput());
                }
            }

            applyRangeConfig.get().setExcFiles(extension.getExcs());
            applyRangeBaseConfig.get().setExcFiles(extension.getExcs());

            if (!extension.getExtraMappings().isEmpty()) {
                extension.getExtraMappings().stream().filter(e -> e instanceof File).map(e -> (File) e).forEach(e -> {
                    userdevJar.get().from(e, c -> c.into("srgs/"));
                    userdevConfig.get().addSRG(e);
                });
                extension.getExtraMappings().stream().filter(e -> e instanceof String).map(e -> (String) e).forEach(e -> userdevConfig.get().addSRGLine(e));
            }

            //UserDev Config Default Values
            if (userdevConfig.get().getTool() == null) {
                userdevConfig.get().setTool("net.minecraftforge:binarypatcher:" + genJoinedBinPatches.get().getResolvedVersion() + ":fatjar");
                userdevConfig.get().setArguments("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");
            }
            if (userdevConfig.get().getUniversal() == null) {
                userdevConfig.get().setUniversal(project.getGroup().toString() + ':' + universalJar.get().getArchiveBaseName().getOrNull() + ':' + project.getVersion() + ':' + universalJar.get().getArchiveClassifier().getOrNull() + '@' + universalJar.get().getArchiveExtension().getOrNull());
            }
            if (userdevConfig.get().getSource() == null) {
                userdevConfig.get().setSource(project.getGroup().toString() + ':' + sourcesJar.get().getArchiveBaseName().getOrNull() + ':' + project.getVersion() + ':' + sourcesJar.get().getArchiveClassifier().getOrNull() + '@' + sourcesJar.get().getArchiveExtension().getOrNull());
            }
            if (!"a/".contentEquals(genPatches.get().getOriginalPrefix())) {
                userdevConfig.get().setPatchesOriginalPrefix(genPatches.get().getOriginalPrefix());
            }
            if (!"b/".contentEquals(genPatches.get().getModifiedPrefix())) {
                userdevConfig.get().setPatchesModifiedPrefix(genPatches.get().getModifiedPrefix());
            }
            if (procConfig != null) {
                userdevJar.get().dependsOn(procConfig);
                userdevConfig.get().setProcessor(extension.getProcessor());
                extension.getProcessorData().forEach((key, value) -> {
                    userdevJar.get().from(value, c -> c.into("processor/"));
                    userdevConfig.get().addProcessorData(key, value);
                });
            }
            userdevConfig.get().setNotchObf(extension.getNotchObf());

            //Allow generation of patches to skip S2S. For in-dev patches while the code doesn't compile.
            if (extension.isSrgPatches()) {
                genPatches.get().dependsOn(applyRangeBaseConfig);
                genPatches.get().setModified(applyRangeBaseConfig.get().getOutput());
            } else {
                //Remap the 'clean' with out mappings.
                ApplyMappings toMCPClean = project.getTasks().register("srg2mcpClean", ApplyMappings.class).get();
                toMCPClean.dependsOn(dlMappingsConfig, Lists.newArrayList(applyPatches.get().getDependsOn()));
                toMCPClean.setInput(applyPatches.get().getBase());
                toMCPClean.setMappings(dlMappingsConfig.get().getOutput());
                toMCPClean.setLambdas(false);

                //Zip up the current working folder as genPatches takes a zip
                Zip dirtyZip = project.getTasks().register("patchedZip", Zip.class).get();
                dirtyZip.from(extension.patchedSrc);
                dirtyZip.getArchiveFileName().set("output.zip");
                dirtyZip.getDestinationDirectory().set(project.file("build/" + dirtyZip.getName() + "/"));

                //Fixup the inputs.
                applyPatches.get().setDependsOn(Lists.newArrayList(toMCPClean));
                applyPatches.get().setBase(toMCPClean.getOutput());
                genPatches.get().setDependsOn(Lists.newArrayList(toMCPClean, dirtyZip));
                genPatches.get().setBase(toMCPClean.getOutput());
                genPatches.get().setModified(dirtyZip.getArchiveFile().get().getAsFile());
            }

            {
                String suffix = extension.getNotchObf() ? mcp_version.substring(0, mcp_version.lastIndexOf('-')) : mcp_version + ":srg";
                File client = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + suffix, true);
                File server = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + suffix, true);
                File joined = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + mcp_version + (extension.getNotchObf() ? "" : ":srg"), true);

                if (client == null || !client.exists())
                    throw new RuntimeException("Something horrible happenend, client " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");
                if (server == null || !server.exists())
                    throw new RuntimeException("Something horrible happenend, server " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");
                if (joined == null || !joined.exists())
                    throw new RuntimeException("Something horrible happenend, joined " + (extension.getNotchObf() ? "notch" : "SRG") + " jar not found");

                TaskProvider<GenerateSRG> srg = extension.getNotchObf() ? createMcp2Obf : createMcp2Srg;
                reobfJar.get().dependsOn(srg);
                reobfJar.get().setSrg(srg.get().getOutput());
                //TODO: Extra SRGs, I don't think this is needed tho...

                genJoinedBinPatches.get().dependsOn(srg);
                genJoinedBinPatches.get().setSrg(srg.get().getOutput());
                genJoinedBinPatches.get().setCleanJar(joined);

                genClientBinPatches.get().dependsOn(srg);
                genClientBinPatches.get().setSrg(srg.get().getOutput());
                genClientBinPatches.get().setCleanJar(client);

                genServerBinPatches.get().dependsOn(srg);
                genServerBinPatches.get().setSrg(srg.get().getOutput());
                genServerBinPatches.get().setCleanJar(server);

                filterNew.get().dependsOn(srg);
                filterNew.get().setSrg(srg.get().getOutput());
                filterNew.get().addBlacklist(joined);
            }

            Map<String, String> tokens = new HashMap<>();

            try {
                // Check meta exists
                if (!dlMCMetaConfig.get().getOutput().exists()) {
                    // Force download meta
                    dlMCMetaConfig.get().downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(dlMCMetaConfig.get().getOutput(), VersionJson.class);

                tokens.put("asset_index", json.assetIndex.id);
            } catch (IOException e) {
                e.printStackTrace();

                // Fallback to MC version
                tokens.put("asset_index", extension.getMcVersion());
            }

            extension.getRuns().forEach(runConfig -> runConfig.tokens(tokens));
            Utils.createRunConfigTasks(extension, extractNatives.get(), downloadAssets.get(), createSrg2Mcp.get());
        });
    }


    private Project getMcpParent(Project project) {
        final PatcherExtension extension = project.getExtensions().findByType(PatcherExtension.class);
        if (extension == null || extension.parent == null) {
            return null;
        }
        MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
        PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);
        if (mcp != null) {
            return extension.parent;
        } else if (patcher != null) {
            return getMcpParent(extension.parent);
        }
        return null;
    }

}
