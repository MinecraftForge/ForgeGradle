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
