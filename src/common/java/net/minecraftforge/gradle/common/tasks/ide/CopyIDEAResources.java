/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
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

public abstract class CopyIDEAResources extends Copy {
    public static final String NAME = "copyIdeaResources";

    public void configure(IdeaModel model, Project project) {
        // We don't need the destination, but it's not optional
        setDestinationDir(new File(project.getBuildDir(), getName()));
        final Path destination = getDestinationDir().toPath();

        for (final SourceSet sourceSet : project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets()) {
            dependsOn(sourceSet.getProcessResourcesTaskName());
            final ProcessResources processResources = project.getTasks().named(sourceSet.getProcessResourcesTaskName(), ProcessResources.class).get();
            final String outName = Utils.getIntellijOutName(sourceSet);
            final String outPath = IntellijRunGenerator.getIdeaPathsForSourceset(project, model, outName, null)
                    // Resources are first
                    .findFirst().orElseGet(() -> new File(model.getModule().getOutputDir(), outName + "/resources").getAbsolutePath());
            for (final File out : processResources.getOutputs().getFiles()) {
                final CopySpec spec = getMainSpec().addChild();
                spec.into(destination.relativize(Paths.get(outPath)).toString());
                spec.from(out);
            }
        }
    }
}
