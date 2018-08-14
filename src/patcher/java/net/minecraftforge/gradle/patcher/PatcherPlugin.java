package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.patcher.task.DownloadMCPMappingsTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import net.minecraftforge.gradle.patcher.task.TaskSrg2Src;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class PatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);

        TaskProvider<DownloadMCPMappingsTask> downloadMappings = project.getTasks().register("downloadMappings", DownloadMCPMappingsTask.class);
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<Copy> toMCPConfig = project.getTasks().register("srg2mcp", Copy.class);
        TaskProvider<TaskSrg2Src> toSrgConfig = project.getTasks().register("mcp2srg", TaskSrg2Src.class, "mcp", "srg");
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);

        downloadMappings.configure(task -> {
            task.setMappings(extension.getMappings());
        });
        applyConfig.configure(task -> {
            task.setClean(extension.cleanSrc);
            task.setPatches(extension.patches);
            task.finalizedBy(toMCPConfig);
        });
        toMCPConfig.configure(task -> {
            task.dependsOn(downloadMappings);
            task.eachFile(file -> {
                file.filter(line -> {
                    return line; // Apply replacements
                });
            });
            task.setOnlyIf(t -> extension.getMappings() != null);
        });
        toSrgConfig.configure(task -> {
            task.dependsOn(downloadMappings);
        });
        genConfig.configure(task -> {
            task.dependsOn(toSrgConfig);
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
