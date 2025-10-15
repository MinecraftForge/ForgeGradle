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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

abstract class SlimeLauncherOptionsImpl implements SlimeLauncherOptionsInternal {
    private final String name;

    private final Property<String> mainClass = this.getObjects().property(String.class);
    private final ListProperty<String> args = this.getObjects().listProperty(String.class);
    private final ListProperty<String> jvmArgs = this.getObjects().listProperty(String.class);
    private final ConfigurableFileCollection classpath = this.getObjects().fileCollection();
    private final Property<String> minHeapSize = this.getObjects().property(String.class);
    private final Property<String> maxHeapSize = this.getObjects().property(String.class);
    private final MapProperty<String, String> systemProperties = this.getObjects().mapProperty(String.class, String.class);
    private final MapProperty<String, String> environment = this.getObjects().mapProperty(String.class, String.class);
    private final DirectoryProperty workingDir = this.getObjects().directoryProperty();

    private final Property<Boolean> client = this.getObjects().property(Boolean.class).convention(false);

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ProviderFactory getProviders();

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
    public ListProperty<String> getArgs() {
        return this.args;
    }

    @Override
    public ListProperty<String> getJvmArgs() {
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
    public MapProperty<String, String> getSystemProperties() {
        return this.systemProperties;
    }

    @Override
    public MapProperty<String, String> getEnvironment() {
        return this.environment;
    }

    @Override
    public DirectoryProperty getWorkingDir() {
        return this.workingDir;
    }

    @Override
    public Property<Boolean> getClient() {
        return this.client;
    }

    /* SETTERS */

    public void args(Object args) {
        this.getArgs().add(this.getProviders().provider(() -> Util.unpack(args).toString()));
    }

    public void args(Object... args) {
        this.getArgs().addAll(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>(args.length);
            for (var arg : args) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void args(Iterable<?> args) {
        this.getArgs().addAll(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>();
            for (var arg : args) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void args(Provider<? extends Iterable<?>> args) {
        this.getArgs().addAll(args.map(iterable -> {
            var ret = new ArrayList<String>();
            for (var arg : iterable) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void setArgs(String... args) {
        this.getArgs().set(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>();
            for (var arg : args) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void jvmArgs(Object jvmArgs) {
        this.getJvmArgs().add(this.getProviders().provider(() -> Util.unpack(jvmArgs).toString()));
    }

    public void jvmArgs(Object... jvmArgs) {
        this.getJvmArgs().addAll(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>();
            for (var arg : jvmArgs) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void jvmArgs(Iterable<?> jvmArgs) {
        this.getJvmArgs().addAll(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>();
            for (var arg : jvmArgs) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void jvmArgs(Provider<? extends Iterable<?>> jvmArgs) {
        this.getJvmArgs().addAll(jvmArgs.map(iterable -> {
            var ret = new ArrayList<String>();
            for (var arg : iterable) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    public void setJvmArgs(Object... jvmArgs) {
        this.getJvmArgs().set(this.getProviders().provider(() -> {
            var ret = new ArrayList<String>();
            for (var arg : jvmArgs) {
                ret.add(Util.unpack(arg).toString());
            }
            return ret;
        }));
    }

    @Override
    public void systemProperty(String name, Object value) {
        this.getSystemProperties().put(name, this.getProviders().provider(() -> Util.unpack(value).toString()));
    }

    @Override
    public void systemProperties(Map<String, ?> properties) {
        for (var entry : properties.entrySet()) {
            this.getSystemProperties().put(entry.getKey(), this.getProviders().provider(() -> Util.unpack(entry.getValue()).toString()));
        }
    }

    @Override
    public void systemProperties(Provider<? extends Map<String, ?>> properties) {
        this.getSystemProperties().putAll(properties.map(map -> {
            var ret = new HashMap<String, String>(map.size());
            for (var entry : map.entrySet()) {
                ret.put(entry.getKey(), Util.unpack(entry.getValue()).toString());
            }
            return ret;
        }));
    }

    @Override
    public void environment(String name, Object value) {
        this.getSystemProperties().put(name, this.getProviders().provider(() -> Util.unpack(value).toString()));
    }

    @Override
    public void environment(Map<String, ?> environment) {
        for (var entry : environment.entrySet()) {
            this.getEnvironment().put(entry.getKey(), this.getProviders().provider(() -> Util.unpack(entry.getValue()).toString()));
        }
    }

    @Override
    public void environment(Provider<? extends Map<String, ?>> properties) {
        this.getEnvironment().putAll(properties.map(map -> {
            var ret = new HashMap<String, String>(map.size());
            for (var entry : map.entrySet()) {
                ret.put(entry.getKey(), Util.unpack(entry.getValue()).toString());
            }
            return ret;
        }));
    }

}
