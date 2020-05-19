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

package net.minecraftforge.gradle.common.util;

import net.minecraftforge.gradle.common.task.ExtractNatives;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.plugins.ide.api.XmlFileContentMerger;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class EclipseHacks {

    public static void doEclipseFixes(@Nonnull final MinecraftExtension minecraft, @Nonnull final ExtractNatives nativesTask, @Nonnull final List<? extends Task> setupTasks) {
        final Project project = minecraft.getProject();
        final File natives = nativesTask.getOutput();

        final EclipseModel eclipseConv = (EclipseModel)project.getExtensions().findByName("eclipse");
        if (eclipseConv == null) {
            // The eclipse plugin hasn't been applied; we don't need to do any eclipse things
            return;
        }
        final XmlFileContentMerger classpathMerger = eclipseConv.getClasspath().getFile();

        final String LIB_ATTR = "org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY";

        project.getTasks().withType(GenerateEclipseClasspath.class, task -> {
            task.dependsOn(nativesTask);
            setupTasks.forEach(task::dependsOn);
        });

        classpathMerger.whenMerged(obj -> {
            Classpath classpath = (Classpath)obj;
            Set<String> paths = new HashSet<>();
            Iterator<ClasspathEntry> itr = classpath.getEntries().iterator();
            while (itr.hasNext()) {
                ClasspathEntry entry = itr.next();
                if (entry instanceof SourceFolder) {
                    SourceFolder sf = (SourceFolder)entry;
                    if (!paths.add(sf.getPath())) {
                        //Eclipse likes to duplicate things... No idea why, let's kill them off
                        itr.remove();
                        continue;
                    }

                    if (!sf.getEntryAttributes().containsKey(LIB_ATTR)) {
                        sf.getEntryAttributes().put(LIB_ATTR, natives.getAbsolutePath());
                    }
                }
            }
        });
    }
}
