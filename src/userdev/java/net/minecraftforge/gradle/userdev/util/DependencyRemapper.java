/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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
    private final List<Consumer<String>> mappingListeners = new ArrayList<>();

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
            project.getLogger().warn("files(...) dependencies are not deobfuscated. Use a flatDir repository instead: https://docs.gradle.org/current/userguide/declaring_repositories.html#sub:flat_dir_resolver");
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
