/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.SourceSet;

import groovy.lang.GroovyObjectSupport;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig extends GroovyObjectSupport {

    private transient final Project project;

    private final String name;
    private FileCollection resources;
    private FileCollection classes;

    private List<SourceSet> sources;

    public ModConfig(final Project project, final String name) {
        this.project = project;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setClasses(FileCollection classes) {
        this.classes = classes;
    }

    public void classes(final Object... classes) {
        setClasses(getClasses().plus(project.files(classes)));
    }

    public FileCollection getClasses() {
        if (classes == null) {
            classes = project.files();
        }

        return classes;
    }

    public boolean hasClasses()
    {
        return classes != null;
    }
    public void setResources(final FileCollection resources) {
        this.resources = resources;
    }

    public void resources(final Object... resources) {
        setResources(getResources().plus(project.files(resources)));
    }

    public void resource(final Object resource) {
        resources(resource);
    }

    public FileCollection getResources() {
        if (resources == null) {
            resources = project.files();
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

    public void sources(final List<SourceSet> sources) {
        getSources().addAll(sources);

        sources.forEach(source -> {
            classes(source.getOutput().getClassesDirs());
            resource(source.getOutput().getResourcesDir());
        });
    }

    public void sources(final SourceSet... sources) {
        sources(Arrays.asList(sources));
    }

    public void source(final SourceSet source) {
        sources(source);
    }

    public List<SourceSet> getSources() {
        if (sources == null) {
            sources = new ArrayList<>();
        }

        return sources;
    }

    public void merge(final ModConfig other, boolean overwrite) {
        if (overwrite) {
            sources = other.sources == null ? sources : other.sources;
            classes = other.classes == null ? classes : other.classes;
            resources = other.resources == null ? resources : other.resources;
        } else {
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
