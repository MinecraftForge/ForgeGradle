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
