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

import net.minecraftforge.gradle.common.task.*;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MappingFile;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.mcp.task.GenerateSRG;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingRepo;
import net.minecraftforge.gradle.userdev.util.Deobfuscator;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class UserDevPlugin implements Plugin<Project> {
    private static String MINECRAFT = "minecraft";
    private static String DEOBF = "deobf";
    public static String OBF = "__obfuscated";

    @Override
    public void apply(@Nonnull Project project) {
        Utils.checkJavaVersion();

        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create(UserDevExtension.EXTENSION_NAME, UserDevExtension.class, project);

        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File nativesFolder = project.file("build/natives/");

        NamedDomainObjectContainer<RenameJarInPlace> reobf = project.container(RenameJarInPlace.class, new NamedDomainObjectFactory<RenameJarInPlace>() {
            @Override
            public RenameJarInPlace create(String jarName) {
                String name = Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);
                JavaPluginConvention java = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

                final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);
                task.setClasspath(java.getSourceSets().getByName("main").getCompileClasspath());

                final Task createMcpToSrg = project.getTasks().findByName("createMcpToSrg");
                if (createMcpToSrg != null) {
                    task.setMappings(() -> createMcpToSrg.getOutputs().getFiles().getSingleFile());
                }

                project.getTasks().getByName("assemble").dependsOn(task);

                // do after-Evaluate resolution, for the same of good error reporting
                project.afterEvaluate(p -> {
                    Task jar = project.getTasks().getByName(jarName);
                    if (!(jar instanceof Jar))
                        throw new IllegalStateException(jarName + "  is not a jar task. Can only reobf jars!");
                    task.setInput(((Jar) jar).getArchivePath());
                    task.dependsOn(jar);

                    if (createMcpToSrg != null && task.getMappings().equals(createMcpToSrg.getOutputs().getFiles().getSingleFile())) {
                        task.dependsOn(createMcpToSrg); // Add needed dependency if uses default mappings
                    }
                });

                return task;
            }
        });
        project.getExtensions().add("reobf", reobf);

        Configuration minecraft = project.getConfigurations().maybeCreate(MINECRAFT);
        Configuration compile = project.getConfigurations().maybeCreate("compile");
        compile.extendsFrom(minecraft);


        //Let gradle handle the downloading by giving it a configuration to dl. We'll focus on applying mappings to it.
        Configuration internalObfConfiguration = project.getConfigurations().maybeCreate(OBF);
        internalObfConfiguration.setDescription("Generated scope for obfuscated dependencies");

        //create extension for dependency remapping
        //can't create at top-level or put in `minecraft` ext due to configuration name conflict
        //TODO move in FG 3.1 when configurations are removed
        Deobfuscator deobfuscator = new Deobfuscator(project, Utils.getCache(project, "deobf_dependencies"));
        DependencyRemapper remapper = new DependencyRemapper(project, deobfuscator);
        project.getExtensions().create(DependencyManagementExtension.EXTENSION_NAME, DependencyManagementExtension.class, project, remapper);

        //TODO remove this block in FG 3.1
        {
            Configuration deobfConfiguration = project.getConfigurations().maybeCreate(DEOBF);

            project.afterEvaluate(p -> {
                DependencySet legacyDeps = deobfConfiguration.getDependencies();
                if (!legacyDeps.isEmpty()) {
                    logger.warn("deobf dependency configuration is deprecated. Please use deobfuscated dependencies in standard configurations");
                    logger.warn("For example, `api fg.deobf(\"your:dependency\")`. More about available configurations: https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_configurations_graph");
                }

                legacyDeps.forEach(d -> p.getDependencies().add("compile", remapper.remap(d)));
            });
        }

        TaskProvider<DownloadMavenArtifact> downloadMcpConfig = project.getTasks().register("downloadMcpConfig", DownloadMavenArtifact.class);
        TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register("extractSrg", ExtractMCPData.class);
        TaskProvider<GenerateSRG> createSrgToMcp = project.getTasks().register("createSrgToMcp", GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcpToSrg = project.getTasks().register("createMcpToSrg", GenerateSRG.class);
        TaskProvider<DownloadMCMeta> downloadMCMeta = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);

        extractSrg.configure(task -> {
            task.dependsOn(downloadMcpConfig);
            task.setConfig(() -> downloadMcpConfig.get().getOutput());
        });

        createSrgToMcp.configure(task -> {
            task.setReverse(false);
            task.dependsOn(extractSrg);
            task.setSrg(extractSrg.get().getOutput());
            task.setMappings(extension.getMappings());
            task.setFormat(MappingFile.Format.SRG);
            task.setOutput(project.file("build/" + createSrgToMcp.getName() + "/output.srg"));
        });

        createMcpToSrg.configure(task -> {
            task.setReverse(true);
            task.dependsOn(extractSrg);
            task.setSrg(extractSrg.get().getOutput());
            task.setMappings(extension.getMappings());
        });

        extractNatives.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
            task.setOutput(nativesFolder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
        });

        if (project.hasProperty("UPDATE_MAPPINGS")) {
            String version = (String)project.property("UPDATE_MAPPINGS");
            String channel = project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String)project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot";

            logger.lifecycle("This process uses Srg2Source for java source file renaming. Please forward relevant bug reports to https://github.com/MinecraftForge/Srg2Source/issues.");
            if ("official".equals(channel)) {
                String warning = "WARNING: This project will be updated to use the official obfuscation mappings provided by Mojang. " + Utils.OFFICIAL_MAPPING_USAGE;
                logger.warn(warning);
            }

            JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName("compileJava");
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            Set<File> srcDirs = javaConv.getSourceSets().getByName("main").getJava().getSrcDirs();

            TaskProvider<DownloadMCPMappingsTask> dlMappingsNew = project.getTasks().register("downloadMappingsNew", DownloadMCPMappingsTask.class);
            TaskProvider<TaskExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", TaskExtractRangeMap.class);
            TaskProvider<TaskApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", TaskApplyRangeMap.class);
            TaskProvider<TaskApplyMappings> toMCPNew = project.getTasks().register("srg2mcpNew", TaskApplyMappings.class);
            TaskProvider<TaskExtractExistingFiles> extractMappedNew = project.getTasks().register("extractMappedNew", TaskExtractExistingFiles.class);

            extractRangeConfig.configure(task -> {
                task.addSources(srcDirs);
                task.addDependencies(javaCompile.getClasspath());
            });

            applyRangeConfig.configure(task -> {
                task.dependsOn(extractRangeConfig, createMcpToSrg);
                task.setRangeMap(extractRangeConfig.get().getOutput());
                task.setSrgFiles(createMcpToSrg.get().getOutput());
                task.setSources(srcDirs);
            });

            dlMappingsNew.configure(task -> {
                task.setMappings(channel + "_" + version);
                task.setOutput(project.file("build/mappings_new.zip"));
            });

            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew, applyRangeConfig);
                task.setInput(applyRangeConfig.get().getOutput());
                task.setMappings(dlMappingsNew.get().getOutput());
            });

            extractMappedNew.configure(task -> {
                task.dependsOn(toMCPNew);
                task.setArchive(toMCPNew.get().getOutput());
                srcDirs.forEach(task::addTarget);
            });

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register("updateMappings", DefaultTask.class);
            updateMappings.get().dependsOn(extractMappedNew);
        }

        project.afterEvaluate(p -> {
            if ("official".equals(extension.getMappingChannel())) {
                String warning = "WARNING: This project is configured to use the official obfuscation mappings provided by Mojang. " + Utils.OFFICIAL_MAPPING_USAGE;
                logger.warn(warning);
            }

            MinecraftUserRepo mcrepo = null;
            DeobfuscatingRepo deobfrepo = null;

            DependencySet deps = minecraft.getDependencies();
            for (Dependency dep : new ArrayList<>(deps)) {
                if (!(dep instanceof ExternalModuleDependency))
                    throw new IllegalArgumentException("minecraft dependency must be a maven dependency.");
                if (mcrepo != null)
                    throw new IllegalArgumentException("Only allows one minecraft dependency.");
                deps.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), extension.getAccessTransformers(), extension.getMappings());
                String newDep = mcrepo.getDependencyString();
                p.getLogger().lifecycle("New Dep: " + newDep);
                ExternalModuleDependency ext = (ExternalModuleDependency) p.getDependencies().create(newDep);
                {
                    if (MinecraftUserRepo.CHANGING_USERDEV)
                        ext.setChanging(true);
                    minecraft.resolutionStrategy(strat -> {
                        strat.cacheChangingModulesFor(10, TimeUnit.SECONDS);
                    });
                }
                minecraft.getDependencies().add(ext);
            }

            project.getRepositories().maven(e -> {
                e.setUrl(Utils.FORGE_MAVEN);
            });

            if (!internalObfConfiguration.getDependencies().isEmpty()) {
                deobfrepo = new DeobfuscatingRepo(project, internalObfConfiguration, deobfuscator);
                if (deobfrepo.getResolvedOrigin() == null) {
                    project.getLogger().error("DeobfRepo attempted to resolve an origin repo early but failed, this may cause issues with some IDEs");
                }
            }
            remapper.attachMappings(extension.getMappings());

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                    .add(mcrepo)
                    .add(deobfrepo)
                    .add(MCPRepo.create(project))
                    .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                    .attach(project);
            project.getRepositories().maven(e -> {
                e.setUrl(Utils.MOJANG_MAVEN);
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
            if (mcrepo == null)
                throw new IllegalStateException("Missing 'minecraft' dependency entry.");
            mcrepo.validate(minecraft, extension.getRuns().getAsMap(), extractNatives.get(), downloadAssets.get(), createSrgToMcp.get()); //This will set the MC_VERSION property.

            String mcVer = (String) project.getExtensions().getExtraProperties().get("MC_VERSION");
            String mcpVer = (String) project.getExtensions().getExtraProperties().get("MCP_VERSION");
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().setMCVersion(mcVer);

            RenameJarInPlace reobfJar = reobf.create("jar");
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.setMappings(createMcpToSrg.get().getOutput());

            String assetIndex = mcVer;

            try {
                // Check meta exists
                if (!downloadMCMeta.get().getOutput().exists()) {
                    // Force download meta
                    downloadMCMeta.get().downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(downloadMCMeta.get().getOutput(), VersionJson.class);

                assetIndex = json.assetIndex.id;
            } catch (IOException e) {
                e.printStackTrace();
            }

            // Finalize asset index
            final String finalAssetIndex = assetIndex;

            extension.getRuns().forEach(runConfig -> runConfig.token("asset_index", finalAssetIndex));
            Utils.createRunConfigTasks(extension, extractNatives.get(), downloadAssets.get(), createSrgToMcp.get());
        });
    }

}
