/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import net.minecraftforge.artifactural.api.artifact.Artifact;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.artifactural.api.artifact.ArtifactType;
import net.minecraftforge.artifactural.api.repository.ArtifactProvider;
import net.minecraftforge.artifactural.base.artifact.StreamableArtifact;
import net.minecraftforge.artifactural.base.repository.ArtifactProviderBuilder;
import net.minecraftforge.artifactural.base.repository.SimpleRepository;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;
import org.gradle.api.logging.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.annotation.Nullable;

public abstract class BaseRepo implements ArtifactProvider<ArtifactIdentifier> {

    public static final boolean DEBUG = Boolean.getBoolean("fg.debugRepo");

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
        if (DEBUG)
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
            File ret;
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

    protected void configureFilter(RepositoryContentDescriptor filter) {}

    @Nullable
    protected abstract File findFile(ArtifactIdentifier artifact) throws IOException;

    public static class Builder {
        private List<ArtifactProvider<ArtifactIdentifier>> repos = new ArrayList<>();
        public Builder add(@Nullable ArtifactProvider<ArtifactIdentifier> repo) {
            if (repo != null)
                repos.add(repo);
            return this;
        }

        public GradleRepositoryAdapter attach(Project project) {
            return attach(project, "bundeled_repo");
        }

        public GradleRepositoryAdapter attach(Project project, String cacheName) {
            int random = new Random().nextInt();
            File cache = Utils.getCache(project, cacheName);
            // Java 8's compiler doesn't allow the lambda to be a method reference, but Java 16 allows it
            // noinspection Convert2MethodRef to prevent IDEA from warning us about it
            final GradleRepositoryAdapter adapter = GradleRepositoryAdapter.add(project.getRepositories(), "BUNDELED_" + random, cache,
                    SimpleRepository.of(ArtifactProviderBuilder.begin(ArtifactIdentifier.class).provide(
                            artifact -> repos.stream()
                                    .map(repo -> repo.getArtifact(artifact))
                                    .filter(s -> s.isPresent())
                                    .findFirst()
                                    .orElse(Artifact.none())
                    ))
            );
            adapter.content(content -> repos.stream().filter(repo -> repo instanceof BaseRepo)
                    .forEach(repo -> ((BaseRepo) repo).configureFilter(content)));
            return adapter;
        }
    }

}
