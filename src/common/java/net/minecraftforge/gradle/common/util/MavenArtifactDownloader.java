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
import java.util.Set;
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
        String name = "mavenDownloader_" + artifact.replace(':', '_');
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
        Set<File> files = null;
        try {
            files = cfg.resolve();
        } catch (NullPointerException npe) {
            // This happens for unknown reasons deep in Gradle code... so we SHOULD find a way to fix it, but
            //honestly i'd rather deprecate this whole system and replace it with downloading things ourselves.
            project.getLogger().error("Failed to download " + artifact + " gradle exploded");
            return null;
        }
        File ret = files.iterator().next(); //We only want the first, not transitive

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
