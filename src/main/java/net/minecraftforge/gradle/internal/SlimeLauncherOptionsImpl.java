/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.SlimeLauncherOptionsNested;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class SlimeLauncherOptionsImpl implements SlimeLauncherOptionsInternal {
    private static final LogLevel level = LogLevel.INFO;

    private final String name;

    private final Property<String> mainClass = this.getObjects().property(String.class);
    private final Property<Boolean> inheritArgs = this.getObjects().property(Boolean.class);
    private final ListProperty<String> args = this.getObjects().listProperty(String.class);
    private final Property<Boolean> inheritJvmArgs = this.getObjects().property(Boolean.class);
    private final ListProperty<String> jvmArgs = this.getObjects().listProperty(String.class);
    private final ConfigurableFileCollection classpath = this.getObjects().fileCollection();
    private final ConfigurableFileCollection extraLibraries = this.getObjects().fileCollection();
    private final Property<String> minHeapSize = this.getObjects().property(String.class);
    private final Property<String> maxHeapSize = this.getObjects().property(String.class);
    private final MapProperty<String, String> systemProperties = this.getObjects().mapProperty(String.class, String.class);
    private final MapProperty<String, String> environment = this.getObjects().mapProperty(String.class, String.class);
    private final DirectoryProperty workingDir = this.getObjects().directoryProperty();

    private final Property<Boolean> client = this.getObjects().property(Boolean.class).convention(false);

    private final MapProperty<String, SlimeLauncherOptionsNested> nested = this.getObjects().mapProperty(String.class, SlimeLauncherOptionsNested.class);

    private final NamedDomainObjectContainer<ModConfigImpl> mods = this.getObjects().domainObjectContainer(ModConfigImpl.class);

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
    public ConfigurableFileCollection getExtraLibraries() {
        return this.extraLibraries;
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

    @Override
    public NamedDomainObjectContainer<ModConfigImpl> getMods() {
        return this.mods;
    }

    /* NESTED */

    @Override
    public void with(String sourceSetName, Action<? super SlimeLauncherOptionsNested> action) {
        var child = getObjects().newInstance(SlimeLauncherOptionsImpl.class, this.name);
        child.getExtraLibraries().setFrom(this.getExtraLibraries());
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
        this.getEnvironment().put(name, this.getProviders().provider(() -> Util.unpack(value).toString()));
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
        LOGGER.log(level, "Baking Launch Options: {} for sourceset {}", name, sourceSetName);
        var target = getObjects().newInstance(SlimeLauncherOptionsImpl.class, name);
        target.getWorkingDir().set(getProjectLayout().getProjectDirectory().dir("runs/" + sourceSetName + '/' + this.name));
        target.getClasspath().setFrom(this.getClasspath());

        // Pull from userdev config
        LOGGER.log(level, "Inheriting Json Parent {}", name);
        target.inheritFromJson("  ", configs, name);

        // Pull from this container
        LOGGER.log(level, "Inheriting From Self");
        target.inherit(this);

        // Gradle is stupid and boolean properties are always present, so only allow upgrading from false -> true
        if (this.getClient().isPresent() && this.getClient().getOrElse(Boolean.FALSE)) {
            LOGGER.log(level, "  Client: {}", this.getClient().get());
            target.getClient().set(this.getClient().get());
        }

        // Inherit from sourceset specific container
        var child = this.getNested().getting(sourceSetName).getOrNull();
        if (child != null) {
            LOGGER.log(level, "Inheriting from Child");
            target.inherit(child);
            if (!child.getExtraLibraries().isEmpty())
                target.setExtraLibraries(child.getExtraLibraries());
        } else {
            target.setExtraLibraries(this.getExtraLibraries());
        }
        return target;
    }

    // Set the values from
    private void inheritFromJson(String prefix, Map<String, RunConfig> configs, String name) {
        var config = configs.get(name);
        if (config == null)
            return;

        if (config.parents != null) {
            for (var parent : config.parents) {
                LOGGER.log(level, "{}Inheriting Json Parent {}", prefix, name);
                this.inheritFromJson(prefix + "  ", configs, parent);
            }
        }

        if (config.main != null) {
            LOGGER.log(level, "{}Main-Class: {}", prefix, config.main);
            getMainClass().set(config.main);
        }

        if (config.args != null && !config.args.isEmpty()) {
            LOGGER.log(level, "{}Args: {}", prefix, config.args);
            var args = new ArrayList<>(config.args);
            args.addAll(this.getArgs().getOrElse(List.of()));
            getArgs().set(args);
        }

        if (config.jvmArgs != null && !config.jvmArgs.isEmpty()) {
            LOGGER.log(level, "{}JVM Args: {}", prefix, config.jvmArgs);
            var args = new ArrayList<>(config.jvmArgs);
            args.addAll(this.getJvmArgs().getOrElse(List.of()));
            getJvmArgs().set(args);
        }

        LOGGER.log(level, "{}Client: {}", prefix, config.client);
        getClient().convention(config.client); // Neds to be convention because boolean properties can only be set once

        if (config.buildAllProjects)
            LOGGER.warn("WARNING: ForgeGradle 7 does not support the buildAllProjects feature.");

        if (config.env != null && !config.env.isEmpty()) {
            for (var entry : config.env.entrySet()) {
                LOGGER.log(level, "{}Env: {} = {}", prefix, entry.getKey(), entry.getValue());
                this.environment(entry.getKey(), entry.getValue());
            }
        }

        if (config.props != null && !config.props.isEmpty()) {
            for (var entry : config.props.entrySet()) {
                LOGGER.log(level, "{}System: {} = {}", prefix, entry.getKey(), entry.getValue());
                this.systemProperty(entry.getKey(), entry.getValue());
            }
        }
    }

    private void inherit(SlimeLauncherOptionsNested target) {
        if (target.getMainClass().isPresent()) {
            LOGGER.log(level, "  MainClass: {}", target.getMainClass().get());
            getMainClass().set(target.getMainClass());
        }

        // ListProperties are ALWAYS present, there is no way to tell if this is intentionally set to empty
        if (target.getArgs().isPresent() && !target.getArgs().get().isEmpty()) {
            var args = new ArrayList<>(this.getArgs().getOrElse(List.of()));
            if (!target.getInheritArgs().getOrElse(Boolean.TRUE))
                args.clear();
            args.addAll(target.getArgs().get());
            getArgs().set(args);
            LOGGER.log(level, "  Args: {}", args);
        }

        // ListProperties are ALWAYS present, there is no way to tell if this is intentionally set to empty
        if (target.getJvmArgs().isPresent() && !target.getJvmArgs().get().isEmpty()) {
            var args = new ArrayList<>(this.getJvmArgs().getOrElse(List.of()));
            if (!target.getInheritJvmArgs().getOrElse(Boolean.TRUE))
                args.clear();
            args.addAll(target.getJvmArgs().get());
            getJvmArgs().set(args);
            LOGGER.log(level, "  JVM Args: {}", args);
        }

        if (target.getMinHeapSize().isPresent()) {
            LOGGER.log(level, "  Min Heap Space: {}", target.getMinHeapSize().get());
            getMinHeapSize().set(target.getMinHeapSize());
        }

        if (target.getMaxHeapSize().isPresent()) {
            LOGGER.log(level, "  Max Heap Space: {}", target.getMaxHeapSize().get());
            getMaxHeapSize().set(target.getMaxHeapSize());
        }

        if (target.getEnvironment().isPresent()) {
            for (var entry : target.getEnvironment().get().entrySet()) {
                LOGGER.log(level, "  Env: {} = {}", entry.getKey(), entry.getValue());
                this.environment(entry.getKey(), entry.getValue());
            }
        }

        if (target.getSystemProperties().isPresent()) {
            for (var entry : target.getSystemProperties().get().entrySet()) {
                LOGGER.log(level, "  System: {} = {}", entry.getKey(), entry.getValue());
                this.systemProperty(entry.getKey(), entry.getValue());
            }
        }

        if (target.getWorkingDir().isPresent()) {
            LOGGER.log(level, "  WorkingDir: {}", target.getWorkingDir().get());
            this.getWorkingDir().set(target.getWorkingDir());
        }

        for (var mod : ((SlimeLauncherOptionsInternal) target).getMods()) {
            LOGGER.log(level, "  Mod: {}", mod.getName());
            var thisMod = this.getMods().maybeCreate(mod.getName());
            var thisModSources = thisMod.getSources();
            for (var source : mod.getSources()) {
                if (thisModSources.stream().noneMatch(targetMod -> targetMod.getName().equals(mod.getName()))) {
                    LOGGER.log(level, "    Source: {}", source.getName());
                    thisMod.sourceInternal(source);
                }
            }
        }
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

    public static abstract class ModConfigImpl implements ModConfigInternal {
        private final String name;
        private final ArrayList<SourceSetNested> sourceSets = new ArrayList<>();

        protected abstract @Inject ObjectFactory getObjects();

        @Inject
        public ModConfigImpl(String name) {
            this.name = name;
        }

        @Override
        public @Internal String getName() {
            return this.name;
        }

        @Override
        public @Nested List<SourceSetNested> getSources() {
            return this.sourceSets;
        }

        @Override
        public void setSourcesInternal(List<SourceSetNested> sources) {
            this.sourceSets.clear();
            this.sourcesInternal(sources);
        }

        @Override
        public void sourcesInternal(List<SourceSetNested> sources) {
            this.sourceSets.addAll(sources);
        }

        @Override
        public void sourcesInternal(SourceSetNested... sources) {
            this.sourceSets.addAll(Arrays.asList(sources));
        }

        @Override
        public void sourceInternal(SourceSetNested source) {
            this.sourceSets.add(source);
        }

        @Override
        public void setSources(List<SourceSet> sources) {
            this.sourceSets.clear();
            this.sources(sources);
        }

        @Override
        public void sources(List<SourceSet> sources) {
            for (var source : sources)
                this.source(source);
        }

        @Override
        public void sources(SourceSet... sources) {
            for (var source : sources)
                this.source(source);
        }

        @Override
        public void source(SourceSet source) {
            this.sourceSets.add(getObjects().newInstance(SourceSetNested.class, source));
        }
    }
}
