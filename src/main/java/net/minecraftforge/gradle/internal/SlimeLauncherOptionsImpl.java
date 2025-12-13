/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.SlimeLauncherOptionsNested;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SlimeLauncherOptionsImpl implements SlimeLauncherOptionsInternal {
    private final String name;

    private final Property<String> mainClass = this.getObjects().property(String.class);
    private final Property<Boolean> inheritArgs = this.getObjects().property(Boolean.class);
    private final ListProperty<String> args = this.getObjects().listProperty(String.class);
    private final Property<Boolean> inheritJvmArgs = this.getObjects().property(Boolean.class);
    private final ListProperty<String> jvmArgs = this.getObjects().listProperty(String.class);
    private final ConfigurableFileCollection classpath = this.getObjects().fileCollection();
    private final Property<String> minHeapSize = this.getObjects().property(String.class);
    private final Property<String> maxHeapSize = this.getObjects().property(String.class);
    private final MapProperty<String, String> systemProperties = this.getObjects().mapProperty(String.class, String.class);
    private final MapProperty<String, String> environment = this.getObjects().mapProperty(String.class, String.class);
    private final DirectoryProperty workingDir = this.getObjects().directoryProperty();

    private final Property<Boolean> client = this.getObjects().property(Boolean.class).convention(false);

    private final MapProperty<String, SlimeLauncherOptionsNested> nested = this.getObjects().mapProperty(String.class, SlimeLauncherOptionsNested.class);

    protected abstract @Inject ProjectLayout getProjectLayout();

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
    public Property<Boolean> getInheritArgs() {
        return this.inheritArgs;
    }

    @Override
    public ListProperty<String> getArgs() {
        return this.args;
    }

    @Override
    public Property<Boolean> getInheritJvmArgs() {
        return this.inheritJvmArgs;
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

    @Override
    public MapProperty<String, SlimeLauncherOptionsNested> getNested() {
        return this.nested;
    }

    /* NESTED */

    @Override
    public void with(String sourceSetName, Action<? super SlimeLauncherOptionsNested> action) {
        var child = getObjects().newInstance(SlimeLauncherOptionsImpl.class, this.name);
        action.execute(child);
        this.getNested().put(sourceSetName, child);
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


    /* INHERITANCE */

    @Override
    public SlimeLauncherOptionsInternal inherit(Map<String, RunConfig> configs, String sourceSetName, String name) {
        var target = getObjects().newInstance(SlimeLauncherOptionsImpl.class, name);
        target.getMainClass().convention(this.getMainClass());
        target.getInheritArgs().convention(this.getInheritArgs());
        target.getArgs().convention(this.getArgs());
        target.getInheritJvmArgs().convention(this.getInheritJvmArgs());
        target.getJvmArgs().convention(this.getJvmArgs());
        target.getClasspath().convention(this.getClasspath());
        target.getMinHeapSize().convention(this.getMinHeapSize());
        target.getMaxHeapSize().convention(this.getMaxHeapSize());
        target.getSystemProperties().convention(this.getSystemProperties());
        target.getEnvironment().convention(this.getEnvironment());
        target.getWorkingDir().convention(this.getWorkingDir().orElse(getProjectLayout().getProjectDirectory().dir("runs/" + sourceSetName + '/' + this.name)));
        target.getClient().convention(this.getClient());
        return this.inherit(target, sourceSetName, configs, name);
    }

    private SlimeLauncherOptionsInternal inherit(SlimeLauncherOptionsInternal target, String sourceSetName, Map<String, RunConfig> configs, String name) {
        var config = configs.get(name);
        if (config != null) {
            if (config.parents != null && !config.parents.isEmpty())
                config.parents.forEach(parent -> this.inherit(target, sourceSetName, configs, parent));

            if (config.main != null)
                target.getMainClass().convention(config.main);

            if (config.args != null && !config.args.isEmpty()) {
                if (target.getInheritArgs().getOrElse(Boolean.TRUE)) {
                    var args = new ArrayList<>(config.args);
                    args.addAll(target.getArgs().get());
                    target.getArgs().convention(args);
                }
            }

            if (config.jvmArgs != null && !config.jvmArgs.isEmpty()) {
                if (target.getInheritJvmArgs().getOrElse(Boolean.TRUE)) {
                    var args = new ArrayList<>(config.jvmArgs);
                    args.addAll(target.getJvmArgs().get());
                    target.getJvmArgs().convention(args);
                }
            }

            target.getClient().convention(config.client);

            if (config.buildAllProjects)
                LOGGER.warn("WARNING: ForgeGradle 7 does not support the buildAllProjects feature.");

            if (config.env != null && !config.env.isEmpty())
                target.environment(config.env);

            if (config.props != null && !config.props.isEmpty())
                target.systemProperties(config.props);
        }

        var child = this.getNested().getting(sourceSetName).getOrNull();
        if (child != null) {
            if (child.getMainClass().filter(Util::isPresent).isPresent())
                target.getMainClass().set(child.getMainClass());

            target.args(child.getArgs().getOrElse(List.of()));

            target.jvmArgs(child.getJvmArgs().getOrElse(List.of()));

            if (child.getMaxHeapSize().filter(Util::isPresent).isPresent())
                target.getMaxHeapSize().set(child.getMaxHeapSize());

            if (child.getMinHeapSize().filter(Util::isPresent).isPresent())
                target.getMinHeapSize().set(child.getMinHeapSize());

            target.environment(child.getEnvironment().getOrElse(Map.of()));

            target.systemProperties(child.getSystemProperties().getOrElse(Map.of()));

            target.getWorkingDir().set(child.getWorkingDir());
        }

        return target;
    }

    /* DEBUGGING */

    @Override
    public String toString() {
        return "SlimeLauncherOptionsImpl{" +
            "name=" + name +
            ", mainClass=" + mainClass.getOrNull() +
            ", args=[" + String.join(", ", args.getOrElse(List.of())) + ']' +
            ", jvmArgs=[" + String.join(", ", jvmArgs.getOrElse(List.of())) + ']' +
            ", classpath=[" + classpath.getAsPath() + ']' +
            ", minHeapSize=" + minHeapSize.getOrNull() +
            ", maxHeapSize=" + maxHeapSize.getOrNull() +
            ", systemProperties=" + systemProperties.getOrNull() +
            ", environment=" + environment.getOrNull() +
            ", workingDir=" + workingDir.getOrNull() +
            ", client=" + client.getOrNull() +
            '}';
    }
}
