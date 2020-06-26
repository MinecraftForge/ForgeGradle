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

import com.google.common.collect.Lists;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.DownloadMCMeta;
import net.minecraftforge.gradle.common.task.DynamicJarExec;
import net.minecraftforge.gradle.common.task.ExtractMCPData;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.task.ExtractZip;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.AccessTransformerFunction;
import net.minecraftforge.gradle.mcp.function.SideAnnotationStripperFunction;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.patcher.task.CreateFakeSASPatches;
import net.minecraftforge.gradle.mcp.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.patcher.task.GenerateBinPatches;
import net.minecraftforge.gradle.common.task.TaskApplyMappings;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.common.task.TaskApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskCreateExc;
import net.minecraftforge.gradle.common.task.TaskExtractExistingFiles;
import net.minecraftforge.gradle.common.task.TaskExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskFilterNewJar;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import net.minecraftforge.gradle.patcher.task.TaskGenerateUserdevConfig;
import net.minecraftforge.gradle.patcher.task.TaskReobfuscateJar;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
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
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class PatcherPlugin implements Plugin<Project> {
    private static final String MC_DEP_CONFIG = "compile";

    @Override
    public void apply(@Nonnull Project project) {
        Utils.checkJavaVersion();

        final PatcherExtension extension = project.getExtensions().create(PatcherExtension.class, PatcherExtension.EXTENSION_NAME, PatcherExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
        final File natives_folder = project.file("build/natives/");

        Jar jarConfig = (Jar) project.getTasks().getByName("jar");
        JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName("compileJava");

        TaskProvider<DownloadMCPMappingsTask> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<DownloadMCMeta> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<TaskApplyMappings> toMCPConfig = project.getTasks().register("srg2mcp", TaskApplyMappings.class);
        TaskProvider<ExtractZip> extractMapped = project.getTasks().register("extractMapped", ExtractZip.class);
        TaskProvider<GenerateSRG> createMcp2Srg = project.getTasks().register("createMcp2Srg", GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcp2Obf = project.getTasks().register("createMcp2Obf", GenerateSRG.class);
        TaskProvider<GenerateSRG> createSrg2Mcp = project.getTasks().register("createSrg2Mcp", GenerateSRG.class);
        TaskProvider<TaskCreateExc> createExc = project.getTasks().register("createExc", TaskCreateExc.class);
        TaskProvider<TaskExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", TaskExtractRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", TaskApplyRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeBaseConfig = project.getTasks().register("applyRangeMapBase", TaskApplyRangeMap.class);
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);
        TaskProvider<TaskReobfuscateJar> reobfJar = project.getTasks().register("reobfJar", TaskReobfuscateJar.class);
        TaskProvider<GenerateBinPatches> genJoinedBinPatches = project.getTasks().register("genJoinedBinPatches", GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genClientBinPatches = project.getTasks().register("genClientBinPatches", GenerateBinPatches.class);
        TaskProvider<GenerateBinPatches> genServerBinPatches = project.getTasks().register("genServerBinPatches", GenerateBinPatches.class);
        TaskProvider<DefaultTask> genBinPatches = project.getTasks().register("genBinPatches", DefaultTask.class);
        TaskProvider<TaskFilterNewJar> filterNew = project.getTasks().register("filterJarNew", TaskFilterNewJar.class);
        TaskProvider<Jar> sourcesJar = project.getTasks().register("sourcesJar", Jar.class);
        TaskProvider<Jar> universalJar = project.getTasks().register("universalJar", Jar.class);
        TaskProvider<Jar> userdevJar = project.getTasks().register("userdevJar", Jar.class);
        TaskProvider<TaskGenerateUserdevConfig> userdevConfig = project.getTasks().register("userdevConfig", TaskGenerateUserdevConfig.class, project);
        TaskProvider<DefaultTask> release = project.getTasks().register("release", DefaultTask.class);

        //Add Known repos
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.FORGE_MAVEN);
        });
        new BaseRepo.Builder()
            .add(MCPRepo.create(project))
            .add(MinecraftRepo.create(project))
            .attach(project);
        project.getRepositories().maven(e -> {
            e.setUrl(Utils.MOJANG_MAVEN);
            e.metadataSources(src -> src.artifact());
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
        applyConfig.configure(task -> {
            task.setPatches(extension.patches);
            if (project.hasProperty("UPDATING")) {
                task.setFailOnErrors(false);
                task.setRejects(project.file("rejects/"));
            }
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyConfig);
            task.setInput(applyConfig.get().getOutput());
            task.setMappings(dlMappingsConfig.get().getOutput());
        });
        extractMapped.configure(task -> {
            task.dependsOn(toMCPConfig);
            task.setZip(toMCPConfig.get().getOutput());
            task.setOutput(extension.patchedSrc);
        });
        extractRangeConfig.configure(task -> {
            task.dependsOn(jarConfig);
            task.setOnlyIf(t -> extension.patches != null);
            task.addDependencies(jarConfig.getArchivePath());
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
        genConfig.configure(task -> {
            task.setOnlyIf(t -> extension.patches != null);
            task.setPatches(extension.patches);
        });

        reobfJar.configure(task -> {
            task.dependsOn(jarConfig, dlMappingsConfig);
            task.setInput(jarConfig.getArchivePath());
            task.setClasspath(project.getConfigurations().getByName(MC_DEP_CONFIG));
            //TODO: Extra SRGs
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
            task.setClassifier("sources");
        });
        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        universalJar.configure(task -> {
            task.dependsOn(filterNew);
            task.from(project.zipTree(filterNew.get().getOutput()));
            task.from(javaConv.getSourceSets().getByName("main").getResources());
            task.setClassifier("universal");
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
            task.dependsOn(userdevConfig, genJoinedBinPatches, sourcesJar, genConfig);
            task.setOnlyIf(t -> extension.isSrgPatches());
            task.from(userdevConfig.get().getOutput(), e -> {
                e.rename(f -> "config.json");
            });
            task.from(genJoinedBinPatches.get().getOutput(), e -> {
                e.rename(f -> "joined.lzma");
            });
            task.from(genConfig.get().getPatches(), e -> {
                e.into("patches/");
            });
            task.setClassifier("userdev");
        });

        final boolean doingUpdate = project.hasProperty("UPDATE_MAPPINGS");
        if (doingUpdate) {
            String version = (String) project.property("UPDATE_MAPPINGS");
            String channel = project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String) project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot";

            TaskProvider<DownloadMCPMappingsTask> dlMappingsNew = project.getTasks().register("downloadMappingsNew", DownloadMCPMappingsTask.class);
            dlMappingsNew.get().setMappings(channel + '_' + version);

            TaskProvider<TaskApplyMappings> toMCPNew = project.getTasks().register("srg2mcpNew", TaskApplyMappings.class);
            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew.get(), applyRangeConfig.get());
                task.setInput(applyRangeConfig.get().getOutput());
                task.setMappings(dlMappingsConfig.get().getOutput());
            });

            TaskProvider<TaskExtractExistingFiles> extractMappedNew = project.getTasks().register("extractMappedNew", TaskExtractExistingFiles.class);
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
                TaskExtractExistingFiles extract = (TaskExtractExistingFiles)p.getTasks().getByName("extractMappedNew");
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
                sourcesJar.get().dependsOn(genConfig);
                sourcesJar.get().from(genConfig.get().getPatches(), e -> {
                    e.into("patches/");
                });
            }
            TaskProvider<DynamicJarExec> procConfig = extension.getProcessor() == null ? null : project.getTasks().register("postProcess", DynamicJarExec.class);

            if (extension.parent != null) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                TaskContainer tasks = extension.parent.getTasks();
                MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (mcp != null) {
                    SetupMCPTask setupMCP = (SetupMCPTask) tasks.getByName("setupMCP");

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
                            applyConfig.get().dependsOn(procConfig);
                            genConfig.get().dependsOn(procConfig);
                        } else {
                            extension.cleanSrc = setupMCP.getOutput();
                            applyConfig.get().dependsOn(setupMCP);
                            genConfig.get().dependsOn(setupMCP);
                        }
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
                    }

                    DownloadMCPConfigTask dlMCP = (DownloadMCPConfigTask)tasks.getByName("downloadConfig");

                    if (createMcp2Srg.get().getSrg() == null) { //TODO: Make extractMCPData macro
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register("extractSrg", ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().setConfig(dlMCP.getOutput());
                        createMcp2Srg.get().setSrg(ext.get().getOutput());
                        createMcp2Srg.get().dependsOn(ext);
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
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setStatics(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }

                    if (createExc.get().getConstructors() == null) {
                        TaskProvider<ExtractMCPData> ext = project.getTasks().register("extractConstructors", ExtractMCPData.class);
                        ext.get().dependsOn(dlMCP);
                        ext.get().setConfig(dlMCP.getOutput());
                        ext.get().setKey("constructors");
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setConstructors(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }
                } else if (patcher != null) {
                    PatcherExtension pExt = extension.parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    TaskApplyPatches parentApply = (TaskApplyPatches) tasks.getByName(applyConfig.get().getName());
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
                            applyConfig.get().dependsOn(procConfig);
                            genConfig.get().dependsOn(procConfig);
                        } else {
                            extension.cleanSrc = parentApply.getOutput();
                            applyConfig.get().dependsOn(parentApply);
                            genConfig.get().dependsOn(parentApply);
                        }
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
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

                    if (createExc.get().getSrg() == null) { //TODO: Make a macro for Srg/Static/Constructors
                        ExtractMCPData extract = ((ExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createExc.get().setSrg(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateExc task = (TaskCreateExc)tasks.getByName(createExc.get().getName());
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
                            TaskCreateExc task = (TaskCreateExc) tasks.getByName(createExc.get().getName());
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
                            TaskCreateExc task = (TaskCreateExc) tasks.getByName(createExc.get().getName());
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
                    filterNew.get().addBlacklist(((Jar) tasks.getByName("jar")).getArchivePath());
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
                SetupMCPTask setupMCP = (SetupMCPTask) mcp.getTasks().getByName("setupMCP");
                setupMCP.addPreDecompile(project.getName() + "AccessTransformer", new AccessTransformerFunction(mcp, extension.getAccessTransformers()));
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.get().from(f, e -> e.into("ats/"));
                    userdevConfig.get().addAT(f);
                });
            }

            if (!extension.getSideAnnotationStrippers().isEmpty()) {
                SetupMCPTask setupMCP = (SetupMCPTask) mcp.getTasks().getByName("setupMCP");
                setupMCP.addPreDecompile(project.getName() + "SideStripper", new SideAnnotationStripperFunction(mcp, extension.getSideAnnotationStrippers()));
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
                userdevConfig.get().setUniversal(project.getGroup().toString() + ':' + universalJar.get().getBaseName() + ':' + project.getVersion() + ':' + universalJar.get().getClassifier() + '@' + universalJar.get().getExtension());
            }
            if (userdevConfig.get().getSource() == null) {
                userdevConfig.get().setSource(project.getGroup().toString() + ':' + sourcesJar.get().getBaseName() + ':' + project.getVersion() + ':' + sourcesJar.get().getClassifier() + '@' + sourcesJar.get().getExtension());
            }
            if (!"a/".contentEquals(genConfig.get().getOriginalPrefix())) {
                userdevConfig.get().setPatchesOriginalPrefix(genConfig.get().getOriginalPrefix());
            }
            if (!"b/".contentEquals(genConfig.get().getModifiedPrefix())) {
                userdevConfig.get().setPatchesModifiedPrefix(genConfig.get().getModifiedPrefix());
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
                genConfig.get().dependsOn(applyRangeBaseConfig);
                genConfig.get().setModified(applyRangeBaseConfig.get().getOutput());
            } else {
                //Remap the 'clean' with out mappings.
                TaskApplyMappings toMCPClean = project.getTasks().register("srg2mcpClean", TaskApplyMappings.class).get();
                toMCPClean.dependsOn(dlMappingsConfig, Lists.newArrayList(applyConfig.get().getDependsOn()));
                toMCPClean.setInput(applyConfig.get().getClean());
                toMCPClean.setMappings(dlMappingsConfig.get().getOutput());

                //Zip up the current working folder as genPatches takes a zip
                Zip dirtyZip = project.getTasks().register("patchedZip", Zip.class).get();
                dirtyZip.from(extension.patchedSrc);
                dirtyZip.setArchiveName("output.zip");
                dirtyZip.setDestinationDir(project.file("build/" + dirtyZip.getName() + "/"));

                //Fixup the inputs.
                applyConfig.get().setDependsOn(Lists.newArrayList(toMCPClean));
                applyConfig.get().setClean(toMCPClean.getOutput());
                genConfig.get().setDependsOn(Lists.newArrayList(toMCPClean, dirtyZip));
                genConfig.get().setClean(toMCPClean.getOutput());
                genConfig.get().setModified(dirtyZip.getArchivePath());
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
