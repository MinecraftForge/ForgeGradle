package net.minecraftforge.gradle.common.tasks.ide;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.jvm.tasks.ProcessResources;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.plugins.ide.eclipse.model.SourceFolder;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public abstract class CopyEclipseResources extends BaseCopyResourcesTask {

    public void configure(EclipseModel model, Project project) {
        final Map<SourceSet, SourceFolder> srcToOut = model.getClasspath().resolveDependencies().stream()
                .filter(SourceFolder.class::isInstance)
                .map(SourceFolder.class::cast)
                .map(folder -> new SrcSetEntry(getSourceSetFromFolder(folder, project), folder))
                .filter(entry -> entry.srcSet != null)
                .distinct()
                .collect(Collectors.toMap(f -> f.srcSet, f -> f.source));
        srcToOut.forEach((src, out) -> {
            dependsOn(src.getProcessResourcesTaskName());
            project.getTasks().named(src.getProcessResourcesTaskName(), ProcessResources.class)
                    .configure(processResources -> {
                        for (final File out1 : processResources.getOutputs().getFiles())
                            toCopy.put(out1, project.file(out.getOutput()));
                    });
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
