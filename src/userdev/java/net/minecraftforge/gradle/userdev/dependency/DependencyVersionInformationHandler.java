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

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.specs.Spec;

import java.util.Optional;
import java.util.stream.DoubleStream;

/**
 * A handler which manages version information.
 * It supports setting a ranged version, and a fixed version for artifacts.
 */
public interface DependencyVersionInformationHandler extends DependencyManagementObject {

    /**
     * Sets the supported version range for dependencies which match the spec.
     *
     * @param spec The spec to match dependencies.
     * @param range The string representation to match the version range.
     */
    void ranged(final Spec<? super ArtifactIdentifier> spec, final String range);

    /**
     * Sets the supported version range for dependencies which match the spec.
     *
     * @param spec The spec to match dependencies.
     * @param range The version range to match.
     */
    void ranged(final Spec<? super ArtifactIdentifier> spec, final VersionRange range);

    /**
     * Sets the supported version range for dependencies which match the spec, but limits it to exactly the given version.
     *
     * @param spec The spec to match dependencies.
     * @param version The version to match.
     */
    void ranged(final Spec<? super ArtifactIdentifier> spec, final ArtifactVersion version);

    /**
     * Sets the fixed version of the dependencies matching the spec.
     *
     * @param spec The spec to match.
     * @param version The string representation of the version.
     */
    void pin(final Spec<? super ArtifactIdentifier> spec, final String version);

    /**
     * Sets the fixed version of the dependencies matching the spec.
     *
     * @param spec The spec to match.
     * @param version The version to set.
     */
    void pin(final Spec<? super ArtifactIdentifier> spec, final ArtifactVersion version);

    /**
     * Gets the version range for the given dependency.
     *
     * @param dependency The dependency to get the version range for.
     * @return The version range, if any.
     */
    Optional<String> getVersionRange(ModuleDependency dependency);

    /**
     * Gets the version for the given dependency.
     *
     * @param dependency The dependency to get the version for.
     * @return The version, if any.
     */
    Optional<String> getVersion(ModuleDependency dependency);
}
