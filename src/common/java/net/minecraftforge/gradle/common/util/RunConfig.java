/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.util.MapEntry;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

public class RunConfig extends GroovyObjectSupport implements Serializable {

    public static final String RUNS_GROUP = "ForgeGradle runs";

    private static final String MCP_CLIENT_MAIN = "mcp.client.Start";
    private static final String MC_CLIENT_MAIN = "net.minecraft.client.main.Main";

    private static final long serialVersionUID = 1L;

    private transient final Project project;
    private transient NamedDomainObjectContainer<ModConfig> mods;

    private final String name;

    private Boolean singleInstance = null;

    private String taskName, main, ideaModule, workDir;

    private List<SourceSet> sources;
    private List<RunConfig> parents, children;
    private List<String> args, jvmArgs;
    private boolean forceExit = true;
    private Boolean client; // so we can have it null
    private Boolean inheritArgs;
    private Boolean inheritJvmArgs;
    private transient String folderName;
    private boolean buildAllProjects;

    private Map<String, String> env, props, tokens;
    private Map<String, Supplier<String>> lazyTokens;

    public RunConfig(final Project project, final String name) {
        this.project = project;
        this.name = name;

        this.mods = project.container(ModConfig.class, modName -> new ModConfig(project, modName));
        this.folderName = project.getName();
    }

