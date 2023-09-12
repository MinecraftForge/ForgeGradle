/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.common.legacy.LegacyExtension;
import net.minecraftforge.gradle.common.tasks.ApplyMappings;
import net.minecraftforge.gradle.common.tasks.ApplyRangeMap;
import net.minecraftforge.gradle.common.tasks.DownloadAssets;
import net.minecraftforge.gradle.common.tasks.DownloadMCMeta;
import net.minecraftforge.gradle.common.tasks.DynamicJarExec;
import net.minecraftforge.gradle.common.tasks.ExtractExistingFiles;
import net.minecraftforge.gradle.common.tasks.ExtractMCPData;
import net.minecraftforge.gradle.common.tasks.ExtractNatives;
import net.minecraftforge.gradle.common.tasks.ExtractRangeMap;
import net.minecraftforge.gradle.common.tasks.ExtractZip;
import net.minecraftforge.gradle.common.tasks.JarExec;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.EnvironmentChecks;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.ChannelProvidersExtension;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.function.MCPFunction;
import net.minecraftforge.gradle.mcp.function.MCPFunctionFactory;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPConfig;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import net.minecraftforge.gradle.mcp.tasks.SetupMCP;
import net.minecraftforge.gradle.patcher.tasks.ApplyPatches;
import net.minecraftforge.gradle.patcher.tasks.BakePatches;
import net.minecraftforge.gradle.patcher.tasks.CreateExc;
import net.minecraftforge.gradle.patcher.tasks.CreateFakeSASPatches;
import net.minecraftforge.gradle.patcher.tasks.FilterNewJar;
import net.minecraftforge.gradle.patcher.tasks.GenerateBinPatches;
import net.minecraftforge.gradle.patcher.tasks.GeneratePatches;
import net.minecraftforge.gradle.patcher.tasks.GenerateUserdevConfig;
import net.minecraftforge.gradle.patcher.tasks.ReobfuscateJar;

import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

