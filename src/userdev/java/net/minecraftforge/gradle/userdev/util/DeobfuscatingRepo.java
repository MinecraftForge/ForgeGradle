/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.util;

import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.gradle.common.util.Artifact;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MavenArtifactDownloader;
import net.minecraftforge.gradle.common.util.Utils;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.repositories.RepositoryContentDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

/*
 * Takes in SRG names jars/sources and remaps them using MCPNames.
 */
public class DeobfuscatingRepo extends BaseRepo {
    @SuppressWarnings("unused")
    private final Project project;

    //once resolved by gradle, will contain SRG-named artifacts for us to deobf
    private final Configuration origin;
    private ResolvedConfiguration resolvedOrigin;
    private final Deobfuscator deobfuscator;

    public DeobfuscatingRepo(Project project, Configuration origin, Deobfuscator deobfuscator) {
        super(Utils.getCache(project, "mod_remap_repo"), project.getLogger());
        this.project = project;
        this.origin = origin;
        this.deobfuscator = deobfuscator;
    }

    @Nullable
    private String getMappings(String version) {
        if (!version.contains("_mapped_"))
            return null;
        return version.split("_mapped_")[1];
    }

    @Override
    protected void configureFilter(RepositoryContentDescriptor filter) {
        filter.includeVersionByRegex(".*", ".*", ".*_mapped_.*"); // Any group, any module BUT version must contain _mapped_
    }

    @Override
    public File findFile(ArtifactIdentifier artifact) throws IOException {
        String version = artifact.getVersion();
        String mappings = getMappings(version);

        if (mappings == null)
            return null; //We only care about the remapped files, not orig

        version = version.substring(0, version.length() - (mappings.length() + "_mapped_".length()));
        String classifier = artifact.getClassifier() == null ? "" : artifact.getClassifier();

        Artifact unmappedArtifact = Artifact.from(artifact).withVersion(version);
        String ext = unmappedArtifact.getExtension();

        debug("  " + REPO_NAME + " Request: " + clean(artifact) + " Mapping: " + mappings);

        if ("pom".equals(ext)) {
            return findPom(unmappedArtifact, mappings);
        } else if ("jar".equals(ext)) {
            if ("sources".equals(classifier)) {
                return findSource(unmappedArtifact, mappings);
            }

            return findRaw(unmappedArtifact, mappings);
        } else {
            throw new RuntimeException("Invalid deobf dependency: " + artifact);
        }
    }

    @Nullable
    private File findPom(Artifact artifact, String mapping) throws IOException {
        Optional<File> orig = findArtifactFile(artifact);

        if (!orig.isPresent()) {
            return null;
        }

        File origFile = orig.get();

        return deobfuscator.deobfPom(origFile, mapping, getArtifactPath(artifact, mapping));
    }

    public ResolvedConfiguration getResolvedOrigin() {
        synchronized (origin) {
            if (resolvedOrigin == null) {
                resolvedOrigin = origin.getResolvedConfiguration();
            }

            return resolvedOrigin;
        }
    }

    private Optional<File> findArtifactFile(Artifact artifact) {
        Stream<ResolvedDependency> deps = getResolvedOrigin().getFirstLevelModuleDependencies(artifact.asDependencySpec()).stream();
        return deps.flatMap(
                d -> d.getModuleArtifacts().stream()
                        .filter(artifact.asArtifactMatcher())
        ).map(ResolvedArtifact::getFile).filter(File::exists).findAny();
    }

    @Nullable
    private File findRaw(Artifact artifact, String mapping) throws IOException {
        Optional<File> orig = findArtifactFile(artifact);
        if (!orig.isPresent()) {
            return null;
        }

        File origFile = orig.get();

        return deobfuscator.deobfBinary(origFile, mapping, getArtifactPath(artifact, mapping));
    }

    @Nullable
    private File findSource(Artifact artifact, String mapping) throws IOException {
        // Check if we have previously failed to retrieve sources for the artifact.
        // If so, don't attempt the download again.
        File noSourceFlag = cache(getArtifactPath(artifact, mapping) + ".nosources");
        if(noSourceFlag.exists()) return null;

        File origFile = MavenArtifactDownloader.manual(project, artifact.getDescriptor(), false);
        if (origFile == null) {
            // Flag that downloading has failed so we don't repeat it
            try {
                noSourceFlag.getParentFile().mkdirs();
                noSourceFlag.createNewFile();
            } catch(IOException e) {
                // Ignore it, not important
            }
            return null;
        }

        return deobfuscator.deobfSources(origFile, mapping, getArtifactPath(artifact, mapping));
    }

    private String getArtifactPath(Artifact artifact, String mappings) {
        String newVersion = artifact.getVersion() + "_mapped_" + mappings;

        return artifact.withVersion(newVersion).getLocalPath();
    }
}
