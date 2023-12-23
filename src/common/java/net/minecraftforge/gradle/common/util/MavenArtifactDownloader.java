/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import com.google.common.collect.ImmutableMap;
import net.minecraftforge.artifactural.gradle.GradleRepositoryAdapter;

import net.minecraftforge.artifactural.gradle.RepositoryContentUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.authentication.http.BasicAuthentication;
import org.xml.sax.SAXException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import groovy.util.Node;
import groovy.xml.XmlParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.xml.parsers.ParserConfigurationException;

public class MavenArtifactDownloader {
    /**
     * This tracks downloads that are <b>currently</b> active. As soon as a download has finished it will be removed
     * from this map.
     */
    private static final Map<DownloadKey, Future<File>> ACTIVE_DOWNLOADS = new HashMap<>();

    private static final Cache<String, File> CACHE = CacheBuilder.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();
    private static final Map<Project, Integer> COUNTER = new HashMap<>();

    private static final Map<String, String> VERSIONS = new HashMap<>();

    @Nullable
    public static File download(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, true, true, true);
    }

    public static String getVersion(Project project, String artifact) {
        Artifact art = Artifact.from(artifact);
        if (!art.getVersion().endsWith("+") && !art.isSnapshot())
            return art.getVersion();
        _download(project, artifact, true, false, true, true);
        return VERSIONS.get(artifact);
    }

    @Nullable
    public static File gradle(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, false, true, true);
    }

    @Nullable
    public static File generate(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, true, false, true);
    }

    @Nullable
    public static File manual(Project project, String artifact, boolean changing) {
        return _download(project, artifact, changing, false, false, true);
    }

    @Nullable
    private static File _download(Project project, String artifact, boolean changing, boolean generated, boolean gradle, boolean manual) {
        /*
         * This somewhat convoluted code is necessary to avoid race-conditions when two Gradle worker threads simultaneously
         * try to download the same artifact.
         * The first thread registers a future that other threads can wait on.
         * Once it finishes, the future will be removed and subsequent calls will use the CACHE instead.
         * We use all parameters of the function as the key here to prevent subtle bugs where the same artifact
         * is looked up simultaneously with different resolver-options, leading only to one attempt being made.
         */
        DownloadKey downloadKey = new DownloadKey(project, artifact, changing, generated, gradle, manual);
        CompletableFuture<File> future;
        synchronized (ACTIVE_DOWNLOADS) {
            Future<File> activeDownload = ACTIVE_DOWNLOADS.get(downloadKey);
            if (activeDownload != null) {
                // Some other thread is already working downloading this exact artifact, wait for it to finish
                try {
                    project.getLogger().info("Waiting for download of {} on other thread", artifact);
                    while (!activeDownload.isDone()) {
                        // Release the monitor of ACTIVE_DOWNLOADS while waiting on the download;
                        // when a new download finishes, we'll get notified and we can check whether the download is complete again.
                        ACTIVE_DOWNLOADS.wait();
                    }
                    return activeDownload.get();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof RuntimeException) {
                        throw (RuntimeException) e.getCause();
                    } else {
                        throw new RuntimeException(e.getCause());
                    }
                }
            } else {
                project.getLogger().info("Downloading {}", artifact);
                // We're the first thread to download the artifact, make sure concurrent downloads just wait for us
                future = new CompletableFuture<>();
                ACTIVE_DOWNLOADS.put(downloadKey, future);
            }
        }

        File ret = null;
        try {
            Artifact art = Artifact.from(artifact);

            ret = CACHE.getIfPresent(artifact);
            if (ret != null && !ret.exists()) {
                CACHE.invalidate(artifact);
                ret = null;
            }

            List<MavenArtifactRepository> mavens = new ArrayList<>();
            List<GradleRepositoryAdapter> fakes = new ArrayList<>();
            List<ArtifactRepository> others = new ArrayList<>();

            project.getRepositories().forEach( repo -> {
                if (repo instanceof MavenArtifactRepository)
                    mavens.add((MavenArtifactRepository)repo);
                else if (repo instanceof GradleRepositoryAdapter)
                    fakes.add((GradleRepositoryAdapter)repo);
                else
                    others.add(repo);
            });

            if (ret == null && generated) {
                ret = _generate(fakes, art);
            }

            if (ret == null && manual) {
                ret = _manual(project, mavens, art, changing);
            }

            if (ret == null && gradle) {
                ret = _gradle(project, others, art, changing);
            }

            if (ret != null)
                CACHE.put(artifact, ret);

            future.complete(ret);
        } catch (RuntimeException | IOException | URISyntaxException e) {
            future.completeExceptionally(e);
            e.printStackTrace();
        } finally {
            synchronized (ACTIVE_DOWNLOADS) {
                ACTIVE_DOWNLOADS.remove(downloadKey);
                ACTIVE_DOWNLOADS.notifyAll();
            }
        }
        return ret;
    }

    @Nullable
    private static File _generate(List<GradleRepositoryAdapter> repos, Artifact artifact) {
        for (GradleRepositoryAdapter repo : repos) {
            File ret = repo.getArtifact(artifact);
            if (ret != null && ret.exists())
                return ret;
        }
        return null;
    }

    @Nullable
    private static File _manual(Project project, List<MavenArtifactRepository> repos, Artifact artifact, boolean changing) throws IOException, URISyntaxException {
        if (!artifact.getVersion().endsWith("+") && !artifact.isSnapshot()) {
            for (MavenArtifactRepository repo : repos) {
                Pair<Artifact, File> pair = _manualMaven(project, repo, repo, artifact, changing);
                if (pair != null && pair.getValue().exists())
                    return pair.getValue();
            }
            return null;
        }

        List<Pair<Artifact, File>> versions = new ArrayList<>();

        // Gather list of all versions from all repos.
        for (MavenArtifactRepository repo : repos) {
            Pair<Artifact, File> pair = _manualMaven(project, repo, repo, artifact, changing);
            if (pair != null && pair.getValue().exists())
                versions.add(pair);
        }

        Artifact version = null;
        File ret = null;
        for (Pair<Artifact, File> ver : versions) {
            //Select highest version
            if (version == null || version.compareTo(ver.getKey()) < 0) {
                version = ver.getKey();
                ret = ver.getValue();
            }
        }

        if (ret == null)
            return null;

        VERSIONS.put(artifact.getDescriptor(), version.getVersion());
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private static Pair<Artifact, File> _manualMaven(Project project, @Nullable AuthenticationSupported auth, MavenArtifactRepository mavenRepo, Artifact artifact, boolean changing) throws IOException, URISyntaxException {
        if (RepositoryContentUtils.contentFilterExcludes(mavenRepo, artifact)) {
            //The repo can never provide the artifact
            return null;
        }
        URI maven = mavenRepo.getUrl();
        if (artifact.getVersion().endsWith("+")) {
            //I THINK +'s are only valid in the end version, So 1.+ and not 1.+.4 as that'd make no sense.
            //It also appears you can't do something like 1.5+ to NOT get 1.4/1.3. So.. mimic that.
            File meta = _downloadWithCache(project, auth, maven, artifact.getGroup().replace('.', '/') + '/' + artifact.getName() + "/maven-metadata.xml", true, true);
            if (meta == null)
                return null; //Don't error, other repos might have it.
            try {
                Node xml = new XmlParser().parse(meta);
                Node versioning = getPath(xml, "versioning/versions");
                List<Node> versions = versioning == null ? null : (List<Node>)versioning.get("version");
                if (versions == null) {
                    meta.delete();
                    throw new IOException("Invalid maven-metadata.xml file, missing version list");
                }
                String prefix = artifact.getVersion().substring(0, artifact.getVersion().length() - 1); // Trim +
                ArtifactVersion minVersion = (!prefix.endsWith(".") && prefix.length() > 0) ? new DefaultArtifactVersion(prefix) : null;
                if (minVersion != null) { //Support min version like 1.5+ by saving it, and moving the prefix
                    //minVersion = new DefaultArtifactVersion(prefix);
                    int idx = prefix.lastIndexOf('.');
                    prefix = idx == -1 ? "" : prefix.substring(0, idx + 1);
                }
                final String prefix_ = prefix;
                ArtifactVersion highest = versions.stream().map(Node::text)
                    .filter(s -> s.startsWith(prefix_))
                    .map(DefaultArtifactVersion::new)
                    .filter(v -> minVersion == null || minVersion.compareTo(v) <= 0)
                    .sorted()
                    .reduce((first, second) -> second).orElse(null);
                if (highest == null)
                    return null; //We have no versions that match what we want, so move on to next repo.
                artifact = Artifact.from(artifact.getGroup(), artifact.getName(), highest.toString(), artifact.getClassifier(), artifact.getExtension());
            } catch (SAXException | ParserConfigurationException e) {
                meta.delete();
                throw new IOException("Invalid maven-metadata.xml file", e);
            }
        } else if (artifact.getVersion().contains("-SNAPSHOT")) {
            return null; //TODO
            //throw new IllegalArgumentException("Snapshot versions are not supported, yet... " + artifact.getDescriptor());
        }

        File ret = _downloadWithCache(project, auth, maven, artifact.getPath(), changing, false);
        return ret == null ? null : ImmutablePair.of(artifact, ret);
    }

    //I'm sure there is a better way but not sure at the moment
    @SuppressWarnings("unchecked")
    @Nullable
    private static Node getPath(Node node, String path) {
        Node tmp = node;
        for (String pt : path.split("/")) {
            tmp = ((List<Node>)tmp.get(pt)).stream().findFirst().orElse(null);
            if (tmp == null)
                return null;
        }
        return tmp;
    }

    @Nullable
    private static File _gradle(Project project, List<ArtifactRepository> repos, Artifact mine, boolean changing) {
        String name = "mavenDownloader_" + mine.getDescriptor().replace(':', '_');
        synchronized(project) {
            int count = COUNTER.getOrDefault(project, 1);
            name += "_" + count++;
            COUNTER.put(project, count);
        }

        //Remove old repos, and only use the ones we're told to.
        List<ArtifactRepository> old = new ArrayList<>(project.getRepositories());
        project.getRepositories().clear();
        project.getRepositories().addAll(repos);

        Configuration cfg = project.getConfigurations().create(name);
        ExternalModuleDependency dependency = (ExternalModuleDependency)project.getDependencies().create(mine.getDescriptor());
        dependency.setChanging(changing);
        cfg.getDependencies().add(dependency);
        cfg.resolutionStrategy(strat -> {
            strat.cacheChangingModulesFor(5, TimeUnit.MINUTES);
            strat.cacheDynamicVersionsFor(5, TimeUnit.MINUTES);
        });
        Set<File> files;
        try {
            files = cfg.resolve();
        } catch (NullPointerException npe) {
            // This happens for unknown reasons deep in Gradle code... so we SHOULD find a way to fix it, but
            //honestly i'd rather deprecate this whole system and replace it with downloading things ourselves.
            project.getLogger().error("Failed to download " + mine.getDescriptor() + " gradle exploded");
            return null;
        }
        File ret = files.iterator().next(); //We only want the first, not transitive

        cfg.getResolvedConfiguration().getResolvedArtifacts().forEach(art -> {
            ModuleVersionIdentifier resolved = art.getModuleVersion().getId();
            if (resolved.getGroup().equals(mine.getGroup()) && resolved.getName().equals(mine.getName())) {
                if ((mine.getClassifier() == null && art.getClassifier() == null) || mine.getClassifier().equals(art.getClassifier()))
                    VERSIONS.put(mine.getDescriptor(), resolved.getVersion());
            }
        });

        project.getConfigurations().remove(cfg);

        project.getRepositories().clear(); //Clear the repos so we can re-add in the correct oder.
        project.getRepositories().addAll(old); //Readd all the normal repos.
        return ret;
    }

    @Nullable
    private static File _downloadWithCache(Project project, @Nullable AuthenticationSupported auth, URI maven, String path, boolean changing, boolean bypassLocal) throws IOException, URISyntaxException {
        URL url = new URIBuilder(maven)
            .setPath(maven.getPath() + '/' + path)
            .build()
            .normalize()
            .toURL();
        File target = Utils.getCache(project, "maven_downloader", path);

        Map<String, String> headers = null;
        if (auth != null && !auth.getAuthentication().isEmpty() && auth.getAuthentication().stream().anyMatch(a -> a instanceof BasicAuthentication)) {
            // We use this to prevent an IllegalStateException with getCredentials() if non-password credentials are used.
            Credentials credentials = auth.getCredentials(Credentials.class);
            if (credentials instanceof PasswordCredentials) {
                PasswordCredentials passwordCredentials = (PasswordCredentials) credentials;
                headers = ImmutableMap.of(
                        "Authorization", "Basic " + Base64.getEncoder().encodeToString((passwordCredentials.getUsername() + ":" + passwordCredentials.getPassword()).getBytes(StandardCharsets.UTF_8))
                );
            }
        }

        return DownloadUtils.downloadWithCache(url, target, headers, changing, bypassLocal);
    }

    /**
     * Key used to track active downloads and avoid downloading the same file in two threads concurrently,
     * leading to corrupted files on disk.
     */
    private static class DownloadKey {
        private final Project project;
        private final String artifact;
        private final boolean changing;
        private final boolean generated;
        private final boolean gradle;
        private final boolean manual;

        DownloadKey(Project project, String artifact, boolean changing, boolean generated, boolean gradle, boolean manual) {
            this.project = project;
            this.artifact = artifact;
            this.changing = changing;
            this.generated = generated;
            this.gradle = gradle;
            this.manual = manual;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            DownloadKey that = (DownloadKey) o;
            return changing == that.changing && generated == that.generated && gradle == that.gradle && manual == that.manual && project.equals(that.project) && artifact.equals(that.artifact);
        }

        @Override
        public int hashCode() {
            return Objects.hash(project, artifact, changing, generated, gradle, manual);
        }

    }

}
