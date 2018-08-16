package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.VersionJson;
import net.minecraftforge.gradle.common.util.VersionJson.Library;
import net.minecraftforge.gradle.common.util.VersionJson.OS;
import net.minecraftforge.gradle.patcher.task.DownloadMCMetaTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.patcher.task.ListDependenciesTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyMappings;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PatcherPlugin implements Plugin<Project> {
    private static final Gson GSON = new GsonBuilder().create();

    @Override
    public void apply(@Nonnull Project project) {
        PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);

        TaskProvider<DownloadMCPMappingsTask> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<DownloadMCMetaTask> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMetaTask.class);
        TaskProvider<ListDependenciesTask> listDepsConfig = project.getTasks().register("listDependencies", ListDependenciesTask.class);
        TaskProvider<DefaultTask> injectClasspath = project.getTasks().register("injectClasspath", DefaultTask.class);
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<TaskApplyMappings> toMCPConfig = project.getTasks().register("srg2mcp", TaskApplyMappings.class);
        TaskProvider<TaskExtractRangeMap> extractRangeConfig = project.getTasks().register("extractRangeMap", TaskExtractRangeMap.class);
        TaskProvider<TaskApplyRangeMap> applyRangeConfig = project.getTasks().register("applyRangeMap", TaskApplyRangeMap.class);
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);

        dlMappingsConfig.configure(task -> {
            task.setMappings(extension.getMappings());
        });
        dlMCMetaConfig.configure(task -> {
            task.setMcVersion(extension.mcVersion);
        });
        listDepsConfig.configure(task -> {
            task.dependsOn(dlMCMetaConfig);
            task.setVersionMeta(dlMCMetaConfig.get().getOutput());
        });
        injectClasspath.configure(task -> {
            task.getOutputs().upToDateWhen(a -> false);
            task.doFirst(p -> {
                project.getRepositories().maven(e -> {
                    e.setUrl("https://libraries.minecraft.net/");
                    e.metadataSources(src -> src.artifact());
                });
                try (InputStream input = new FileInputStream(dlMCMetaConfig.get().getOutput())) {
                    VersionJson json = GSON.fromJson(new InputStreamReader(input), VersionJson.class);
                    for (Library lib : json.libraries) {
                        project.getDependencies().add("compile", lib.name);
                    }
                    project.getDependencies().add("compile", "com.google.code.findbugs:jsr305:3.0.1"); //TODO: This is included in MCPConfig, Need to feed that as input to this...
                    project.getConfigurations().getByName("compile").resolve(); //TODO: How to let gradle auto-resolve these?
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        });
        applyConfig.configure(task -> {
            task.finalizedBy(toMCPConfig);
            task.setOnlyIf(t -> extension.patches != null);

            task.setClean(extension.cleanSrc);
            task.setPatches(extension.patches);
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(dlMappingsConfig, applyConfig);
            task.setOnlyIf(t -> extension.getMappings() != null);

            task.setInput(applyConfig.get().getOutput());
            task.setMappings(dlMappingsConfig.get().getOutput());
            task.setOutput(extension.patchedSrc);
        });
        extractRangeConfig.configure(task -> {
            Jar jar = (Jar)project.getTasks().getByName("jar");
            task.dependsOn(listDepsConfig, jar);
            JavaPluginConvention javaConv = (JavaPluginConvention)project.getConvention().getPlugins().get("java");
            Set<File> src = new HashSet<>();
            for (SourceSet set : javaConv.getSourceSets()) {
                if (!set.getName().toLowerCase(Locale.ENGLISH).equals("test")) {
                    src.addAll(set.getJava().getSrcDirs());
                }
            }
            task.setSources(src);
            task.addDependencies(listDepsConfig.get().getOutput());
            task.addDependencies(jar.getArchivePath());
        });
        applyRangeConfig.configure(task -> {
            task.dependsOn(extractRangeConfig);

            task.setSources(extension.patchedSrc);
            task.setRangeMap(extractRangeConfig.get().getOutput());
            // TODO: Add support for extra mappings and EXCs from the extension
            // task.setMappings();
            // task.setExcs();
        });
        genConfig.configure(task -> {
            task.dependsOn(applyRangeConfig);
            task.setOnlyIf(t -> extension.patches != null);

            task.setClean(extension.cleanSrc);
            task.setModified(applyRangeConfig.get().getOutput());
            task.setPatches(extension.patches);
        });

        project.afterEvaluate(p -> {
            p.getTasks().withType(GenerateEclipseClasspath.class, t -> { t.dependsOn(injectClasspath); });
            p.getTasks().withType(AbstractCompile.class, t -> { t.dependsOn(injectClasspath); });
        });
    }

}
