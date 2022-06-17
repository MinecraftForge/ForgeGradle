package net.minecraftforge.gradle.common.tasks.ide;

import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.nio.file.Path;

public abstract class CopyIDEAResources extends BaseCopyResourcesTask {

    public void configure(IdeaModel model) {
        if (model.getModule().getOutputDir() == null)
            return;
        final Path outDir = model.getModule().getOutputDir().toPath();
        for (final SourceSet sourceSet : model.getProject().getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
            dependsOn(sourceSet.getProcessResourcesTaskName());
            final ProcessResources processResources = (ProcessResources) model.getProject().getProject().getTasks().getByName(sourceSet.getProcessResourcesTaskName());
            for (final File out : processResources.getOutputs().getFiles())
                toCopy.put(out, outDir.resolve(sourceSet.getName()).resolve("resources").toFile());
        }
    }
}
