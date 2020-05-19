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

package net.minecraftforge.gradle.userdev;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;

public class DependencyManagementExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "fg";
    private final Project project;
    private final DependencyRemapper remapper;

    public DependencyManagementExtension(Project project, DependencyRemapper remapper) {
        this.project = project;
        this.remapper = remapper;
    }

    @SuppressWarnings("unused")
    public Dependency deobf(Object dependency) {
        return deobf(dependency, null);
    }

    public Dependency deobf(Object dependency, Closure<?> configure){
        Dependency baseDependency = project.getDependencies().create(dependency, configure);
        project.getConfigurations().getByName(UserDevPlugin.OBF).getDependencies().add(baseDependency);

        return remapper.remap(baseDependency);
    }
}
