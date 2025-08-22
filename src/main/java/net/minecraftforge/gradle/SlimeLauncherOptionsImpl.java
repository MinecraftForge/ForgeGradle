/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

abstract class SlimeLauncherOptionsImpl implements SlimeLauncherOptionsInternal {
    private final String name;

    private final Property<String> mainClass = this.getObjects().property(String.class);
    private final ListProperty<Object> args = this.getObjects().listProperty(Object.class);
    private final ListProperty<Object> jvmArgs = this.getObjects().listProperty(Object.class);
    private final ConfigurableFileCollection classpath = this.getObjects().fileCollection();
    private final Property<String> minHeapSize = this.getObjects().property(String.class);
    private final Property<String> maxHeapSize = this.getObjects().property(String.class);
    private final MapProperty<String, Object> systemProperties = this.getObjects().mapProperty(String.class, Object.class);
    private final MapProperty<String, Object> environment = this.getObjects().mapProperty(String.class, Object.class);
    private final DirectoryProperty workingDir = this.getObjects().directoryProperty();

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public SlimeLauncherOptionsImpl(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Property<String> getMainClass() {
        return this.mainClass;
    }

    @Override
    public ListProperty<Object> getArgs() {
        return this.args;
    }

    @Override
    public ListProperty<Object> getJvmArgs() {
        return this.jvmArgs;
    }

    @Override
    public ConfigurableFileCollection getClasspath() {
        return this.classpath;
    }

    @Override
    public Property<String> getMinHeapSize() {
        return this.minHeapSize;
    }

    @Override
    public Property<String> getMaxHeapSize() {
        return this.maxHeapSize;
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return this.systemProperties;
    }

    @Override
    public MapProperty<String, Object> getEnvironment() {
        return this.environment;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return this.workingDir;
    }
}
