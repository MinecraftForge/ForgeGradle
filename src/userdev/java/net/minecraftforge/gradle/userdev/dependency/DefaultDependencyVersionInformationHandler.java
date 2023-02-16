/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.dependency;

import com.google.common.collect.Maps;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.specs.Spec;

import java.util.Map;
import java.util.Optional;

public class DefaultDependencyVersionInformationHandler extends AbstractDependencyManagementObject implements DependencyVersionInformationHandler {

    private final Map<Spec<? super ArtifactIdentifier>, String> rangedVersions = Maps.newHashMap();
    private final Map<Spec<? super ArtifactIdentifier>, String> pinnedVersions = Maps.newHashMap();

    public DefaultDependencyVersionInformationHandler(final Project project) {
        super(project);
    }

    @Override
    public void ranged(final Spec<? super ArtifactIdentifier> spec, final String range) {
        rangedVersions.put(spec, range);
    }

    @Override
    public void ranged(final Spec<? super ArtifactIdentifier> spec, final VersionRange range) {
        ranged(spec, range.toString());
    }

    @Override
    public void ranged(final Spec<? super ArtifactIdentifier> spec, final ArtifactVersion version) {
        ranged(spec, String.format("[%s,%s]", version, version));
    }

    @Override
    public void pin(final Spec<? super ArtifactIdentifier> spec, final String version) {
        pinnedVersions.put(spec, version);
    }

    @Override
    public void pin(final Spec<? super ArtifactIdentifier> spec, final ArtifactVersion version) {
        pin(spec, version.toString());
    }

    @Override
    public Optional<String> getVersionRange(final ModuleDependency dependency) {
        final ArtifactIdentifier identifier = createArtifactIdentifier(dependency);
        return rangedVersions.entrySet().stream()
                .filter(entry -> entry.getKey().isSatisfiedBy(identifier))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    @Override
    public Optional<String> getVersion(final ModuleDependency dependency) {
        final ArtifactIdentifier identifier = createArtifactIdentifier(dependency);
        return pinnedVersions.entrySet().stream()
                .filter(entry -> entry.getKey().isSatisfiedBy(identifier))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
