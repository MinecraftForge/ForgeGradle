package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.patcher.task.Srg2SrcTask;
import net.minecraftforge.gradle.patcher.task.TaskApplyPatches;
import net.minecraftforge.gradle.patcher.task.TaskGeneratePatches;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;

public class PatcherPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        project.getExtensions().create("patcher", PatcherExtension.class, project);

        TaskProvider<Srg2SrcTask> toMCPConfig = project.getTasks().register("srg2mcp", Srg2SrcTask.class, "srg", "mcp");
        TaskProvider<TaskApplyPatches> applyConfig = project.getTasks().register("applyPatches", TaskApplyPatches.class);
        TaskProvider<Srg2SrcTask> toSrgConfig = project.getTasks().register("mcp2srg", Srg2SrcTask.class, "mcp", "srg");
        TaskProvider<TaskGeneratePatches> genConfig = project.getTasks().register("genPatches", TaskGeneratePatches.class);

        applyConfig.configure(task -> {
            task.dependsOn(toMCPConfig);
        });
        genConfig.configure(task -> {
            task.dependsOn(toSrgConfig);
        });
    }

}
