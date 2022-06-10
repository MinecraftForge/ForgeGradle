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
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface DependencyFilter {

    /**
     * Resolve a FileCollection against the include/exclude rules in the filter
     * @param configuration
     * @return
     */
    FileCollection resolve(Configuration configuration);

    /**
     * Resolve all FileCollections against the include/exclude ruels in the filter and combine the results
     * @param configurations
     * @return
     */
    FileCollection resolve(Collection<Configuration> configurations);

    /**
     * Exclude dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter exclude(Spec<? super ResolvedDependency> spec);

    /**
     * Include dependencies that match the provided spec.
     *
     * @param spec
     * @return
     */
    DependencyFilter include(Spec<? super ResolvedDependency> spec);

    /**
     * Create a spec that matches the provided project notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(Map<String, ?> notation);

    /**
     * Create a spec that matches the default configuration for the provided project path on group, name, and version
     *
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> project(String notation);

    /**
     * Create a spec that matches dependencies using the provided notation on group, name, and version
     * @param notation
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Object notation);

    /**
     * Create a spec that matches the provided dependency on group, name, and version
     * @param dependency
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Dependency dependency);

    /**
     * Create a spec that matches the provided closure
     * @param spec
     * @return
     */
    Spec<? super ResolvedDependency> dependency(Closure<Boolean> spec);

    boolean isIncluded(ResolvedDependency dependency);
}
