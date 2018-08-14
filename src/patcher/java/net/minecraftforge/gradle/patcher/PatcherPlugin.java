package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.patcher.task.TaskSrg2Src;
import net.minecraftforge.gradle.mcp.MCPExtension;
import net.minecraftforge.gradle.mcp.MCPPlugin;
import net.minecraftforge.gradle.mcp.task.SetupMCPTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import net.minecraftforge.gradle.patcher.task.TaskMCPMapSource;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class PatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        PatcherExtension extension = project.getExtensions().create("patcher", PatcherExtension.class, project);

        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<TaskMCPMapSource> toMCPConfig = project.getTasks().register("srg2mcp", TaskMCPMapSource.class);
        TaskProvider<TaskSrg2Src> toSrgConfig = project.getTasks().register("mcp2srg", TaskSrg2Src.class, "mcp", "srg");
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);

        applyConfig.configure(task -> {
            task.setPatches(extension.patches);
            task.setOutput(() -> project.file("build/" + task.getName() + "/output.zip"));

        });
        toMCPConfig.configure(task -> {
            task.dependsOn(applyConfig);
        });
        genConfig.configure(task -> {
            task.dependsOn(toSrgConfig);
        });

        if (extension.parent != null) { //If there is no parent, then, well, they have to configure everything themselves.
             project.evaluationDependsOn(extension.parent.getPath());
             extension.parent.afterEvaluate(parent -> {
                 MCPPlugin mcp = parent.getPlugins().findPlugin(MCPPlugin.class);
                 PatcherPlugin patcher = parent.getPlugins().findPlugin(PatcherPlugin.class);
                 if (mcp != null) {
                     MCPExtension parentExt = parent.getExtensions().getByType(MCPExtension.class);
                     if (extension.getMappings() == null) {
                         extension.setMappings(parentExt.getMappings());
                     }

                     SetupMCPTask setupMCP = (SetupMCPTask)parent.getTasks().getByName("setupMCP");
                     applyConfig.configure( task -> {
                         task.dependsOn(setupMCP);
                         task.mustRunAfter(setupMCP);
                         task.setBase(setupMCP.getOutput());
                     });

                 } else if (patcher != null) {
                     PatcherExtension parentExt = parent.getExtensions().getByType(PatcherExtension.class);
                     if (extension.getMappings() == null) {
                         extension.setMappings(parentExt.getMappings());
                     }

                     TaskApplyPatches parentPatch = (TaskApplyPatches)parent.getTasks().getByName("applyPatches");
                     applyConfig.configure(task -> {
                         task.dependsOn(parentPatch);
                         task.setBase(parentPatch.getOutput());
                     });
                 } else {
                     throw new IllegalArgumentException("Parent must either be MCPPluigin or PatcherPlugin");
                 }
             });
        }
    }

}
