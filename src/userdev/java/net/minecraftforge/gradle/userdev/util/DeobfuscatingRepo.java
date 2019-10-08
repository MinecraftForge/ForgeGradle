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

package net.minecraftforge.gradle.userdev.util;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import net.minecraftforge.gradle.common.util.*;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Stream;

/*
 * Takes in SRG names jars/sources and remaps them using MCPNames.
 */
public class DeobfuscatingRepo extends BaseRepo {
    @SuppressWarnings("unused")
    private final Project project;

    //once resolved by gradle, will contain SRG-named artifacts for us to deobf
    private final Configuration origin;
    private ResolvedConfiguration resolvedOrigin;
    private Deobfuscator deobfuscator;

    public DeobfuscatingRepo(Project project, Configuration origin, Deobfuscator deobfuscator) {
        super(Utils.getCache(project, "mod_remap_repo"), project.getLogger());
        this.project = project;
        this.origin = origin;
        this.deobfuscator = deobfuscator;
    }

    private String getMappings(String version) {
        if (!version.contains("_mapped_"))
            return null;
        return version.split("_mapped_")[1];
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
            throw new RuntimeException("Invalid deobf dependency: " + artifact.toString());
        }
    }

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

    private File findRaw(Artifact artifact, String mapping) throws IOException {
        Optional<File> orig = findArtifactFile(artifact);
        if (!orig.isPresent()) {
            return null;
        }

        File origFile = orig.get();

        return deobfuscator.deobfBinary(origFile, mapping, getArtifactPath(artifact, mapping));
    }

    private File findSource(Artifact artifact, String mapping) throws IOException {
        Optional<File> orig = findArtifactFile(artifact);
        if (!orig.isPresent()) {
            return null;
        }

        File origFile = orig.get();

        return deobfuscator.deobfSources(origFile, mapping, getArtifactPath(artifact, mapping));
    }

    private String getArtifactPath(Artifact artifact, String mappings) {
        String newVersion = artifact.getVersion() + "_mapped_" + mappings;

        return artifact.withVersion(newVersion).getLocalPath();
    }
}
