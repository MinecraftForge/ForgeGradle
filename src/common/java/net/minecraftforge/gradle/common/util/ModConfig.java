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

package net.minecraftforge.gradle.common.util;

import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSet;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig extends GroovyObjectSupport {

    private transient final Project rootProject;

    private final String name;
    private Project modProject;
    private FileCollection resources;
    private FileCollection classes;

    private List<SourceSet> sources;

    public ModConfig(@Nonnull final Project rootProject, @Nonnull final String name) {
        this.rootProject = rootProject;
        this.name = name;
    }

    public String getName() {
        return name;
    }
    
    public void setModProject(Project project) {
        modProject = project;
    }
    
    public void modProject(Project project) {
        setModProject(project);
    }
    
    public Project getModProject() {
        if (modProject == null) {
            modProject = rootProject;
        }
        
        return modProject;
    }

    public void setClasses(FileCollection classes) {
        this.classes = classes;
    }

    public void classes(@Nonnull final Object... classes) {
        setClasses(getClasses().plus(getModProject().files(classes)));
    }

    public FileCollection getClasses() {
        if (classes == null) {
            classes = getModProject().files();
        }

        return classes;
    }

    public boolean hasClasses()
    {
        return classes != null;
    }
    public void setResources(@Nonnull final FileCollection resources) {
        this.resources = resources;
    }

    public void resources(@Nonnull final Object... resources) {
        setResources(getResources().plus(getModProject().files(resources)));
    }

    public void resource(@Nonnull final Object resource) {
        resources(resource);
    }

    public FileCollection getResources() {
        if (resources == null) {
            resources = getModProject().files();
        }

        return resources;
    }

    public boolean hasResources()
    {
        return resources != null;
    }

    public void setSources(List<SourceSet> sources) {
        this.sources = sources;
    }

    public void sources(@Nonnull final List<SourceSet> sources) {
        getSources().addAll(sources);

        sources.forEach(source -> {
            classes(source.getOutput().getClassesDirs());
            resource(source.getOutput().getResourcesDir());
        });
    }

    public void sources(@Nonnull final SourceSet... sources) {
        sources(Arrays.asList(sources));
    }

    public void source(@Nonnull final SourceSet source) {
        sources(source);
    }

    public List<SourceSet> getSources() {
        if (sources == null) {
            sources = new ArrayList<>();
        }

        return sources;
    }

    public void merge(@Nonnull final ModConfig other, boolean overwrite) {
        if (overwrite) {
            modProject = other.modProject == null ? modProject : other.modProject;
            sources = other.sources == null ? sources : other.sources;
            classes = other.classes == null ? classes : other.classes;
            resources = other.resources == null ? resources : other.resources;
        } else {
            if (other.modProject != null) {
                modProject(other.getModProject());
            }
            
            if (other.resources != null) {
                resources(other.getResources());
            }

            if (other.classes != null) {
                classes(other.getClasses());
            }

            if (other.sources != null) {
                sources(other.getSources());
            }
        }
    }
}
