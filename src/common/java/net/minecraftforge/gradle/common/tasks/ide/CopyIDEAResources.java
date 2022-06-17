package net.minecraftforge.gradle.common.tasks.ide;

import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.nio.file.Path;

public abstract class CopyIDEAResources extends Copy {

    public void configure(IdeaModel model) {
        final Path outDir;
        if (model.getModule().getOutputDir() == null)
            outDir = model.getProject().getProject().file("out").toPath();
        else
            outDir = model.getModule().getOutputDir().toPath();
        for (final SourceSet sourceSet : model.getProject().getProject().getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
            dependsOn(sourceSet.getProcessResourcesTaskName());
            model.getProject().getProject().getTasks().named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class)
                    .configure(processResources -> {
                        final String outName = sourceSet.getName().equals(SourceSet.MAIN_SOURCE_SET_NAME) ? "production" : sourceSet.getName();
                        for (final File out : processResources.getOutputs().getFiles())
                            into(outDir.resolve(outName).resolve("resources")).from(out);
                    });
        }
    }
}
