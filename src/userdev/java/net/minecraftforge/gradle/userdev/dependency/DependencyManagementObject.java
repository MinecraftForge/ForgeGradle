/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.dependency;

import groovy.lang.Closure;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.specs.Spec;

public interface DependencyManagementObject {
    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * If a regex is supplied for any of the group, name, or version, the spec will match if the dependency matches the regex.
     *
     * @param notation The dependency notation to parse.
     * @return The spec that matches the dependency notation.
     */
    Spec<? super ArtifactIdentifier> dependency(Object notation);

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     *
     * @param dependency The dependency to match.
     * @return The spec that matches the dependency.
     */
    Spec<? super ArtifactIdentifier> dependency(Dependency dependency);

    /**
     * Create a spec that matches the provided closure.
     *
     * @param spec The closure to invoke.
     * @return The spec that matches by invoking the closure.
     */
    Spec<? super ArtifactIdentifier> dependency(Closure<Boolean> spec);

    /**
     * Simple artifact identifier class which only references group, name and version.
     */
    final class ArtifactIdentifier {
        private final String group;
        private final String name;
        private final String version;

        /**
         * Creates a new instance of the given artifact details.
         *
         * @param group   The group of the artifact to identify.
         * @param name    The name of the artifact to identify.
         * @param version The version of the artifact to identify.
         */
        public ArtifactIdentifier(String group, String name, String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        /**
         * Gets the group of the artifact.
         *
         * @return The group of the artifact.
         */
        public String getGroup() {
            return group;
        }

        /**
         * Gets the name of the artifact.
         *
         * @return The name of the artifact.
         */
        public String getName() {
            return name;
        }

        /**
         * Gets the version of the artifact.
         *
         * @return The version of the artifact.
         */
        public String getVersion() {
            return version;
        }
    }
}
