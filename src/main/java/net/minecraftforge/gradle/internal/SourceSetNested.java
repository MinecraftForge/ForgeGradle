/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetOutput;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;

abstract class SourceSetNested {
    private final String name;
    private final FileCollection compileClasspath;
    private final FileCollection annotationProcessorPath;
    private final FileCollection runtimeClasspath;
    private final SourceSetOutputNested output;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public SourceSetNested(SourceSet sourceSet) {
        this.name = sourceSet.getName();
        this.compileClasspath = getObjects().fileCollection().from(sourceSet.getCompileClasspath());
        this.annotationProcessorPath = getObjects().fileCollection().from(sourceSet.getAnnotationProcessorPath());
        this.runtimeClasspath = getObjects().fileCollection().from(sourceSet.getRuntimeClasspath());
        this.output = getObjects().newInstance(SourceSetOutputNested.class, sourceSet.getOutput());
    }

    protected @Internal String getName() {
        return this.name;
    }

    protected @InputFiles @Classpath FileCollection getCompileClasspath() {
        return this.compileClasspath;
    }

    protected @InputFiles @Classpath FileCollection getAnnotationProcessorPath() {
        return this.annotationProcessorPath;
    }

    protected @InputFiles @Classpath FileCollection getRuntimeClasspath() {
        return this.runtimeClasspath;
    }

    protected @Nested SourceSetOutputNested getOutput() {
        return this.output;
    }

    static abstract class SourceSetOutputNested {
        private final FileCollection files;
        private final FileCollection classesDirs;
        private final @Nullable File resourcesDir;
        private final FileCollection dirs;
        private final FileCollection generatedSourcesDirs;

        protected abstract @Inject ObjectFactory getObjects();

        @Inject
        public SourceSetOutputNested(SourceSetOutput output) {
            this.files = getObjects().fileCollection().from(output);
            this.classesDirs = getObjects().fileCollection().from(output.getClassesDirs());
            this.resourcesDir = output.getResourcesDir();
            this.dirs = getObjects().fileCollection().from(output.getDirs());
            this.generatedSourcesDirs = getObjects().fileCollection().from(output.getGeneratedSourcesDirs());
        }

        protected @Internal FileCollection getAsFileCollection() {
            return this.files;
        }

        protected @Internal FileCollection getClassesDirs() {
            return this.classesDirs;
        }

        protected @Internal @Nullable File getResourcesDir() {
            return this.resourcesDir;
        }

        protected @Internal FileCollection getDirs() {
            return this.dirs;
        }

        protected @Internal FileCollection getGeneratedSourcesDirs() {
            return this.generatedSourcesDirs;
        }
    }
}