    public final String getName() {
        return name;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public void taskName(String taskName) {
        setTaskName(taskName);
    }

    public final String getTaskName() {
        if (taskName == null) {
            taskName = getName().replaceAll("[^a-zA-Z0-9\\-_]", "");

            if (!taskName.startsWith("run")) {
                taskName = "run" + Utils.capitalize(taskName);
            }
        }

        return taskName;
    }

    public final String getUniqueFileName() {
        return project.getPath().length() > 1 ? String.join("_", String.join("_", project.getPath().substring(1).split(":")), getTaskName()) : getTaskName();
    }

    public final String getUniqueName() {
        return getUniqueFileName().replaceAll("_", " ");
    }

    public void environment(Map<String, Object> map) {
        this.setEnvironment(map);
    }

    public void setEnvironment(Map<String, Object> map) {
        map.forEach((k, v) -> getEnvironment().put(k, v instanceof File ? ((File) v).getAbsolutePath() : (String) v));
    }

    public void environment(String key, String value) {
        getEnvironment().put(key, value);
    }

    public void environment(String key, File value) {
        getEnvironment().put(key, value.getAbsolutePath());
    }

    public Map<String, String> getEnvironment() {
        if (env == null) {
            env = new HashMap<>();
        }

        return env;
    }

    public void main(String value) {
        this.setMain(value);
    }

    public void setMain(String value) {
        this.main = value;
    }

    public String getMain() {
        return this.main;
    }

    public void inheritArgs(boolean value) {
        setInheritArgs(value);
    }

    public void setInheritArgs(boolean value) {
        this.inheritArgs = value;
    }

    public boolean getInheritArgs() {
        // Both null and true mean inherit the args
        return inheritArgs != Boolean.FALSE;
    }

    public void inheritJvmArgs(boolean value) {
        setInheritJvmArgs(value);
    }

    public void setInheritJvmArgs(boolean value) {
        this.inheritJvmArgs = value;
    }

    public boolean getInheritJvmArgs() {
        // Both null and true mean inherit the args
        return inheritJvmArgs != Boolean.FALSE;
    }

    public void buildAllProjects(boolean value) {
        setBuildAllProjects(value);
    }

    public void setBuildAllProjects(boolean value) {
        this.buildAllProjects = value;
    }

    public boolean getBuildAllProjects() {
        return buildAllProjects;
    }

    public void args(List<Object> values) {
        setArgs(values);
    }

    public void args(Object... values) {
        args(Arrays.asList(values));
    }

    public void arg(Object value) {
        args(value);
    }

    public void setArgs(List<Object> values) {
        values.forEach(value -> getArgs().add(value instanceof File ? ((File) value).getAbsolutePath() : Objects.toString(value)));
    }

    public List<String> getArgs() {
        if (args == null) {
            args = new ArrayList<>();
        }

        return args;
    }

    public void jvmArgs(List<String> values) {
        setJvmArgs(values);
    }

    public void jvmArgs(String... values) {
        jvmArgs(Arrays.asList(values));
    }

    public void jvmArg(String value) {
        jvmArgs(value);
    }

    public void setJvmArgs(List<String> values) {
        getJvmArgs().addAll(values);
    }

    public List<String> getJvmArgs() {
        if (jvmArgs == null) {
            jvmArgs = new ArrayList<>();
        }

        return jvmArgs;
    }

    public void singleInstance(boolean value) {
        this.setSingleInstance(value);
    }

    public void setSingleInstance(boolean singleInstance) {
        this.singleInstance = singleInstance;
    }

    public boolean isSingleInstance() {
        return singleInstance != null && singleInstance;
    }

    public void properties(Map<String, Object> map) {
        this.setProperties(map);
    }

    public void setProperties(Map<String, Object> map) {
        map.forEach((k, v) -> getProperties().put(k, v instanceof File ? ((File) v).getAbsolutePath() : (String) v));
    }

    public void property(String key, String value) {
        getProperties().put(key, value);
    }

    public void property(String key, File value) {
        getProperties().put(key, value.getAbsolutePath());
    }

    public Map<String, String> getProperties() {
        if (props == null) {
            props = new HashMap<>();
        }

        return props;
    }

    public void ideaModule(String value) {
        this.setIdeaModule(value);
    }

    public void setIdeaModule(String value) {
        this.ideaModule = value;
    }

    public final String getIdeaModule() {
        if (ideaModule == null) {
            ideaModule = project.getName().replace(' ', '_') + ".main";
        }

        return ideaModule;
    }

    public void workingDirectory(String value) {
        setWorkingDirectory(value);
    }

    public void workingDirectory(File value) {
        setWorkingDirectory(value.getAbsolutePath());
    }

    public void setWorkingDirectory(String value) {
        this.workDir = value;
    }

    public String getWorkingDirectory() {
        if (workDir == null) {
            workDir = project.file("run").getAbsolutePath();
        }

        return workDir;
    }

    public void forceExit(boolean forceExit) {
        this.setForceExit(forceExit);
    }

    public void setForceExit(boolean forceExit) {
        this.forceExit = forceExit;
    }

    public boolean getForceExit() {
        return this.forceExit;
    }

    public NamedDomainObjectContainer<ModConfig> mods(@SuppressWarnings("rawtypes") Closure closure) {
        return mods.configure(closure);
    }

    public NamedDomainObjectContainer<ModConfig> getMods() {
        return mods;
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

    private Stream<RunConfig> entriesToRuns(MapEntry... entries) {
        return Stream.of(entries).map(entry -> {
            final Project project = entry.getKey() == null
                    ? this.project : this.project.project(entry.getKey().toString());

            final MinecraftExtension minecraft = project.getExtensions().findByType(MinecraftExtension.class);

            return minecraft == null ? null : minecraft.getRuns().maybeCreate(entry.getValue().toString());
        }).filter(Objects::nonNull);
    }

    public void setParents(List<RunConfig> parents) {
        this.parents = parents;
    }

    public void setParents(MapEntry... parents) {
        setParents(entriesToRuns(parents).collect(Collectors.toList()));
    }

    public void setParents(Map<String, String> parents) {
        setParents(parents.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void parents(MapEntry... parents) {
        getParents().addAll(entriesToRuns(parents).collect(Collectors.toList()));
    }

    public void parents(Map<String, String> parents) {
        parents(parents.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void parent(@Nullable String project, @Nullable String parent) {
        parents(new MapEntry(project, parent));
    }

    public void parents(int index, MapEntry... parents) {
        getParents().addAll(index, entriesToRuns(parents).collect(Collectors.toList()));
    }

    public void parents(int index, Map<String, String> parents) {
        parents(index, parents.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void parent(int index, @Nullable String project, @Nullable String parent) {
        parents(index, new MapEntry(project, parent));
    }

    public void parents(RunConfig... parents) {
        getParents().addAll(Arrays.asList(parents));
    }

    public void parent(RunConfig parent) {
        parents(parent);
    }

    public void parents(int index, RunConfig... parents) {
        getParents().addAll(Math.min(index, getParents().size()), Arrays.asList(parents));
    }

    public void parent(int index, RunConfig parent) {
        parents(index, parent);
    }

    public final List<RunConfig> getParents() {
        if (parents == null) {
            parents = new ArrayList<>();
        }

        return parents;
    }

    public void setChildren(List<RunConfig> children) {
        this.children = children;
    }

    public void setChildren(MapEntry... children) {
        setChildren(entriesToRuns(children).collect(Collectors.toList()));
    }

    public void setChildren(Map<String, String> children) {
        setChildren(children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void children(MapEntry... children) {
        getChildren().addAll(entriesToRuns(children).collect(Collectors.toList()));
    }

    public void children(Map<String, String> children) {
        children(children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void child(@Nullable String project, @Nullable String child) {
        children(new MapEntry(project, child));
    }

    public void children(int index, MapEntry... children) {
        getChildren().addAll(index, entriesToRuns(children).collect(Collectors.toList()));
    }

    public void children(int index, Map<String, String> children) {
        children(index, children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }

    public void child(int index, @Nullable String project, @Nullable String child) {
        children(index, new MapEntry(project, child));
    }

    public void children(RunConfig... children) {
        getChildren().addAll(Arrays.asList(children));
    }

    public void child(RunConfig child) {
        children(child);
    }

    public void children(int index, RunConfig... children) {
        getChildren().addAll(Math.min(index, getParents().size()), Arrays.asList(children));
    }

    public void child(int index, RunConfig child) {
        children(index, child);
    }

    public final List<RunConfig> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }

        return children;
    }

    public void merge(final RunConfig other, boolean overwrite) {

        RunConfig first = overwrite ? other : this;
        RunConfig second = overwrite ? this : other;

        List<String> args = new ArrayList<>();
        if (this.getInheritArgs() && other.args != null)
            args.addAll(other.args);
        if (this.args != null)
            args.addAll(this.args);
        this.args = args;
        List<String> jvmArgs = new ArrayList<>();
        if (this.getInheritJvmArgs() && other.jvmArgs != null)
            jvmArgs.addAll(other.jvmArgs);
        if (this.jvmArgs != null)
            jvmArgs.addAll(this.jvmArgs);
        this.jvmArgs = jvmArgs;
        main = first.main == null ? second.main : first.main;
        mods = first.mods == null ? second.mods : first.mods;
        sources = first.sources == null ? second.sources : first.sources;
        workDir = first.workDir == null ? second.workDir : first.workDir;
        ideaModule = first.ideaModule == null ? second.ideaModule : first.ideaModule;
        singleInstance = first.singleInstance == null ? second.singleInstance : first.singleInstance;
        this.client = first.client == null ? second.client : first.client;
        if (overwrite) {
            this.buildAllProjects = other.buildAllProjects;
            this.folderName = other.folderName;
        }

        if (other.env != null) {
            other.env.forEach(overwrite
                    ? (key, value) -> getEnvironment().put(key, value)
                    : (key, value) -> getEnvironment().putIfAbsent(key, value));
        }

        if (other.props != null) {
            other.props.forEach(overwrite
                    ? (key, value) -> getProperties().put(key, value)
                    : (key, value) -> getProperties().putIfAbsent(key, value));
        }

        if (other.mods != null) {
            other.mods.forEach(otherMod -> {
                final ModConfig thisMod = getMods().findByName(otherMod.getName());

                if (thisMod == null) {
                    getMods().add(otherMod);
                } else {
                    thisMod.merge(otherMod, false);
                }
            });
        }
    }

    public void merge(List<RunConfig> runs) {
        runs.stream().distinct().filter(run -> run != this).forEach(run -> merge(run, false));
    }

    public void mergeParents() {
        merge(getParents());
    }

    public void mergeChildren() {
        merge(getChildren());
    }

    public void setTokens(Map<String, String> tokens) {
        this.tokens = new HashMap<>(tokens);
    }

    public void token(String key, String value) {
        getTokens().put(key, value);
    }

    public void tokens(Map<String, String> tokens) {
        getTokens().putAll(tokens);
    }

    public Map<String, String> getTokens() {
        if (tokens == null) {
            tokens = new HashMap<>();
        }

        return tokens;
    }

    public void setLazyTokens(Map<String, Supplier<String>> lazyTokens) {
        this.lazyTokens = new HashMap<>(lazyTokens);
    }

    public void lazyToken(String key, Supplier<String> valueSupplier) {
        getLazyTokens().put(key, valueSupplier);
    }

    public void lazyToken(String key, Closure<String> closure) {
        lazyToken(key, closure::call);
    }

    public void lazyTokens(Map<String, Supplier<String>> lazyTokens) {
        getLazyTokens().putAll(lazyTokens);
    }

    public Map<String, Supplier<String>> getLazyTokens() {
        if (lazyTokens == null) {
            lazyTokens = new HashMap<>();
        }

        return lazyTokens;
    }

    public String replace(Map<String, ?> vars, String value) {
        if (value.length() <= 2 || value.indexOf('{') == -1) {
            return value;
        }

        return Utils.replaceTokens(vars, value);
    }

    public void client(boolean value) {
        setClient(value);
    }

    public void setClient(boolean value) {
        this.client = value;
    }

    public boolean isClient() {
        if (client == null) {
            boolean isTargetClient = getEnvironment().getOrDefault("target", "").contains("client") || getName().contains("client");

            client = isTargetClient || MCP_CLIENT_MAIN.equals(getMain()) || MC_CLIENT_MAIN.equals(getMain());
        }

        return client;
    }

    public List<SourceSet> getAllSources() {
        List<SourceSet> sources = getSources();

        getMods().stream().map(ModConfig::getSources).flatMap(Collection::stream).forEach(sources::add);

        sources = sources.stream().distinct().collect(Collectors.toList());

        if (sources.isEmpty()) {
            final JavaPluginExtension main = project.getExtensions().getByType(JavaPluginExtension.class);

            sources.add(main.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME));
        }

        return sources;
    }

    public void folderName(String folderName) {
        setFolderName(folderName);
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public String getFolderName() {
        return folderName;
    }

    @Override
    public String toString() {
        return "RunConfig[project='" + project.getPath() + "', name='" + getName() + "']";
    }

}
