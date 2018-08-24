package net.minecraftforge.gradle.forgedev;

import net.minecraftforge.gradle.patcher.PatcherPlugin;
import net.minecraftforge.gradle.patcher.task.TaskGenerateBinPatches;
import net.minecraftforge.gradle.patcher.task.TaskReobfuscateJar;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;

import javax.annotation.Nonnull;

public class ForgeDevPlugin implements Plugin<Project> {

    @Override
    public void apply(@Nonnull Project project) {
        // Require patcher to be applied first
        if(!project.getPlugins().hasPlugin(PatcherPlugin.class)) {
            project.getPlugins().apply(PatcherPlugin.class);
        }

        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);

        TaskProvider<TaskGenerateBinPatches> genJoinedBinPatches = (TaskProvider) project.getTasks().named("genJoinedBinPatches");
        TaskProvider<TaskGenerateBinPatches> genClientBinPatches = (TaskProvider) project.getTasks().named("genClientBinPatches");
        TaskProvider<TaskGenerateBinPatches> genServerBinPatches = (TaskProvider) project.getTasks().named("genServerBinPatches");
        TaskProvider<Zip> packageSrcPatches = (TaskProvider) project.getTasks().named("packageSrcPatches");
        TaskProvider<TaskReobfuscateJar> reobfJar = (TaskProvider) project.getTasks().named("reobfJar");

        TaskProvider<Jar> sourcesJar = project.getTasks().register("sourcesJar", Jar.class);
        TaskProvider<Jar> universalJar = project.getTasks().register("universalJar", Jar.class);
        TaskProvider<Jar> userdevJar = project.getTasks().register("userdevJar", Jar.class);
        TaskProvider<DefaultTask> release = project.getTasks().register("release", DefaultTask.class);

        sourcesJar.configure(task -> {
            task.dependsOn("classes");
            task.setClassifier("sources");
            task.from(javaConvention.getSourceSets().getByName("main").getAllSource());
        });
        project.getArtifacts().add("archives", sourcesJar);

        universalJar.configure(task -> {
            task.dependsOn(reobfJar, genClientBinPatches, genServerBinPatches);
            task.from(project.zipTree(reobfJar.get().getOutput())); // Not sure if there's a better way to do this
            task.from(genClientBinPatches.get().getOutput());
            task.from(genServerBinPatches.get().getOutput());
        });
        userdevJar.configure(task -> {
            task.dependsOn(genJoinedBinPatches, packageSrcPatches);
            task.from(genJoinedBinPatches.get().getOutput());
            task.from(packageSrcPatches.get().getArchivePath());
            // TODO: Add Forge tsrg, exc and AT
//            Stream.of("forge.tsrg", "forge.exc", "forge_at.cfg")
//                    .map(project::file).filter(File::exists).forEach(task::from);
            // TODO: Generate and add environment.json (namely the mcpconfig version)
        });

        release.configure(task -> {
            task.dependsOn(sourcesJar, universalJar, userdevJar);
        });
    }

}
