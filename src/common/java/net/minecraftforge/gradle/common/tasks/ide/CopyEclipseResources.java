/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.tasks.ide;

import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class CopyEclipseResources extends Copy {
    public static final String NAME = "copyEclipseResources";

    public CopyEclipseResources() {
        this.getOutputs().upToDateWhen(task -> false);
    }

    public void configure(EclipseModel model, Project project) {
        // We don't need the destination, but it's not optional
        setDestinationDir(new File(project.getLayout().getBuildDirectory().getAsFile().get(), getName()));
        final Path destination = getDestinationDir().toPath();

        final Map<SourceSet, SourceFolder> srcToOut = model.getClasspath().resolveDependencies().stream()
                .filter(SourceFolder.class::isInstance)
                .map(SourceFolder.class::cast)
                .map(folder -> new SrcSetEntry(getSourceSetFromFolder(folder, project), folder))
                .filter(entry -> entry.srcSet != null)
                .distinct()
                .collect(Collectors.toMap(f -> f.srcSet, f -> f.source));
        srcToOut.forEach((src, out) -> {
            dependsOn(src.getProcessResourcesTaskName());
            final ProcessResources processResources = project.getTasks().named(src.getProcessResourcesTaskName(), ProcessResources.class).get();
            final CopySpec spec = getMainSpec().addChild();
            spec.into(destination.relativize(project.file(out.getOutput()).toPath()).toString());
            spec.with(processResources.getRootSpec());
            // Eclipse MAY have multiple sourcesets have the same output, and a sourceset may include resources from another (datagen sourcesets)
            spec.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
        });
    }

    private static SourceSet getSourceSetFromFolder(SourceFolder folder, Project project) {
        final Path in = project.file(folder.getPath()).toPath();
        final JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        for (final SourceSet src : java.getSourceSets()) {
            if (src.getResources().getSrcDirs().stream()
                    .map(File::toPath)
                    .anyMatch(path -> path.endsWith(in))) {
                return src;
            }
        }
        return null;
    }

    private static final class SrcSetEntry {
        public final SourceSet srcSet;
        public final SourceFolder source;

        private SrcSetEntry(SourceSet srcSet, SourceFolder source) {
            this.srcSet = srcSet;
            this.source = source;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SrcSetEntry that = (SrcSetEntry) o;
            return that.srcSet == this.srcSet;
        }

        @Override
        public int hashCode() {
            return Objects.hash(srcSet);
        }
    }
}
