package net.minecraftforge.gradle.common.util;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.repositories.ArtifactRepository;

import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class MavenArtifactDownloader {
    private static final Cache<String, File> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private static final Map<Project, Integer> COUNTER = new HashMap<>();

    private static final Map<String, String> VERSIONS = new HashMap<>();

    private static File _download(Project project, String artifact, boolean changing, boolean generated, boolean gradle) {
        File ret = null;
        try {
            ret = CACHE.getIfPresent(artifact);
            if (ret != null && !ret.exists()) {
                CACHE.invalidate(artifact);
                ret = null;
            }
            if (ret == null && generated) {
                ret = _generate(project, artifact);
                if (ret != null)
                    CACHE.put(artifact, ret);
            }
            if (ret == null && gradle) {
                ret = _gradle(project, artifact, changing);
                if (ret != null)
                    CACHE.put(artifact, ret);
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        return ret;
    }

    private static File _generate(Project project, String artifact) {
        Artifact art = Artifact.from(artifact);
        List<ArtifactRepository> repos = project.getRepositories();
        for (ArtifactRepository repo : repos) {
            if (repo instanceof GradleRepositoryAdapter) {
                GradleRepositoryAdapter fake = (GradleRepositoryAdapter)repo;
                File ret = fake.getArtifact(art);
                if (ret != null && ret.exists())
                    return ret;
            }
        }
        return null;
    }

    private static File _gradle(Project project, String artifact, boolean changing) {
        String name = "mavenDownloader_" + artifact;
        synchronized(project) {
            int count = COUNTER.getOrDefault(project, 1);
            name += "_" + count++;
            COUNTER.put(project, count);
        }

        Artifact mine = Artifact.from(artifact);
        List<ArtifactRepository> repos = project.getRepositories();
        List<ArtifactRepository> old = new ArrayList<>(repos);
        repos.removeIf(e -> e instanceof GradleRepositoryAdapter); //Remove any fake repos

        Configuration cfg = project.getConfigurations().create(name);
        ExternalModuleDependency dependency = (ExternalModuleDependency)project.getDependencies().create(artifact);
        dependency.setChanging(changing);
        cfg.getDependencies().add(dependency);
        cfg.resolutionStrategy(strat -> {
            strat.cacheChangingModulesFor(5, TimeUnit.MINUTES);
            strat.cacheDynamicVersionsFor(5, TimeUnit.MINUTES);
        });
        File ret = cfg.resolve().iterator().next(); //We only want the first, not transitive

        cfg.getResolvedConfiguration().getResolvedArtifacts().forEach(art -> {
            ModuleVersionIdentifier resolved = art.getModuleVersion().getId();
            if (resolved.getGroup().equals(mine.getGroup()) && resolved.getName().equals(mine.getName())) {
                if ((mine.getClassifier() == null && art.getClassifier() == null) || mine.getClassifier().equals(art.getClassifier()))
                    VERSIONS.put(artifact, resolved.getVersion());
            }
        });

        project.getConfigurations().remove(cfg);

        repos.clear(); //Clear the repos so we can re-add in the correct oder.
        repos.addAll(old); //Readd all the normal repos.
        return ret;
    }

    public static File both(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, true, true);
    }

    public static String getVersion(Project project, String artifact) {
        gradle(project, artifact, true);
        return VERSIONS.get(artifact);
    }

    public static File gradle(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, false, true);
    }

    public static File generate(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, true, false);
    }
}
