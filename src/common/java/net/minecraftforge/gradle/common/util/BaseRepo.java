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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

import com.amadornes.artifactural.api.artifact.Artifact;
import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.amadornes.artifactural.api.artifact.ArtifactType;
import com.amadornes.artifactural.api.repository.ArtifactProvider;
import com.amadornes.artifactural.base.artifact.StreamableArtifact;
import com.amadornes.artifactural.base.repository.ArtifactProviderBuilder;
import com.amadornes.artifactural.base.repository.SimpleRepository;
import com.amadornes.artifactural.gradle.GradleRepositoryAdapter;

public abstract class BaseRepo implements ArtifactProvider<ArtifactIdentifier> {
    private final File cache;
    protected final Logger log;
    protected final String REPO_NAME = getClass().getSimpleName();

    protected BaseRepo(File cache, Logger log) {
        this.cache = cache;
        this.log = log;
    }

    protected File getCacheRoot() {
        return this.cache;
    }

    protected File cache(String... path) {
        return new File(getCacheRoot(), String.join(File.separator, path));
    }

    protected String clean(ArtifactIdentifier art) {
        return art.getGroup() + ":" + art.getName() + ":" + art.getVersion() + ":" + art.getClassifier() + "@" + art.getExtension();
    }

    protected void debug(String message) {
        if (System.getProperty("fg.debugRepo", "false").equals("true"))
            this.log.lifecycle(message);
    }
    protected void info(String message) {
        this.log.lifecycle(message);
    }

    @Override
    public final Artifact getArtifact(ArtifactIdentifier artifact) {
        try {
            debug(REPO_NAME + " Request: " + clean(artifact));
            String[] pts  = artifact.getExtension().split("\\.");

            String desc = (artifact.getGroup() + ":" + artifact.getName() + ":" + artifact.getVersion() + ":" + artifact.getClassifier() + "@" + pts[0]).intern();
            File ret = null;
            synchronized (desc) {
                if (pts.length == 1)
                    ret = findFile(artifact);
                else // Call without the .md5/.sha extension.
                    ret = findFile(net.minecraftforge.gradle.common.util.Artifact.from(artifact.getGroup(), artifact.getName(), artifact.getVersion(), artifact.getClassifier(), pts[0]));
            }

            if (ret != null) {
                ArtifactType type = ArtifactType.OTHER;
                if (artifact.getClassifier() != null && artifact.getClassifier().endsWith("sources"))
                    type = ArtifactType.SOURCE;
                else if ("jar".equals(artifact.getExtension()))
                    type = ArtifactType.BINARY;

                if (pts.length == 1)
                    return StreamableArtifact.ofFile(artifact, type, ret);
                else if (pts.length == 2) {
                    File hash = new File(ret.getAbsolutePath() + "." + pts[1]);
                    if (hash.exists())
                        return StreamableArtifact.ofFile(artifact, type, hash);
                }
            }
            return Artifact.none();
        } catch (Throwable e) {
            //Catch everything so we don't error up in gradle and fuck up the internals so it never asks us for anything ever again!
            log.lifecycle("Error getting artifact: " + clean(artifact) + " from  " + REPO_NAME, e);
            return Artifact.none();
        }
    }

    protected abstract File findFile(ArtifactIdentifier artifact) throws IOException;

    public static class Builder {
        private List<ArtifactProvider<ArtifactIdentifier>> repos = new ArrayList<>();
        public Builder add(ArtifactProvider<ArtifactIdentifier> repo) {
            if (repo != null)
                repos.add(repo);
            return this;
        }

        public void attach(Project project) {
            int random = new Random().nextInt();
            File cache = Utils.getCache(project, "bundeled_repo");
            GradleRepositoryAdapter.add(project.getRepositories(), "BUNDELED_" + random, cache,
                SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(
                    new ArtifactProvider<ArtifactIdentifier>() {
                        @Override
                        public Artifact getArtifact(ArtifactIdentifier artifact) {
                            return repos.stream().map(repo -> repo.getArtifact(artifact)).filter(Artifact::isPresent).findFirst().orElse(Artifact.none());
                        }
                    }
                ))
            );
        }
    }

}
