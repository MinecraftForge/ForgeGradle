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

import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;

/**
 * A dependency filter that can be used to filter out dependencies based on different specifications.
 * Inspired and incrementally derived from the dependency filter in the Shadow plugin.
 */
public interface DependencyFilter extends DependencyManagementObject {
    /**
     * Exclude dependencies that match the provided spec.
     * If at least one exclude spec is provided, only the dependencies which fail the check will be excluded.
     *
     * @param spec The spec to exclude dependencies that match.
     * @return The filter (this object).
     */
    DependencyFilter exclude(Spec<? super ArtifactIdentifier> spec);

    /**
     * Include dependencies that match the provided spec.
     * If at least one include spec is supplied then only dependencies that match the include-spec will be included.
     *
     * @param spec The spec to include dependencies that match.
     * @return The filter (this object)
     */
    DependencyFilter include(Spec<? super ArtifactIdentifier> spec);

    /**
     * Indicates if the given resolved dependency passes the filter.
     *
     * @param dependency The resolved dependency to check.
     * @return The result of the filter.
     */
    boolean isIncluded(ResolvedDependency dependency);

    /**
     * Indicates if the given dependency passes the filter.
     *
     * @param dependency The dependency to check.
     * @return The result of the filter.
     */
    boolean isIncluded(ModuleDependency dependency);

    /**
     * Checks if the given artifact identifier matches the dependency.
     *
     * @param identifier The identifier to check.
     * @return The result of the filter.
     */
    boolean isIncluded(ArtifactIdentifier identifier);

}
