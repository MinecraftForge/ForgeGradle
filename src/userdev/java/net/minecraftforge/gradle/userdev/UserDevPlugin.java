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

import net.minecraftforge.gradle.common.tasks.ApplyMappings;
import net.minecraftforge.gradle.common.tasks.ApplyRangeMap;
import net.minecraftforge.gradle.common.tasks.DownloadAssets;
import net.minecraftforge.gradle.common.tasks.DownloadMCMeta;
import net.minecraftforge.gradle.common.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.common.tasks.ExtractExistingFiles;
import net.minecraftforge.gradle.common.tasks.ExtractMCPData;
import net.minecraftforge.gradle.common.tasks.ExtractNatives;
import net.minecraftforge.gradle.common.tasks.ExtractRangeMap;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MinecraftRepo;
import net.minecraftforge.gradle.common.util.MojangLicenseHelper;
import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.mcp.tasks.DownloadMCPMappings;
import net.minecraftforge.gradle.mcp.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingRepo;
import net.minecraftforge.gradle.userdev.util.Deobfuscator;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import net.minecraftforge.srgutils.IMappingFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository.MetadataSources;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.JavaCompile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public class UserDevPlugin implements Plugin<Project> {
    private static String MINECRAFT = "minecraft";
    public static String OBF = "__obfuscated";

    @Override
    public void apply(@Nonnull Project project) {
        Utils.checkEnvironment();
        Utils.addRepoFilters(project);

        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create(UserDevExtension.EXTENSION_NAME, UserDevExtension.class, project);

        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File nativesFolder = project.file("build/natives/");

        NamedDomainObjectContainer<RenameJarInPlace> reobf = project.container(RenameJarInPlace.class, jarName -> {
            String name = Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);
            JavaPluginConvention java = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);
            task.getClasspath().from(java.getSourceSets().getByName("main").getCompileClasspath());

            final Task createMcpToSrg = project.getTasks().findByName("createMcpToSrg");
            if (createMcpToSrg != null) {
                task.getMappings().set(createMcpToSrg.getOutputs().getFiles().getSingleFile());
            }

            project.getTasks().getByName("assemble").dependsOn(task);

            // do after-Evaluate resolution, for the same of good error reporting
            project.afterEvaluate(p -> {
                Task jar = project.getTasks().getByName(jarName);
                if (!(jar instanceof Jar))
                    throw new IllegalStateException(jarName + "  is not a jar task. Can only reobf jars!");
                task.getInput().set(((Jar) jar).getArchiveFile().get().getAsFile());
                task.dependsOn(jar);

                if (createMcpToSrg != null && task.getMappings().equals(createMcpToSrg.getOutputs().getFiles().getSingleFile())) {
                    task.dependsOn(createMcpToSrg); // Add needed dependency if uses default mappings
                }
            });

            return task;
        });
        project.getExtensions().add("reobf", reobf);

        Configuration minecraft = project.getConfigurations().maybeCreate(MINECRAFT);
        for (String cfg : new String[] {"compile", "implementation"}) {
            Configuration c = project.getConfigurations().findByName(cfg);
            if (c != null)
                c.extendsFrom(minecraft);
        }

        //Let gradle handle the downloading by giving it a configuration to dl. We'll focus on applying mappings to it.
        Configuration internalObfConfiguration = project.getConfigurations().maybeCreate(OBF);
        internalObfConfiguration.setDescription("Generated scope for obfuscated dependencies");

        //create extension for dependency remapping
        //can't create at top-level or put in `minecraft` ext due to configuration name conflict
        Deobfuscator deobfuscator = new Deobfuscator(project, Utils.getCache(project, "deobf_dependencies"));
        DependencyRemapper remapper = new DependencyRemapper(project, deobfuscator);
        project.getExtensions().create(DependencyManagementExtension.EXTENSION_NAME, DependencyManagementExtension.class, project, remapper);

        TaskProvider<DownloadMavenArtifact> downloadMcpConfig = project.getTasks().register("downloadMcpConfig", DownloadMavenArtifact.class);
        TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register("extractSrg", ExtractMCPData.class);
        TaskProvider<GenerateSRG> createSrgToMcp = project.getTasks().register("createSrgToMcp", GenerateSRG.class);
        TaskProvider<GenerateSRG> createMcpToSrg = project.getTasks().register("createMcpToSrg", GenerateSRG.class);
        TaskProvider<DownloadMCMeta> downloadMCMeta = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);
        TaskProvider<DefaultTask> hideLicense = project.getTasks().register(MojangLicenseHelper.HIDE_LICENSE, DefaultTask.class);
        TaskProvider<DefaultTask> showLicense = project.getTasks().register(MojangLicenseHelper.SHOW_LICENSE, DefaultTask.class);

        hideLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.hide(project, extension.getMappingChannel().get(), extension.getMappingVersion().get());
            });
        });

        showLicense.configure(task -> {
            task.doLast(_task -> {
                MojangLicenseHelper.show(project, extension.getMappingChannel().get(), extension.getMappingVersion().get());
            });
        });

        extractSrg.configure(task -> {
            task.dependsOn(downloadMcpConfig);
            task.getConfig().set(downloadMcpConfig.get().getOutput());
        });

        createSrgToMcp.configure(task -> {
            task.setReverse(false);
            task.dependsOn(extractSrg);
            task.getSrg().set(extractSrg.get().getOutput());
            task.getMappings().set(extension.getMappings());
            task.getFormat().set(IMappingFile.Format.SRG);
            task.getOutput().set(project.file("build/" + createSrgToMcp.getName() + "/output.srg"));
        });

        createMcpToSrg.configure(task -> {
            task.setReverse(true);
            task.dependsOn(extractSrg);
            task.getSrg().set(extractSrg.get().getOutput());
            task.getMappings().set(extension.getMappings());
        });

        extractNatives.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.getMeta().set(downloadMCMeta.get().getOutput());
            task.getOutput().set(nativesFolder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.getMeta().set(downloadMCMeta.get().getOutput());
        });

        final boolean doingUpdate = project.hasProperty("UPDATE_MAPPINGS");
        final String updateVersion = doingUpdate ? (String)project.property("UPDATE_MAPPINGS") : null;
        final String updateChannel = doingUpdate
            ? (project.hasProperty("UPDATE_MAPPINGS_CHANNEL") ? (String)project.property("UPDATE_MAPPINGS_CHANNEL") : "snapshot")
            : null;
        if (doingUpdate) {
            logger.lifecycle("This process uses Srg2Source for java source file renaming. Please forward relevant bug reports to https://github.com/MinecraftForge/Srg2Source/issues.");

            JavaCompile javaCompile = (JavaCompile) project.getTasks().getByName("compileJava");
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            Set<File> srcDirs = javaConv.getSourceSets().getByName("main").getJava().getSrcDirs();

            TaskProvider<DownloadMCPMappings> dlMappingsNew = project.getTasks().register("downloadMappingsNew", DownloadMCPMappings.class);
            TaskProvider<ExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", ExtractRangeMap.class);
            TaskProvider<ApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", ApplyRangeMap.class);
            TaskProvider<ApplyMappings> toMCPNew = project.getTasks().register("srg2mcpNew", ApplyMappings.class);
            TaskProvider<ExtractExistingFiles> extractMappedNew = project.getTasks().register("extractMappedNew", ExtractExistingFiles.class);

            extractRangeConfig.configure(task -> {
                task.getSources().from(srcDirs);
                task.getDependencies().from(javaCompile.getClasspath());
            });

            applyRangeConfig.configure(task -> {
                task.dependsOn(extractRangeConfig, createMcpToSrg);
                task.getRangeMap().set(extractRangeConfig.get().getOutput());
                task.getSrgFiles().from(createMcpToSrg.get().getOutput());
                task.getSources().from(srcDirs);
            });

            dlMappingsNew.configure(task -> {
                task.getMappings().set(updateChannel + "_" + updateVersion);
                task.getOutput().set(project.file("build/mappings_new.zip"));
            });

            toMCPNew.configure(task -> {
                task.dependsOn(dlMappingsNew, applyRangeConfig);
                task.getInput().set(applyRangeConfig.get().getOutput().get().getAsFile());
                task.getMappings().set(dlMappingsNew.get().getOutput());
            });

            extractMappedNew.configure(task -> {
                task.dependsOn(toMCPNew);
                task.getArchive().set(toMCPNew.get().getOutput());
                task.getTargets().from(srcDirs);
            });

            TaskProvider<DefaultTask> updateMappings = project.getTasks().register("updateMappings", DefaultTask.class);
            updateMappings.get().dependsOn(extractMappedNew);
        }

        project.afterEvaluate(p -> {
            MinecraftUserRepo mcrepo = null;
            DeobfuscatingRepo deobfrepo = null;

            DependencySet deps = minecraft.getDependencies();
            for (Dependency dep : new ArrayList<>(deps)) {
                if (!(dep instanceof ExternalModuleDependency))
                    throw new IllegalArgumentException("minecraft dependency must be a maven dependency.");
                if (mcrepo != null)
                    throw new IllegalArgumentException("Only allows one minecraft dependency.");
                deps.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), new ArrayList<>(extension.getAccessTransformers().getFiles()), extension.getMappings().get());
                String newDep = mcrepo.getDependencyString();
                //p.getLogger().lifecycle("New Dep: " + newDep);
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
                e.metadataSources(m -> {
                    m.gradleMetadata();
                    m.mavenPom();
                    m.artifact();
                });
            });

            if (!internalObfConfiguration.getDependencies().isEmpty()) {
                deobfrepo = new DeobfuscatingRepo(project, internalObfConfiguration, deobfuscator);
                if (deobfrepo.getResolvedOrigin() == null) {
                    project.getLogger().error("DeobfRepo attempted to resolve an origin repo early but failed, this may cause issues with some IDEs");
                }
            }
            remapper.attachMappings(extension.getMappings().get());

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                    .add(mcrepo)
                    .add(deobfrepo)
                    .add(MCPRepo.create(project))
                    .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                    .attach(project);

            MojangLicenseHelper.displayWarning(p, extension.getMappingChannel().get(), extension.getMappingVersion().get(), updateChannel, updateVersion);

            project.getRepositories().maven(e -> {
                e.setUrl(Utils.MOJANG_MAVEN);
                e.metadataSources(MetadataSources::artifact);
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
            if (mcrepo == null)
                throw new IllegalStateException("Missing 'minecraft' dependency entry.");
            mcrepo.validate(minecraft, extension.getRuns().getAsMap(), extractNatives.get(), downloadAssets.get(), createSrgToMcp.get()); //This will set the MC_VERSION property.

            String mcVer = (String) project.getExtensions().getExtraProperties().get("MC_VERSION");
            String mcpVer = (String) project.getExtensions().getExtraProperties().get("MCP_VERSION");
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().getMCVersion().set(mcVer);

            RenameJarInPlace reobfJar = reobf.create("jar");
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.getMappings().set(createMcpToSrg.get().getOutput());

            String assetIndex = mcVer;

            try {
                // Check meta exists
                if (!downloadMCMeta.get().getOutput().get().getAsFile().exists()) {
                    // Force download meta
                    downloadMCMeta.get().downloadMCMeta();
                }

                VersionJson json = Utils.loadJson(downloadMCMeta.get().getOutput().get().getAsFile(), VersionJson.class);

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
