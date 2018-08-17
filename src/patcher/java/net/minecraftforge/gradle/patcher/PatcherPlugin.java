package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.Library;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.task.DownloadMCPConfigTask;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCMetaTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyMappings;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskCreateExc;
import net.minecraftforge.gradle.patcher.task.TaskCreateSrg;
import net.minecraftforge.gradle.patcher.task.TaskExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskExtractMCPData;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PatcherPlugin implements Plugin<Project> {
    private static final Gson GSON = new GsonBuilder().create();
    private static final String MC_DEP_CONFIG = "compile"; //TODO: Separate config?
    private static final String BASE_SOURCE = "base";

    @Override
    public void apply(@Nonnull Project project) {
        final PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);
        if (project.getPluginManager().findPlugin("java") == null) {
            project.getPluginManager().apply("java");
        }
        final JavaPluginConvention javaConv = (JavaPluginConvention)project.getConvention().getPlugins().get("java");

        TaskProvider<DownloadMCPMappingsTask> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<DownloadMCMetaTask> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMetaTask.class);
        TaskProvider<DefaultTask> injectClasspath = project.getTasks().register("injectClasspath", DefaultTask.class);
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<TaskApplyMappings> toMCPConfig = project.getTasks().register("srg2mcp", TaskApplyMappings.class);
        TaskProvider<Copy> extractMapped = project.getTasks().register("extractMapped", Copy.class);
        TaskProvider<TaskCreateSrg> createSrg = project.getTasks().register("createMcp2Srg", TaskCreateSrg.class);
        TaskProvider<TaskCreateExc> createExc = project.getTasks().register("createExc", TaskCreateExc.class);
        TaskProvider<TaskExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", TaskExtractRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMapBase", TaskApplyRangeMap.class);
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);

        //Add the patched output to a new sourceset so we can tell the difference when creating patches.
        SourceSet baseSource = javaConv.getSourceSets().maybeCreate(BASE_SOURCE);

        dlMappingsConfig.configure(task -> {
            task.setMappings(extension.getMappings());
        });
        dlMCMetaConfig.configure(task -> {
            task.setMcVersion(extension.mcVersion);
        });
        injectClasspath.configure(task -> {
            task.dependsOn(dlMCMetaConfig.get());
            task.getOutputs().upToDateWhen(a -> false);
            task.doFirst(p -> {
                try (InputStream input = new FileInputStream(dlMCMetaConfig.get().getOutput())) {
                    VersionJson json = GSON.fromJson(new InputStreamReader(input), VersionJson.class);
                    for (Library lib : json.libraries) {
                        project.getDependencies().add(MC_DEP_CONFIG, lib.name);
                    }
                    project.getDependencies().add(MC_DEP_CONFIG, "com.google.code.findbugs:jsr305:3.0.1"); //TODO: This is included in MCPConfig, Need to feed that as input to this...
                    project.getConfigurations().getByName(MC_DEP_CONFIG).resolve(); //TODO: How to let gradle auto-resolve these?
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        applyConfig.configure(task -> {
            task.setPatches(extension.patches);
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyConfig);
            task.setInput(applyConfig.get().getOutput());
            task.setMappings(dlMappingsConfig.get().getOutput());
        });
        extractMapped.configure(task -> {
            task.dependsOn(toMCPConfig);
            task.from(project.zipTree(toMCPConfig.get().getOutput()));
            task.into(extension.patchedSrc);
        });
        extractRangeConfig.configure(task -> {
            Jar jar = (Jar)project.getTasks().getByName("jar");
            task.dependsOn(injectClasspath, jar);

            Set<File> src = new HashSet<>();
            javaConv.getSourceSets().stream()
            .filter(s -> s.getName().toLowerCase(Locale.ENGLISH).startsWith("test"))
            .forEach(s -> src.addAll(s.getJava().getSrcDirs()));

            task.setSources(src);
            task.addDependencies(project.getConfigurations().getByName(MC_DEP_CONFIG));
            task.addDependencies(jar.getArchivePath());
        });

        createSrg.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
        });
        createExc.configure(task -> {
            task.dependsOn(dlMappingsConfig);
            task.setMappings(dlMappingsConfig.get().getOutput());
        });

        applyRangeConfig.configure(task -> {
            task.dependsOn(extractRangeConfig, createSrg, createExc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            task.setSrgFiles(createSrg.get().getOutput());
            task.setExcFiles(createExc.get().getOutput());
        });
        genConfig.configure(task -> {
            task.dependsOn(applyRangeConfig);
            task.setOnlyIf(t -> extension.patches != null);
            task.setModified(applyRangeConfig.get().getOutput());
            task.setPatches(extension.patches);
        });

        project.afterEvaluate(p -> {

            //Add Known repos
            project.getRepositories().maven(e -> {
                e.setUrl("https://libraries.minecraft.net/");
                e.metadataSources(src -> src.artifact());
            });
            project.getRepositories().maven(e -> {
                e.setUrl("http://files.minecraftforge.net/maven/");
            });

            //Add PatchedSrc as a new source set
            baseSource.java(v -> { v.srcDir(extension.patchedSrc); });
            baseSource.resources(v -> { }); //TODO: Asset downloading, needs asset index from json.
            applyRangeConfig.get().setSources(baseSource.getJava().getSrcDirs());

            if (extension.patches != null && !extension.patches.exists()) { //Auto-make folders so that gradle doesnt explode some tasks.
                extension.patches.mkdirs();
            }

            if (extension.parent != null) { //Most of this is done after evaluate, and checks for nulls to allow the build script to override us. We can't do it in the config step because if someone configs a task in the build script it resolves our config during evaluation.
                TaskContainer tasks = extension.parent.getTasks();
                MCPPlugin mcp = extension.parent.getPlugins().findPlugin(MCPPlugin.class);
                PatcherPlugin patcher = extension.parent.getPlugins().findPlugin(PatcherPlugin.class);

                if (mcp != null) {

                    if (extension.cleanSrc == null) {
                        extension.cleanSrc = ((SetupMCPTask)tasks.getByName("setupMCP")).getOutput();
                        applyConfig.get().dependsOn(tasks.getByName("setupMCP"));
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
                    }

                    File mcpConfig = ((DownloadMCPConfigTask)tasks.getByName("downloadConfig")).getOutput();

                    if (createSrg.get().getSrg() == null) {
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractSrg", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        createSrg.get().setSrg(ext.get().getOutput());
                        createSrg.get().dependsOn(ext);
                    }

                    if (createExc.get().getSrg() == null) {
                        createExc.get().setSrg(createSrg.get().getSrg());
                        createExc.get().dependsOn(createSrg);
                    }

                    if (createExc.get().getStatics() == null) {
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractStatic", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        ext.get().setKey("statics");
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setStatics(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }

                    if (createExc.get().getConstructors() == null) {
                        TaskProvider<TaskExtractMCPData> ext = project.getTasks().register("extractConstructors", TaskExtractMCPData.class);
                        ext.get().setConfig(mcpConfig);
                        ext.get().setKey("constructors");
                        ext.get().setOutput(project.file("build/" + ext.get().getName() + "/output.txt"));
                        createExc.get().setConstructors(ext.get().getOutput());
                        createExc.get().dependsOn(ext);
                    }


                } else if (patcher != null) {
                    PatcherExtension pExt = extension.parent.getExtensions().getByType(PatcherExtension.class);
                    extension.copyFrom(pExt);

                    if (dlMappingsConfig.get().getMappings() == null) {
                        dlMappingsConfig.get().setMappings(extension.getMappings());
                    }

                    if (extension.cleanSrc == null) {
                        TaskApplyPatches task = (TaskApplyPatches)tasks.getByName(applyConfig.get().getName());
                        extension.cleanSrc = task.getOutput();
                        applyConfig.get().dependsOn(task);
                    }
                    if (applyConfig.get().getClean() == null) {
                        applyConfig.get().setClean(extension.cleanSrc);
                    }
                    if (genConfig.get().getClean() == null) {
                        genConfig.get().setClean(extension.cleanSrc);
                    }

                    if (createSrg.get().getSrg() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createSrg.get().setSrg(extract.getOutput());
                            createSrg.get().dependsOn(extract);
                        } else {
                            TaskCreateSrg task = (TaskCreateSrg)tasks.getByName(createSrg.get().getName());
                            createSrg.get().setSrg(task.getSrg());
                            createSrg.get().dependsOn(task);
                        }
                    }

                    if (createExc.get().getSrg() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractSrg"));
                        if (extract != null) {
                            createExc.get().setSrg(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateSrg task = (TaskCreateSrg)tasks.getByName(createExc.get().getName());
                            createExc.get().setSrg(task.getSrg());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getStatics() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractStatic"));
                        if (extract != null) {
                            createExc.get().setStatics(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateExc task = (TaskCreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setStatics(task.getStatics());
                            createExc.get().dependsOn(task);
                        }
                    }
                    if (createExc.get().getConstructors() == null) {
                        TaskExtractMCPData extract = ((TaskExtractMCPData)tasks.getByName("extractConstructors"));
                        if (extract != null) {
                            createExc.get().setConstructors(extract.getOutput());
                            createExc.get().dependsOn(extract);
                        } else {
                            TaskCreateExc task = (TaskCreateExc)tasks.getByName(createExc.get().getName());
                            createExc.get().setConstructors(task.getConstructors());
                            createExc.get().dependsOn(task);
                        }
                    }
                } else {
                    throw new IllegalStateException("Parent must either be a Patcher or MCP project");
                }
            }

            //Make sure tasks that require a valid classpath happen after making the classpath
            p.getTasks().withType(GenerateEclipseClasspath.class, t -> { t.dependsOn(injectClasspath); });
            p.getTasks().withType(AbstractCompile.class, t -> { t.dependsOn(injectClasspath); });
        });
    }

}
