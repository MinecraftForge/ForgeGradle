package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.patcher.task.DownloadMCMetaTask;
import net.minecraftforge.gradle.patcher.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.patcher.task.ListDependenciesTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyMappings;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskApplyRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskExtractRangeMap;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import java.util.Collections;

public class PatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);

        TaskProvider<DownloadMCPMappingsTask> dlMappingsConfig = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<DownloadMCMetaTask> dlMCMetaConfig = project.getTasks().register("downloadMCMeta", DownloadMCMetaTask.class);
        TaskProvider<ListDependenciesTask> listDepsConfig = project.getTasks().register("listDependencies", ListDependenciesTask.class);
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
            task.dependsOn(listDepsConfig);
            task.setSources(Collections.singleton(extension.cleanSrc));
            task.setDependencies(listDepsConfig.get().getOutput());
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

//        if (extension.parent != null) { //If there is no parent, then, well, they have to configure everything themselves.
//            project.evaluationDependsOn(extension.parent.getPath());
//            extension.parent.afterEvaluate(parent -> {
//                MCPPlugin mcp = parent.getPlugins().findPlugin(MCPPlugin.class);
//                PatcherPlugin patcher = parent.getPlugins().findPlugin(PatcherPlugin.class);
//                if (mcp != null) {
//                    MCPExtension parentExt = parent.getExtensions().getByType(MCPExtension.class);
//                    if (extension.getMappings() == null) {
//                        extension.setMappings(parentExt.getMappings());
//                    }
//
//                    SetupMCPTask setupMCP = (SetupMCPTask) parent.getTasks().getByName("setupMCP");
//                    applyConfig.configure(task -> {
//                        task.dependsOn(setupMCP);
//                        task.mustRunAfter(setupMCP);
//                        task.setBase(setupMCP.getOutput());
//                    });
//
//                } else if (patcher != null) {
//                    PatcherExtension parentExt = parent.getExtensions().getByType(PatcherExtension.class);
//                    if (extension.getMappings() == null) {
//                        extension.setMappings(parentExt.getMappings());
//                    }
//
//                    TaskApplyPatches parentPatch = (TaskApplyPatches) parent.getTasks().getByName("applyPatches");
//                    applyConfig.configure(task -> {
//                        task.dependsOn(parentPatch);
//                        task.setBase(parentPatch.getOutput());
//                    });
//                } else {
//                    throw new IllegalArgumentException("Parent must either be MCPPluigin or PatcherPlugin");
//                }
//            });
//        }
    }

}
