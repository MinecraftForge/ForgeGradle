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
import net.minecraftforge.gradle.common.util.*;
import net.minecraftforge.gradle.mcp.MCPRepo;
import net.minecraftforge.gradle.userdev.tasks.GenerateSRG;
import net.minecraftforge.gradle.userdev.tasks.RenameJarInPlace;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UserDevPlugin implements Plugin<Project> {
    private static String MINECRAFT = "minecraft";
    private static String DEOBF = "deobf";

    @Override
    public void apply(@Nonnull Project project) {
        @SuppressWarnings("unused")
        final Logger logger = project.getLogger();
        final UserDevExtension extension = project.getExtensions().create("minecraft", UserDevExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final File natives_folder = project.file("build/natives/");
        JavaPluginConvention java = (JavaPluginConvention)project.getConvention().getPlugins().get("java");

        NamedDomainObjectContainer<RenameJarInPlace> reobf = project.container(RenameJarInPlace.class, new NamedDomainObjectFactory<RenameJarInPlace>() {
            @Override
            public RenameJarInPlace create(String jarName) {
                String name = Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);

                final RenameJarInPlace task = project.getTasks().maybeCreate("reobf" + name, RenameJarInPlace.class);
                task.setClasspath(java.getSourceSets().getByName("main").getCompileClasspath());

                project.getTasks().getByName("assemble").dependsOn(task);

                // do after-Evaluate resolution, for the same of good error reporting
                project.afterEvaluate(p -> {
                    Task jar = project.getTasks().getByName(jarName);
                    if (!(jar instanceof Jar))
                        throw new IllegalStateException(jarName + "  is not a jar task. Can only reobf jars!");
                    task.setInput(((Jar)jar).getArchivePath());
                    task.dependsOn(jar);
                });

                return task;
            }
        });
        project.getExtensions().add("reobf", reobf);

        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceSet test = java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME);
        SourceSet api = java.getSourceSets().maybeCreate("api");

        Configuration minecraft = project.getConfigurations().maybeCreate(MINECRAFT);
        Configuration compile = project.getConfigurations().maybeCreate(main.getCompileConfigurationName());
        Configuration apiCompile = project.getConfigurations().maybeCreate(api.getCompileConfigurationName());
        Configuration testCompile = project.getConfigurations().maybeCreate(test.getCompileConfigurationName());
        Configuration deobf = project.getConfigurations().maybeCreate(DEOBF);

        compile.extendsFrom(minecraft);
        compile.extendsFrom(deobf);
        apiCompile.extendsFrom(compile);
        testCompile.extendsFrom(apiCompile);

        Stream.of(main, test).forEach(sourceSet -> {
            sourceSet.setCompileClasspath(sourceSet.getCompileClasspath().plus(api.getOutput()));
            sourceSet.setRuntimeClasspath(sourceSet.getRuntimeClasspath().plus(api.getOutput()));
        });

        TaskProvider<DownloadMavenArtifact> downloadMcpConfig = project.getTasks().register("downloadMcpConfig", DownloadMavenArtifact.class);
        TaskProvider<ExtractMCPData> extractSrg = project.getTasks().register("extractSrg", ExtractMCPData.class);
        TaskProvider<GenerateSRG> createMcpToSrg = project.getTasks().register("createMcpToSrg", GenerateSRG.class);
        TaskProvider<DownloadMCMeta> downloadMCMeta = project.getTasks().register("downloadMCMeta", DownloadMCMeta.class);
        TaskProvider<ExtractNatives> extractNatives = project.getTasks().register("extractNatives", ExtractNatives.class);
        TaskProvider<DownloadAssets> downloadAssets = project.getTasks().register("downloadAssets", DownloadAssets.class);

        extractSrg.configure(task -> {
            task.dependsOn(downloadMcpConfig);
            task.setConfig(downloadMcpConfig.get().getOutput());
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
            task.setOutput(natives_folder);
        });
        downloadAssets.configure(task -> {
            task.dependsOn(downloadMCMeta.get());
            task.setMeta(downloadMCMeta.get().getOutput());
        });

        project.afterEvaluate(p -> {
            MinecraftUserRepo mcrepo = null;
            ModRemapingRepo deobfrepo = null;

            //TODO: UserDevRepo deobf = new UserDevRepo(project);

            DependencySet deps = minecraft.getDependencies();
            for (Dependency dep : deps.stream().collect(Collectors.toList())) {
                if (!(dep instanceof ExternalModuleDependency))
                    throw new IllegalArgumentException("minecraft dependency must be a maven dependency.");
                if (mcrepo != null)
                    throw new IllegalArgumentException("Only allows one minecraft dependancy.");
                deps.remove(dep);

                mcrepo = new MinecraftUserRepo(p, dep.getGroup(), dep.getName(), dep.getVersion(), extension.getAccessTransformers(), extension.getMappings());
                String newDep = mcrepo.getDependencyString();
                p.getLogger().lifecycle("New Dep: " + newDep);
                ExternalModuleDependency ext = (ExternalModuleDependency)p.getDependencies().create(newDep);
                {
                    ext.setChanging(true); //TODO: Remove when not in dev
                    minecraft.resolutionStrategy(strat -> {
                        strat.cacheChangingModulesFor(0, TimeUnit.MINUTES);
                    });
                }
                minecraft.getDependencies().add(ext);
            }

            deps = deobf.getDependencies();
            for (Dependency dep : deps.stream().collect(Collectors.toList())) {
                if (!(dep instanceof ExternalModuleDependency)) //TODO: File deps as well.
                    throw new IllegalArgumentException("deobf dependency must be a maven dependency. File deps are on the TODO");
                deps.remove(dep);

                if (deobfrepo == null)
                    deobfrepo = new ModRemapingRepo(p, extension.getMappings());
                String newDep = deobfrepo.addDep(dep.getGroup(), dep.getName(), dep.getVersion()); // Classifier?
                deobf.getDependencies().add(p.getDependencies().create(newDep));
            }

            // We have to add these AFTER our repo so that we get called first, this is annoying...
            new BaseRepo.Builder()
                .add(mcrepo)
                .add(deobfrepo)
                .add(MCPRepo.create(project))
                .add(MinecraftRepo.create(project)) //Provides vanilla extra/slim/data jars. These don't care about OBF names.
                .attach(project);
            project.getRepositories().maven(e -> {
                e.setUrl("http://files.minecraftforge.net/maven/");
            });
            project.getRepositories().maven(e -> {
                e.setUrl("https://libraries.minecraft.net/");
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().mavenCentral(); //Needed for MCP Deps
            if (mcrepo == null)
                throw new IllegalStateException("Missing 'minecraft' dependency entry.");
            mcrepo.validate(); //This will set the MC_VERSION property.

            String mcVer = (String)project.getExtensions().getExtraProperties().get("MC_VERSION");
            String mcpVer = (String)project.getExtensions().getExtraProperties().get("MCP_VERSION");
            downloadMcpConfig.get().setArtifact("de.oceanlabs.mcp:mcp_config:" + mcpVer + "@zip");
            downloadMCMeta.get().setMCVersion(mcVer);

            RenameJarInPlace reobfJar  = reobf.create("jar");
            reobfJar.dependsOn(createMcpToSrg);
            reobfJar.setMappings(createMcpToSrg.get().getOutput());

            createRunConfigsTasks(project, extractNatives.get(), downloadAssets.get(), extension.getRunConfigs());
        });
    }

    private void createRunConfigsTasks(@Nonnull Project project, ExtractNatives extractNatives, DownloadAssets downloadAssets, List<RunConfig> runs)
    {
        // Utility task to abstract the prerequisites when using the intellij run generation
        TaskProvider<Task> prepareRun = project.getTasks().register("prepareRun", Task.class);
        prepareRun.configure(task -> {
            task.dependsOn(project.getTasks().getByName("classes"), extractNatives, downloadAssets);
        });

        runs.forEach(runConfig -> {
            String taskName = runConfig.getName().replaceAll("[^a-zA-Z0-9\\-_]","");
            if (!taskName.startsWith("run"))
                taskName = "run" + taskName.substring(0,1).toUpperCase() + taskName.substring(1);
            TaskProvider<JavaExec> runTask = project.getTasks().register(taskName, JavaExec.class);
            runTask.configure(task -> {
                task.dependsOn(prepareRun.get());

                task.setMain(runConfig.getMain());
                task.setArgs(runConfig.getArgs());
                task.setSystemProperties(runConfig.getProperties());
                task.setEnvironment(runConfig.getEnvironment());

                String workDir = runConfig.getWorkingDirectory();
                File file = new File(workDir);
                if(!file.exists())
                    file.mkdirs();

                task.setWorkingDir(workDir);

                JavaPluginConvention java = (JavaPluginConvention)project.getConvention().getPlugins().get("java");
                task.setClasspath(java.getSourceSets().getByName("main").getRuntimeClasspath());
            });
        });

        EclipseHacks.doEclipseFixes(project, extractNatives, downloadAssets, runs);
        IntellijUtils.createIntellijRunsTask(project, extractNatives, downloadAssets, prepareRun.get(), runs);
    }
}
