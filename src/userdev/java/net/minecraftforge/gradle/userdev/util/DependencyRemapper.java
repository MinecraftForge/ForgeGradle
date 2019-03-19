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
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.api.internal.file.collections.DefaultConfigurableFileCollection;
import org.gradle.api.internal.tasks.DefaultTaskContainer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class DependencyRemapper {
    private final Project project;
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
     * That means either calling Dependency#copy and then changing parameters in the copy
     * or using gradle classes starting with Default, e.g. DefaultSelfResolvingDependency
     */
    public Dependency remap(Dependency dependency) {
        if (dependency instanceof ExternalModuleDependency) {
            return remapExternalModule((ExternalModuleDependency) dependency);
        }

        if (dependency instanceof FileCollectionDependency) {
            return remapFiles((FileCollectionDependency) dependency);
        }

        project.getLogger().warn("Cannot deobfuscate dependency of type {}, using obfuscated version!", dependency.getClass().getSimpleName());
        return dependency;
    }

    private ExternalModuleDependency remapExternalModule(ExternalModuleDependency dependency) {
        ExternalModuleDependency newDep = dependency.copy();
        mappingListeners.add(m -> newDep.version(v -> v.strictly(newDep.getVersion() + "_mapped_" + m)));
        return newDep;
    }

    private Dependency remapFiles(FileCollectionDependency dependency) {
        DefaultConfigurableFileCollection files = new DefaultConfigurableFileCollection(new IdentityFileResolver(), (DefaultTaskContainer) project.getTasks());
        DefaultSelfResolvingDependency newDep = new DefaultSelfResolvingDependency(files);
        mappingListeners.add(m ->
                dependency.getFiles().getFiles().forEach(f ->
                        {
                            try {
                                if (f.getName().endsWith("sources.jar")) {
                                    files.from(deobfuscator.deobfSources(f, m,  "local_file", f.getName()));
                                } else {
                                    files.from(deobfuscator.deobfBinary(f, m, "local_file", f.getName()));
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                )
        );
        return newDep;
    }

    public void attachMappings(String mappings) {
        mappingListeners.forEach(l -> l.accept(mappings));
    }

}
