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

import groovy.lang.Closure;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface DependencyFilter {
    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter exclude(Spec<? super ArtifactIdentifier> spec);

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter include(Spec<? super ArtifactIdentifier> spec);

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ArtifactIdentifier> project(Map<String, ?> notation);

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ArtifactIdentifier> project(String notation);

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ArtifactIdentifier> dependency(Object notation);

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     *
     * @param dependency
     * @return
     */
    Spec<? super ArtifactIdentifier> dependency(Dependency dependency);

    /**
     * Create a spec that matches the provided closure
     *
     * @param spec
     * @return
     */
    Spec<? super ArtifactIdentifier> dependency(Closure<Boolean> spec);

    boolean isIncluded(ResolvedDependency dependency);

    boolean isIncluded(ExternalModuleDependency dependency);

    boolean isIncluded(ArtifactIdentifier dependency);

    final class ArtifactIdentifier {
        private final String group;
        private final String name;
        private final String version;

        public ArtifactIdentifier(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        public String getGroup() {
            return group;
        }

        public String getName() {
            return name;
        }

        public String getVersion() {
            return version;
        }
    }
}
