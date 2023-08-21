/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks.ide;

import net.minecraftforge.gradle.common.util.Utils;
import net.minecraftforge.gradle.common.util.runs.IntellijRunGenerator;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class CopyIntellijResources extends Copy {
    public static final String NAME = "copyIntellijResources";

    public CopyIntellijResources() {
        this.getOutputs().upToDateWhen(task -> false);
    }

    public void configure(IdeaModel model, Project project) {
        // We don't need the destination, but it's not optional
        setDestinationDir(new File(project.getLayout().getBuildDirectory().getAsFile().get(), getName()));
        final Path destination = getDestinationDir().toPath();

        for (final SourceSet sourceSet : project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
            dependsOn(sourceSet.getProcessResourcesTaskName());
            final ProcessResources processResources = project.getTasks().named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class).get();
            final String outName = Utils.getIntellijOutName(sourceSet);
            final String outPath = IntellijRunGenerator.getIdeaPathsForSourceset(project, model, outName, null)
                    // Resources are first
                    .findFirst().orElseGet(() -> new File(model.getModule().getOutputDir(), outName + "/resources").getAbsolutePath());
            final CopySpec spec = getMainSpec().addChild();
            spec.into(destination.relativize(Paths.get(outPath)).toString());
            spec.with(processResources.getRootSpec());
        }
    }
}