import codechicken.diffpatch.util.PatchMode;
import com.google.common.collect.Lists;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class PatcherPlugin implements Plugin<Project> {
    private static final String MC_DEP_CONFIG = "minecraftImplementation";

    @Override
    public void apply(@Nonnull Project project) {
        EnvironmentChecks.checkEnvironment(project);

        final PatcherExtension extension = project.getExtensions().create(PatcherExtension.class, PatcherExtension.EXTENSION_NAME, PatcherExtension.class, project);
        project.getExtensions().create(ChannelProvidersExtension.EXTENSION_NAME, ChannelProvidersExtension.class);
        project.getExtensions().create(LegacyExtension.EXTENSION_NAME, LegacyExtension.class);
        project.getPluginManager().apply(JavaPlugin.class);
        final JavaPluginExtension javaConv = project.getExtensions().getByType(JavaPluginExtension.class);

        Configuration mcImplementation = project.getConfigurations().maybeCreate(MC_DEP_CONFIG);
        mcImplementation.setCanBeResolved(true);
        project.getConfigurations().named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME)
                .configure(c -> c.extendsFrom(mcImplementation));

        final TaskContainer tasks = project.getTasks();
        final TaskProvider<Jar> jarTask = tasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class);
        final TaskProvider<JavaCompile> javaCompileTask = tasks.named(JavaPlugin.COMPILE_JAVA_TASK_NAME, JavaCompile.class);
        final NamedDomainObjectProvider<SourceSet> mainSource = javaConv.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME);

        final TaskProvider<DownloadMCPMappings> dlMappingsConfig = tasks.register("downloadMappings", DownloadMCPMappings.class);
        final TaskProvider<DownloadMCMeta> dlMCMetaConfig = tasks.register("downloadMCMeta", DownloadMCMeta.class);
        final TaskProvider<ExtractNatives> extractNatives = tasks.register("extractNatives", ExtractNatives.class);
        final TaskProvider<ApplyPatches> applyPatches = tasks.register("applyPatches", ApplyPatches.class);
        final TaskProvider<ApplyMappings> toMCPConfig = tasks.register("srg2mcp", ApplyMappings.class);
        final TaskProvider<ExtractZip> extractMapped = tasks.register("extractMapped", ExtractZip.class);
        final TaskProvider<GenerateSRG> createMcp2Srg = tasks.register("createMcp2Srg", GenerateSRG.class);
        final TaskProvider<GenerateSRG> createMcp2Obf = tasks.register("createMcp2Obf", GenerateSRG.class);
        final TaskProvider<GenerateSRG> createSrg2Mcp = tasks.register("createSrg2Mcp", GenerateSRG.class);
        final TaskProvider<CreateExc> createExc = tasks.register("createExc", CreateExc.class);
        final TaskProvider<ExtractRangeMap> extractRangeConfig = tasks.register("extractRangeMap", ExtractRangeMap.class);
        final TaskProvider<ApplyRangeMap> applyRangeConfig = tasks.register("applyRangeMap", ApplyRangeMap.class);
        final TaskProvider<ApplyRangeMap> applyRangeBaseConfig = tasks.register("applyRangeMapBase", ApplyRangeMap.class);
        final TaskProvider<GeneratePatches> genPatches = tasks.register("genPatches", GeneratePatches.class);
        final TaskProvider<BakePatches> bakePatches = tasks.register("bakePatches", BakePatches.class);
        final TaskProvider<DownloadAssets> downloadAssets = tasks.register("downloadAssets", DownloadAssets.class);
        final TaskProvider<ReobfuscateJar> reobfJar = tasks.register("reobfJar", ReobfuscateJar.class);
        final TaskProvider<GenerateBinPatches> genJoinedBinPatches = tasks.register("genJoinedBinPatches", GenerateBinPatches.class);
        final TaskProvider<GenerateBinPatches> genClientBinPatches = tasks.register("genClientBinPatches", GenerateBinPatches.class);
        final TaskProvider<GenerateBinPatches> genServerBinPatches = tasks.register("genServerBinPatches", GenerateBinPatches.class);
        final TaskProvider<DefaultTask> genBinPatches = tasks.register("genBinPatches", DefaultTask.class);
        final TaskProvider<FilterNewJar> filterNew = tasks.register("filterJarNew", FilterNewJar.class);
        final TaskProvider<Jar> sourcesJar = tasks.register("sourcesJar", Jar.class);
        final TaskProvider<Jar> universalJar = tasks.register("universalJar", Jar.class);
        final TaskProvider<Jar> userdevJar = tasks.register("userdevJar", Jar.class);
        final TaskProvider<GenerateUserdevConfig> userdevConfig = tasks.register("userdevConfig", GenerateUserdevConfig.class, project);
        final TaskProvider<DefaultTask> release = tasks.register("release", DefaultTask.class);

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

        release.configure(task -> task.dependsOn(sourcesJar, universalJar, userdevJar));

        dlMappingsConfig.configure(task -> task.getMappings().convention(extension.getMappings()));

        extractNatives.configure(task -> {
            task.getMeta().set(dlMCMetaConfig.flatMap(DownloadMCMeta::getOutput));
            task.getOutput().set(project.getLayout().getBuildDirectory().dir("natives"));
        });

        downloadAssets.configure(task -> task.getMeta().set(dlMCMetaConfig.flatMap(DownloadMCMeta::getOutput)));

        applyPatches.configure(task -> {
            final Provider<Directory> workDir = project.getLayout().getBuildDirectory().dir(task.getName());
            task.getOutput().set(workDir.map(s -> s.file("output.zip")));
            task.getRejects().set(workDir.map(s -> s.file("rejects.zip").getAsFile()));
            task.getPatches().set(extension.getPatches());
            task.getPatchMode().set(PatchMode.ACCESS);
            if (project.hasProperty("UPDATING")) {
                task.getPatchMode().set(PatchMode.FUZZY);
                task.getRejects().set(project.getLayout().getProjectDirectory().dir("rejects").getAsFile());
                task.setFailOnError(false);
            }
        });

        toMCPConfig.configure(task -> {
            task.getInput().set(applyPatches.flatMap(ApplyPatches::getOutput));
            task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
            task.setLambdas(false);
        });

        extractMapped.configure(task -> {
            task.getZip().set(toMCPConfig.flatMap(ApplyMappings::getOutput));
            task.getOutput().set(extension.getPatchedSrc());
        });

        extractRangeConfig.configure(task -> {
            task.getDependencies().from(jarTask.flatMap(AbstractArchiveTask::getArchiveFile));

            // Only add main source, as we inject the patchedSrc into it as a sourceset.
            task.getSources().from(mainSource.map(s -> s.getJava().getSourceDirectories()));
            task.getDependencies().from(javaCompileTask.map(JavaCompile::getClasspath));
        });

        createMcp2Srg.configure(task -> task.setReverse(true));
        createSrg2Mcp.configure(task -> task.setReverse(false));
        createMcp2Obf.configure(task -> {
            task.setNotch(true);
            task.setReverse(true);
        });

        createExc.configure(task -> task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput)));

        applyRangeConfig.configure(task -> {
            task.getSources().from(mainSource.map(s -> s.getJava().getSourceDirectories().minus(project.files(extension.getPatchedSrc()))));
            task.setOnlyIf(t -> !task.getSources().isEmpty());
            task.getRangeMap().set(extractRangeConfig.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(GenerateSRG::getOutput));
            task.getExcFiles().from(createExc.flatMap(CreateExc::getOutput), extension.getExcs());
        });

        applyRangeBaseConfig.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.getSources().from(extension.getPatchedSrc());
            task.getRangeMap().set(extractRangeConfig.flatMap(ExtractRangeMap::getOutput));
            task.getSrgFiles().from(createMcp2Srg.flatMap(GenerateSRG::getOutput));
            task.getExcFiles().from(createExc.flatMap(CreateExc::getOutput), extension.getExcs());
        });

        genPatches.configure(task -> {
            task.setOnlyIf(t -> extension.getPatches().isPresent());
            task.getOutput().set(extension.getPatches());
        });

        bakePatches.configure(task -> {
            task.dependsOn(genPatches);
            task.getInput().set(extension.getPatches());
            task.getOutput().set(new File(task.getTemporaryDir(), "output.zip"));
        });

        reobfJar.configure(task -> {
            task.getInput().set(jarTask.flatMap(AbstractArchiveTask::getArchiveFile));
            task.getLibraries().from(project.getConfigurations().named(MC_DEP_CONFIG));
        });

        genJoinedBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getSide().set("joined");
        });
        genClientBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getSide().set("client");
        });
        genServerBinPatches.configure(task -> {
            task.getDirtyJar().set(reobfJar.flatMap(ReobfuscateJar::getOutput));
            task.getSide().set("server");
        });
        genBinPatches.configure(task -> task.dependsOn(genJoinedBinPatches, genClientBinPatches, genServerBinPatches));

        filterNew.configure(task -> task.getInput().set(reobfJar.flatMap(ReobfuscateJar::getOutput)));

        /*
         * All sources in SRG names.
         * patches in /patches/
         */
        sourcesJar.configure(task -> {
            task.setOnlyIf(t -> applyRangeConfig.flatMap(ApplyRangeMap::getOutput).map(rf -> rf.getAsFile().exists()).getOrElse(false));
            task.dependsOn(applyRangeConfig);
            task.from(project.zipTree(applyRangeConfig.flatMap(ApplyRangeMap::getOutput)));
            task.getArchiveClassifier().set("sources");
        });

        /* Universal:
         * All of our classes and resources as normal jar.
         *   Should only be OUR classes, not parent patcher projects.
         */
        universalJar.configure(task -> {
            task.dependsOn(filterNew);
            task.from(project.zipTree(filterNew.flatMap(FilterNewJar::getOutput)));
            task.from(javaConv.getSourceSets().named(SourceSet.MAIN_SOURCE_SET_NAME).map(SourceSet::getResources));
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
            task.dependsOn(sourcesJar, bakePatches);
            task.setOnlyIf(t -> extension.isSrgPatches());
            task.from(userdevConfig.flatMap(GenerateUserdevConfig::getOutput), e -> e.rename(f -> "config.json"));
            task.from(genJoinedBinPatches.flatMap(GenerateBinPatches::getOutput), e -> e.rename(f -> "joined.lzma"));
            task.from(project.zipTree(bakePatches.flatMap(BakePatches::getOutput)), e -> e.into("patches/"));
            task.getArchiveClassifier().set("userdev");
        });

        final boolean doingUpdate = project.hasProperty("UPDATE_MAPPINGS");
        final String updateVersion = doingUpdate ? (String) project.property("UPDATE_MAPPINGS") : null;
        final String updateChannel = doingUpdate
                ? (project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String) project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot")
                : null;
        if (doingUpdate) {
            TaskProvider<DownloadMCPMappings> dlMappingsNew = tasks.register("downloadMappingsNew", DownloadMCPMappings.class);
            dlMappingsNew.get().getMappings().set(updateChannel + '_' + updateVersion);

            TaskProvider<ApplyMappings> toMCPNew = tasks.register("srg2mcpNew", ApplyMappings.class);
            toMCPNew.configure(task -> {
                task.getInput().set(applyRangeConfig.flatMap(ApplyRangeMap::getOutput));
                task.getMappings().set(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
                task.setLambdas(false);
            });

            TaskProvider<ExtractExistingFiles> extractMappedNew = tasks.register("extractMappedNew", ExtractExistingFiles.class);
            extractMappedNew.configure(task -> {
                task.getArchive().set(toMCPNew.flatMap(ApplyMappings::getOutput));
                task.getTargets().from(mainSource.map(s -> s.getJava().getSourceDirectories().minus(project.files(extension.getPatchedSrc()))));
            });

            TaskProvider<DefaultTask> updateMappings = tasks.register("updateMappings", DefaultTask.class);
            updateMappings.configure(task -> task.dependsOn(extractMappedNew));
        }

        project.afterEvaluate(p -> {
            // Add the patched source as a source dir during afterEvaluate, to not be overwritten by buildscripts
            mainSource.configure(s -> s.getJava().srcDir(extension.getPatchedSrc()));

            //mainSource.resources(v -> {
            //}); //TODO: Asset downloading, needs asset index from json.
            //javaConv.getSourceSets().stream().forEach(s -> extractRangeConfig.get().addSources(s.getJava().getSrcDirs()));


            // Automatically create the patches folder if it does not exist
            if (extension.getPatches().isPresent()) {
                File patchesDir = extension.getPatches().get().getAsFile();
                if (!patchesDir.exists() && !patchesDir.mkdirs()) { // TODO: validate if we actually need to do this
                    p.getLogger().warn("Unable to create patches folder automatically, there may be some task errors");
                }
                sourcesJar.configure(t -> t.from(genPatches.flatMap(GeneratePatches::getOutput), e -> e.into("patches/")));
            }

            final TaskProvider<DynamicJarExec> procConfig = extension.getProcessor() == null ? null
                    : tasks.register("postProcess", DynamicJarExec.class);

            if (extension.getParent().isPresent()) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                final Project parent = extension.getParent().get();
                final TaskContainer parentTasks = parent.getTasks();
                final MCPPlugin parentMCPPlugin = parent.getPlugins().findPlugin(MCPPlugin.class);
                final PatcherPlugin parentPatcherPlugin = parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (parentMCPPlugin != null) {
                    final TaskProvider<SetupMCP> setupMCP = parentTasks.named("setupMCP", SetupMCP.class);

                    Provider<RegularFile> setupOutput = setupMCP.flatMap(SetupMCP::getOutput);
                    if (procConfig != null) {
                        procConfig.configure(task -> {
                            task.getInput().set(setupMCP.flatMap(SetupMCP::getOutput));
                            task.getTool().set(extension.getProcessor().getVersion());
                            task.getArgs().set(extension.getProcessor().getArgs());
                            Integer javaVersion = extension.getProcessor().getJavaVersion();
                            if (javaVersion != null)
                                task.setRuntimeJavaVersion(javaVersion);
                            task.getData().set(extension.getProcessorData());
                        });
                        setupOutput = procConfig.flatMap(DynamicJarExec::getOutput);
                    }

                    extension.getCleanSrc().convention(setupOutput);
                    applyPatches.configure(task -> task.getBase().convention(extension.getCleanSrc().getAsFile()));
                    genPatches.configure(task -> task.getBase().convention(extension.getCleanSrc()));

                    final TaskProvider<DownloadMCPConfig> downloadConfig = parentTasks.named("downloadConfig", DownloadMCPConfig.class);

                    final TaskProvider<ExtractMCPData> extractSrg = tasks.register("extractSrg", ExtractMCPData.class);
                    extractSrg.configure(task -> task.getConfig().set(downloadConfig.flatMap(DownloadMCPConfig::getOutput)));
                    createMcp2Srg.configure(task -> task.getSrg().convention(extractSrg.flatMap(ExtractMCPData::getOutput)));

                    final TaskProvider<ExtractMCPData> extractStatic = tasks.register("extractStatic", ExtractMCPData.class);
                    extractStatic.configure(task -> {
                        task.getConfig().set(downloadConfig.flatMap(DownloadMCPConfig::getOutput));
                        task.getKey().set("statics");
                        task.setAllowEmpty(true);
                        task.getOutput().set(project.getLayout().getBuildDirectory()
                                .dir(task.getName()).map(d -> d.file("output.txt")));
                    });

                    final TaskProvider<ExtractMCPData> extractConstructors = tasks.register("extractConstructors", ExtractMCPData.class);
                    extractConstructors.configure(task -> {
                        task.getConfig().set(downloadConfig.flatMap(DownloadMCPConfig::getOutput));
                        task.getKey().set("constructors");
                        task.setAllowEmpty(true);
                        task.getOutput().set(project.getLayout().getBuildDirectory()
                                .dir(task.getName()).map(d -> d.file("output.txt")));
                    });

                    createExc.configure(task -> {
                        task.getConfig().convention(downloadConfig.flatMap(DownloadMCPConfig::getOutput));
                        task.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getOutput));
                        task.getStatics().convention(extractStatic.flatMap(ExtractMCPData::getOutput));
                        task.getConstructors().convention(extractConstructors.flatMap(ExtractMCPData::getOutput));
                    });

                } else if (parentPatcherPlugin != null) {
                    final PatcherExtension parentPatcher = parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(parentPatcher);
                    final TaskProvider<ApplyPatches> parentApplyPatches = parentTasks.named("applyPatches", ApplyPatches.class);

                    Provider<RegularFile> setupOutput = parentApplyPatches.flatMap(ApplyPatches::getOutput);
                    if (procConfig != null) {
                        procConfig.configure(task -> {
                            task.getInput().set(parentApplyPatches.flatMap(ApplyPatches::getOutput));
                            task.getTool().set(extension.getProcessor().getVersion());
                            task.getArgs().set(extension.getProcessor().getArgs());
                            task.getData().set(extension.getProcessorData());
                        });
                        setupOutput = procConfig.flatMap(DynamicJarExec::getOutput);
                    }

                    extension.getCleanSrc().convention(setupOutput);
                    applyPatches.configure(task -> task.getBase().convention(extension.getCleanSrc().getAsFile()));
                    genPatches.configure(task -> task.getBase().convention(extension.getCleanSrc()));

                    final TaskProvider<GenerateSRG> parentCreateMcp2Srg = parentTasks.named("createMcp2Srg", GenerateSRG.class);
                    createMcp2Srg.configure(task -> task.getSrg().convention(parentCreateMcp2Srg.flatMap(GenerateSRG::getSrg)));

                    final TaskProvider<CreateExc> parentCreateExc = parentTasks.named("createExc", CreateExc.class);
                    createExc.configure(task -> {
                        task.getConfig().convention(parentCreateExc.flatMap(CreateExc::getConfig));
                        task.getSrg().convention(parentCreateExc.flatMap(CreateExc::getSrg));
                        task.getStatics().convention(parentCreateExc.flatMap(CreateExc::getStatics));
                        task.getConstructors().convention(parentCreateExc.flatMap(CreateExc::getConstructors));
                    });

                    for (TaskProvider<GenerateBinPatches> binPatchesTask : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                        final TaskProvider<GenerateBinPatches> parentBinPatchesTask = parentTasks.named(binPatchesTask.getName(), GenerateBinPatches.class);
                        binPatchesTask.configure(task -> task.getPatchSets().from(parentBinPatchesTask.map(GenerateBinPatches::getPatchSets)));
                    }

                    filterNew.configure(task -> task.getBlacklist().from(parentTasks.named(JavaPlugin.JAR_TASK_NAME, Jar.class)
                            .flatMap(AbstractArchiveTask::getArchiveFile)));
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }

                dlMappingsConfig.configure(task -> task.getMappings().convention(extension.getMappings()));

                for (TaskProvider<GenerateSRG> genSrg : Arrays.asList(createMcp2Srg, createSrg2Mcp, createMcp2Obf)) {
                    genSrg.configure(task -> task.getMappings().convention(dlMappingsConfig.flatMap(DownloadMCPMappings::getMappings)));
                }

                createMcp2Obf.configure(task -> task.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getSrg)));
                createSrg2Mcp.configure(task -> task.getSrg().convention(createMcp2Srg.flatMap(GenerateSRG::getSrg)));
            }
            final Project mcpParent = getMcpParent(project);
            if (mcpParent == null) {
                throw new IllegalStateException("Could not find MCP parent project, you must specify a parent chain to MCP.");
            }
            final MCPExtension mcpParentExtension = mcpParent.getExtensions().getByType(MCPExtension.class);

            // Needs to be client extra, to get the data files.
            project.getDependencies().add(MC_DEP_CONFIG, mcpParentExtension.getConfig()
                    .map(ver -> "net.minecraft:client:" + ver.getVersion() + ":extra"));
            // Add mappings so that it can be used by reflection tools.
            project.getDependencies().add(MC_DEP_CONFIG, extension.getMappingChannel()
                    .zip(extension.getMappingVersion(), MCPRepo::getMappingDep));

            dlMCMetaConfig.configure(task -> task.getMCVersion().convention(extension.getMcVersion()));

            if (!extension.getAccessTransformers().isEmpty()) {
                TaskProvider<SetupMCP> setupMCP = mcpParent.getTasks().named("setupMCP", SetupMCP.class);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createAT(mcpParent,
                        new ArrayList<>(extension.getAccessTransformers().getFiles()), Collections.emptyList());
                setupMCP.configure(task -> task.getPreDecompile().put(project.getName() + "AccessTransformer", function));
                extension.getAccessTransformers().forEach(f -> {
                    userdevJar.configure(t -> t.from(f, e -> e.into("ats/")));
                    userdevConfig.configure(t -> t.getATs().from(f));
                });
            }

            if (!extension.getSideAnnotationStrippers().isEmpty()) {
                TaskProvider<SetupMCP> setupMCP = mcpParent.getTasks().named("setupMCP", SetupMCP.class);
                @SuppressWarnings("deprecation")
                MCPFunction function = MCPFunctionFactory.createSAS(mcpParent, new ArrayList<>(extension.getSideAnnotationStrippers().getFiles()), Collections.emptyList());
                setupMCP.configure(task -> task.getPreDecompile().put(project.getName() + "SideStripper", function));
                extension.getSideAnnotationStrippers().forEach(f -> {
                    userdevJar.configure(t -> t.from(f, e -> e.into("sas/")));
                    userdevConfig.configure(t -> t.getSASs().from(f));
                });
            }

            TaskProvider<CreateFakeSASPatches> fakePatches = null;
            PatcherExtension ext = extension;
            while (ext != null) {
                if (!ext.getSideAnnotationStrippers().isEmpty()) {
                    if (fakePatches == null) {
                        fakePatches = tasks.register("createFakeSASPatches", CreateFakeSASPatches.class);
                    }
                    final PatcherExtension patcherExt = ext;
                    fakePatches.configure(task -> task.getFiles().from(patcherExt.getSideAnnotationStrippers()));
                }
                if (ext.getParent().isPresent()) { // FIXME: possible loop?
                    ext = ext.getParent().get().getExtensions().findByType(PatcherExtension.class);
                }
            }

            if (fakePatches != null) {
                final TaskProvider<CreateFakeSASPatches> fakePatchesTask = fakePatches;
                for (TaskProvider<GenerateBinPatches> binPatchesTask : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                    binPatchesTask.configure(task -> task.getPatchSets().from(fakePatchesTask.flatMap(CreateFakeSASPatches::getOutput)));
                }
            }

            if (!extension.getExtraMappings().isEmpty()) {
                extension.getExtraMappings().stream().filter(e -> e instanceof File).map(e -> (File) e).forEach(e -> {
                    userdevJar.configure(t -> t.from(e, c -> c.into("srgs/")));
                    userdevConfig.configure(t -> t.getSRGs().from(e));
                });
                extension.getExtraMappings().stream().filter(e -> e instanceof String)
                        .map(e -> (String) e).forEach(e -> userdevConfig.configure(t -> t.getSRGLines().add(e)));
            }

            //UserDev Config Default Values
            userdevConfig.configure(task -> {
                task.getTool().convention(genJoinedBinPatches.map(JarExec::getResolvedVersion)
                        .map(ver -> "net.minecraftforge:binarypatcher:" + ver + ":fatjar"));
                task.getArguments().addAll("--clean", "{clean}", "--output", "{output}", "--apply", "{patch}");
                task.getUniversal().convention(universalJar.flatMap(t ->
                        t.getArchiveBaseName().flatMap(baseName ->
                                t.getArchiveClassifier().flatMap(classifier ->
                                        t.getArchiveExtension().map(jarExt ->
                                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                                        )))));
                task.getSource().convention(sourcesJar.flatMap(t ->
                        t.getArchiveBaseName().flatMap(baseName ->
                                t.getArchiveClassifier().flatMap(classifier ->
                                        t.getArchiveExtension().map(jarExt ->
                                                project.getGroup().toString() + ':' + baseName + ':' + project.getVersion() + ':' + classifier + '@' + jarExt
                                        )))));
                task.getPatchesOriginalPrefix().convention(genPatches.flatMap(GeneratePatches::getOriginalPrefix));
                task.getPatchesModifiedPrefix().convention(genPatches.flatMap(GeneratePatches::getModifiedPrefix));
                task.setNotchObf(extension.getNotchObf());
            });

            if (procConfig != null) {
                userdevJar.configure(task -> {
                    task.dependsOn(procConfig);
                    task.from(extension.getProcessorData().get().values(), c -> c.into("processor"));
                });
                userdevConfig.configure(task -> {
                    task.setProcessor(extension.getProcessor());
                    extension.getProcessorData().get().forEach(task::addProcessorData);
                });
            }

            // Allow generation of patches to skip S2S. For in-dev patches while the code doesn't compile.
            if (extension.isSrgPatches()) {
                genPatches.configure(task -> task.getModified().value(applyRangeBaseConfig.flatMap(ApplyRangeMap::getOutput)));
            } else {
                // Remap the 'clean' with out mappings.
                TaskProvider<ApplyMappings> toMCPClean = tasks.register("srg2mcpClean", ApplyMappings.class);
                toMCPClean.configure(task -> {
                    task.dependsOn(applyPatches.map(DefaultTask::getDependsOn));
                    task.getInput().fileProvider(applyPatches.flatMap(ApplyPatches::getBase));
                    task.getMappings().value(dlMappingsConfig.flatMap(DownloadMCPMappings::getOutput));
                    task.setLambdas(false);
                });

                // Zip up the current working folder as genPatches takes a zip
                TaskProvider<Zip> dirtyZip = tasks.register("patchedZip", Zip.class);
                dirtyZip.configure(task -> {
                    task.from(extension.getPatchedSrc());
                    task.getArchiveFileName().set("output.zip");
                    task.getDestinationDirectory().set(project.getLayout().getBuildDirectory().dir(task.getName()));
                });

                // Fixup the inputs.
                applyPatches.configure(task -> task.getBase().set(toMCPClean.flatMap(s -> s.getOutput().getAsFile())));
                genPatches.configure(task -> {
                    task.getBase().set(toMCPClean.flatMap(ApplyMappings::getOutput));
                    task.getModified().set(dirtyZip.flatMap(AbstractArchiveTask::getArchiveFile));
                });
            }

            {
                final Provider<String> mcpConfigVersion = mcpParentExtension.getConfig().map(Artifact::getVersion);
                Provider<String> version = mcpConfigVersion.map(ver -> extension.getNotchObf() ? ver.substring(0, ver.lastIndexOf('-')) : ver);
                Provider<String> classifier = project.provider(() -> extension.getNotchObf() ? "" : ":srg");

                Provider<File> clientJar = version.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:client:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Client " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });
                Provider<File> serverJar = version.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:server:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Server " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });
                Provider<File> joinedJar = mcpConfigVersion.zip(classifier, (ver, cls) -> {
                    File ret = MavenArtifactDownloader.generate(project, "net.minecraft:joined:" + ver + cls, true);
                    if (ret == null || !ret.exists())
                        throw new RuntimeException("Joined " + (extension.getNotchObf() ? "notch" : "SRG" + " jar not found"));
                    return ret;
                });

                TaskProvider<GenerateSRG> srg = extension.getNotchObf() ? createMcp2Obf : createMcp2Srg;
                reobfJar.configure(t -> t.getSrg().set(srg.flatMap(GenerateSRG::getOutput)));

                genJoinedBinPatches.configure(t -> t.getCleanJar().convention(project.getLayout().file(joinedJar)));
                genClientBinPatches.configure(t -> t.getCleanJar().convention(project.getLayout().file(clientJar)));
                genServerBinPatches.configure(t -> t.getCleanJar().convention(project.getLayout().file(serverJar)));
                for (TaskProvider<GenerateBinPatches> binPatchesTask : Lists.newArrayList(genJoinedBinPatches, genClientBinPatches, genServerBinPatches)) {
                    binPatchesTask.configure(task -> {
                        task.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                        if (extension.getPatches().isPresent()) {
                            task.mustRunAfter(genPatches);
                            task.getPatchSets().from(extension.getPatches());
                        }
                    });
                }

                filterNew.configure(t -> {
                    t.getSrg().set(srg.flatMap(GenerateSRG::getOutput));
                    t.getBlacklist().from(joinedJar);
                });
            }

            Map<String, String> tokens = new HashMap<>();

            try {
                // Check meta exists
                final DownloadMCMeta downloadMCMetaTask = dlMCMetaConfig.get();
                final File metaOutput = downloadMCMetaTask.getOutput().get().getAsFile();
                if (!metaOutput.exists()) {
                    // Force download meta
                    downloadMCMetaTask.downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(metaOutput, VersionJson.class);

                tokens.put("asset_index", json.assetIndex.id);
            } catch (IOException e) {
                e.printStackTrace();

                // Fallback to MC version
                tokens.put("asset_index", extension.getMcVersion().get());
            }

            extension.getRuns().forEach(runConfig -> runConfig.tokens(tokens));
            if (extension.getCopyIdeResources().get() == Boolean.TRUE)
                Utils.setupIDEResourceCopy(project);  // We need to have the copy resources task BEFORE the run config ones so we can detect them
            Utils.createRunConfigTasks(extension, extractNatives, downloadAssets, createSrg2Mcp);
        });
    }

    @Nullable
    private Project getMcpParent(Project project) {
        final PatcherExtension extension = project.getExtensions().findByType(PatcherExtension.class);
        if (extension == null || !extension.getParent().isPresent()) {
            return null;
        }
        final Project parent = extension.getParent().get();
        MCPPlugin mcp = parent.getPlugins().findPlugin(MCPPlugin.class);
        PatcherPlugin patcher = parent.getPlugins().findPlugin(PatcherPlugin.class);
        if (mcp != null) {
            return parent;
        } else if (patcher != null) {
            return getMcpParent(parent);
        }
        return null;
    }
}
