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

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.FileCollectionDependency;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DependencyRemapper {
    private final Project project;
    @SuppressWarnings("unused")
    private Deobfuscator deobfuscator;
    private List<Consumer<String>> mappingListeners = new ArrayList<>();

    public DependencyRemapper(Project project, Deobfuscator deobfuscator) {
        this.project = project;
        this.deobfuscator = deobfuscator;
    }

    /*
     * Impl note: Gradle uses a lot of internal instanceof checking,
     * so it's more reliable to use the same classes gradle uses.
     *
     * Best way to do it is Dependency#copy. If that's not possible,
     * internal classes starting with Default are an option. It should be a last resort,
     * as that is a part of internal unstable APIs.
     */
    public Dependency remap(Dependency dependency) {
        if (dependency instanceof ExternalModuleDependency) {
            return remapExternalModule((ExternalModuleDependency) dependency);
        }

        if (dependency instanceof FileCollectionDependency) {
            project.getLogger().warn("files(...) dependencies are not deobfuscated. Use a flatDir repository instead: https://docs.gradle.org/current/userguide/repository_types.html#sec:flat_dir_resolver");
        }

        project.getLogger().warn("Cannot deobfuscate dependency of type {}, using obfuscated version!", dependency.getClass().getSimpleName());
        return dependency;
    }

    private ExternalModuleDependency remapExternalModule(ExternalModuleDependency dependency) {
        ExternalModuleDependency newDep = dependency.copy();
        mappingListeners.add(m -> newDep.version(v -> v.strictly(newDep.getVersion() + "_mapped_" + m)));
        return newDep;
    }

    public void attachMappings(String mappings) {
        mappingListeners.forEach(l -> l.accept(mappings));
    }

}
