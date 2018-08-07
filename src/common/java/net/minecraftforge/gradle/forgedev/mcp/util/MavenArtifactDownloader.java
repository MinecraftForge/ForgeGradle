package net.minecraftforge.gradle.forgedev.mcp.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class MavenArtifactDownloader {

    private static final Map<Project, Integer> COUNTERS = new WeakHashMap<>();

    public static Set<File> download(Project project, String artifact) {
        Configuration cfg = project.getConfigurations().create("downloadDeps" + COUNTERS.getOrDefault(project, 0));
        Dependency dependency = project.getDependencies().create(artifact);
        cfg.getDependencies().add(dependency);
        Set<File> files = cfg.resolve();
        project.getConfigurations().remove(cfg);
        COUNTERS.compute(project, (proj, prev) -> (prev != null ? prev : 0) + 1);
        return files;
    }

}
