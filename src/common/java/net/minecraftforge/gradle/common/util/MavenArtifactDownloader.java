package net.minecraftforge.gradle.common.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class MavenArtifactDownloader {
    private static final Cache<String, File> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    private static final Map<String, String> VERSIONS = new HashMap<>();

    private static File _download(Project project, String artifact, boolean changing) {
        File ret = null;
        try {
            ret = CACHE.get(artifact, () -> gradleDownload(project, artifact, changing));
            if (ret != null && !ret.exists()) {
                CACHE.invalidate(artifact);
                ret = CACHE.get(artifact, () -> gradleDownload(project, artifact, changing));
            }
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private static File gradleDownload(Project project, String artifact, boolean changing) {
        String name = "mavenDownloader_" + artifact.replace(":", "/");

        //TODO: Bypass gradle's crap?
        //List<ArtifactRepository> repos = project.getRepositories();

        Configuration cfg = project.getConfigurations().create(name);
        ExternalModuleDependency dependency = (ExternalModuleDependency)project.getDependencies().create(artifact);
        dependency.setChanging(changing);
        cfg.getDependencies().add(dependency);
        cfg.resolutionStrategy(strat -> {
            strat.cacheChangingModulesFor(5, TimeUnit.MINUTES);
            strat.cacheDynamicVersionsFor(5, TimeUnit.MINUTES);
        });
        File ret = cfg.resolve().iterator().next(); //We only want the first, not transitive

        Artifact mine = Artifact.from(artifact);
        cfg.getResolvedConfiguration().getResolvedArtifacts().forEach(art -> {
            ModuleVersionIdentifier resolved = art.getModuleVersion().getId();
            if (resolved.getGroup().equals(mine.getGroup()) && resolved.getName().equals(mine.getName())) {
                if ((mine.getClassifier() == null && art.getClassifier() == null) || mine.getClassifier().equals(art.getClassifier()))
                    VERSIONS.put(artifact, resolved.getVersion());
            }
        });

        project.getConfigurations().remove(cfg);
        return ret;
    }

    public static File single(Project project, String artifact) {
        return single(project, artifact, false);
    }

    public static File single(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing);
    }

    public static String getVersion(Project project, String artifact) {
        single(project, artifact);
        return VERSIONS.get(artifact);
    }
}
