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
    private final transient Project project;

    private final String name;

    private List<SourceSet> sources;

    public ModConfig(final Project project, final String name) {
        this.project = project;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setSources(List<SourceSet> sources) {
        this.sources = sources;
    }

    public void sources(final List<SourceSet> sources) {
        getSources().addAll(sources);
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
        } else {
            if (other.sources != null) {
                sources(other.getSources());
            }
        }
    }
}
