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

import com.google.common.collect.ImmutableList;
import groovy.util.MapEntry;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RunConfig implements Serializable {

    private static final String MCP_CLIENT_MAIN = "mcp.client.Start";
    private static final String MC_CLIENT_MAIN = "net.minecraft.client.main.Main";

    private static final long serialVersionUID = 1L;

    private transient final Project project;
    private final String name;

    private String taskName, main, ideaModule, workDir;

    private boolean singleInstance = false;

    private List<String> args, jvmArgs;
    private List<SourceSet> sources;
    private List<RunConfig> parents, children;
    private Map<String, String> env, props, tokens;

    public RunConfig(@Nonnull final Project project, @Nonnull final String name) {
        this.project = project;
        this.name = name;
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
            taskName = getName().replaceAll("[^a-zA-Z0-9\\-_]","");

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
        map.forEach((k,v) -> getEnvironment().put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
    }
    public void environment(String key, String value) {
        getEnvironment().put(key, value);
    }
    public void environment(String key, File value) {
        getEnvironment().put(key, value.getAbsolutePath());
    }
    public Map<String, String> getEnvironment() {
        if (env == null)
            env = new HashMap<>();
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

    public void arg(String value) {
        getArgs().add(value);
    }
    public void setArgs(List<String> values) {
        getArgs().addAll(values);
    }
    public List<String> getArgs() {
        if (args == null)
            args = new ArrayList<>();
        return args;
    }

    public void jvmArg(String value) {
        getJvmArgs().add(value);
    }
    public void jvmArgs(List<String> values) {
        getJvmArgs().addAll(values);
    }
    public List<String> getJvmArgs() {
        if (jvmArgs == null)
            jvmArgs = new ArrayList<>();
        return jvmArgs;
    }

    public void singleInstance(boolean value) {
        this.setSingleInstance(value);
    }
    public void setSingleInstance(boolean singleInstance) {
        this.singleInstance = singleInstance;
    }
    public boolean isSingleInstance() {
        return singleInstance;
    }

    public void properties(Map<String, Object> map) {
        this.setProperties(map);
    }
    public void setProperties(Map<String, Object> map) {
        map.forEach((k,v) -> getProperties().put(k, v instanceof File ? ((File)v).getAbsolutePath() : (String)v));
    }
    public void property(String key, String value) {
        getProperties().put(key, value);
    }
    public void property(String key, File value) {
        getProperties().put(key, value.getAbsolutePath());
    }
    public Map<String, String> getProperties() {
        if (props == null)
            props = new HashMap<>();
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
            ideaModule = project.getName() + "_main";
        }

        return ideaModule;
    }

    public void workingDirectory(String value) {
        this.setWorkingDirectory(value);
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

    public void source(SourceSet value) {
        this.getSources().add(value);
    }
    public void setSources(List<SourceSet> value) {
        this.sources = value;
    }
    public List<SourceSet> getSources() {
        if (this.sources == null)
            this.sources = new ArrayList<>();
        return this.sources;
    }
    private List<File> getSourceDirs() {
        final List<File> ret = new ArrayList<>();
        getSources().forEach(set -> {
            ret.add(set.getOutput().getResourcesDir());
            ret.addAll(set.getOutput().getClassesDirs().getFiles());
            ret.addAll(set.getOutput().getDirs().getFiles());
        });
        return ret;
    }

    public void setParents(List<RunConfig> parents) {
        this.parents = parents;
    }
    public void parents(@Nonnull RunConfig... parents) {
        getParents().addAll(Arrays.asList(parents));
    }
    public void parent(@Nonnull RunConfig parent) {
        parents(parent);
    }
    public void parent(int index, @Nonnull RunConfig parent) {
        getParents().add(Math.min(index, getParents().size()), parent);
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
    private Stream<RunConfig> entriesToRuns(@Nonnull MapEntry... children) {
        return Stream.of(children).map(entry -> {
            final Project project = entry.getKey() == null
                    ? this.project : this.project.project(entry.getKey().toString());

            final MinecraftExtension minecraft = project.getExtensions().findByType(MinecraftExtension.class);

            return minecraft == null ? null : minecraft.getRuns().maybeCreate(entry.getValue().toString());
        }).filter(Objects::nonNull);
    }
    public void setChildren(@Nonnull MapEntry... children) {
        setChildren(entriesToRuns(children).collect(Collectors.toList()));
    }
    public void setChildren(@Nonnull Map<String, String> children) {
        setChildren(children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }
    public void children(@Nonnull MapEntry... children) {
        getChildren().addAll(entriesToRuns(children).collect(Collectors.toList()));
    }
    public void children(@Nonnull Map<String, String> children) {
        children(children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }
    public void child(@Nullable String project, @Nullable String child) {
        children(new MapEntry(project, child));
    }
    public void children(int index, @Nonnull MapEntry... children) {
        getChildren().addAll(index, entriesToRuns(children).collect(Collectors.toList()));
    }
    public void children(int index, @Nonnull Map<String, String> children) {
        children(index, children.entrySet().stream()
                .map((entry) -> new MapEntry(entry.getKey(), entry.getValue()))
                .toArray(MapEntry[]::new));
    }
    public void child(int index, @Nullable String project, @Nullable String child) {
        children(index, new MapEntry(project, child));
    }
    public void children(@Nonnull RunConfig... children) {
        getChildren().addAll(Arrays.asList(children));
    }
    public void child(@Nonnull RunConfig child) {
        children(child);
    }
    public void children(int index, @Nonnull RunConfig... children) {
        getChildren().addAll(Math.min(index, getParents().size()), Arrays.asList(children));
    }
    public void child(int index, @Nonnull RunConfig child) {
        children(index, child);
    }
    public final List<RunConfig> getChildren() {
        if (children == null) {
            children = new ArrayList<>();
        }

        return children;
    }

    public void merge(RunConfig other, boolean overwrite) {
        this.singleInstance = other.singleInstance; // This always overwrite cuz there is no way to tell if it's set
        if (overwrite) {
            this.args = other.args == null ? this.args : other.args;
            this.main = other.main == null ? this.main : other.main;
            this.workDir = other.workDir == null ? this.workDir : other.workDir;
            this.ideaModule = other.ideaModule == null ? this.ideaModule : other.ideaModule;
            this.sources = other.sources == null ? this.sources : other.sources;

            if (other.env != null) {
                other.env.forEach((k,v) -> getEnvironment().put(k, v));
            }

            if (other.props != null) {
                other.props.forEach((k,v) -> getProperties().put(k, v));
            }
        } else {
            this.args = other.args == null || this.args != null ? this.args : other.args;
            this.main = other.main == null || this.main != null ? this.main : other.main;
            this.workDir = other.workDir == null || this.workDir != null ? this.workDir : other.workDir;
            this.ideaModule = other.ideaModule == null || this.ideaModule != null ? this.ideaModule : other.ideaModule;

            if (other.env != null) {
                other.env.forEach((k,v) -> getEnvironment().putIfAbsent(k, v));
            }

            if (other.props != null) {
                other.props.forEach((k,v) -> getProperties().putIfAbsent(k, v));
            }

            if (other.sources != null) {
                getSources().addAll(0, other.sources);
            }
        }
    }
    public void merge(@Nonnull List<RunConfig> runs) {
        runs.stream().distinct().filter(run -> run != this).forEach(run -> merge(run, false));
    }
    public void mergeParents() {
        merge(getParents());
    }
    public void mergeChildren() {
        merge(getChildren());
    }

    public void setTokens(Map<String, String> tokens) {
        this.tokens = tokens;
    }
    private Map<String, String> getTokens() {
        if (tokens == null) {
            tokens = new HashMap<>();
        }

        return tokens;
    }

    private void replaceTokens() {
        getEnvironment().keySet().forEach(key -> getEnvironment().compute(key, (k, value) -> replace(getTokens(), value)));
        getProperties().keySet().forEach(key -> getProperties().compute(key, (k, value) -> replace(getTokens(), value)));
    }

    private String replace(Map<String, String> vars, String value) {
        if (value == null || value.length() <= 2 || value.charAt(0) != '{' || value.charAt(value.length() - 1) != '}')
            return value;
        String key = value.substring(1, value.length() - 1);
        String resolved = vars.get(key);
        return resolved == null ? value : resolved;
    }

    public boolean isClient() {
        boolean isTargetClient = getEnvironment().getOrDefault("target", "").contains("client");
        return isTargetClient || MCP_CLIENT_MAIN.equals(getMain()) || MC_CLIENT_MAIN.equals(getMain());
    }

    @Nonnull
    @SuppressWarnings("UnstableApiUsage")
    public final TaskProvider<JavaExec> createRunTask(@Nonnull final TaskProvider<Task> prepareRuns, @Nonnull final List<String> additionalClientArgs) {
        return createRunTask(prepareRuns.get(), additionalClientArgs);
    }

    @Nonnull
    @SuppressWarnings("UnstableApiUsage")
    public final TaskProvider<JavaExec> createRunTask(@Nonnull final Task prepareRuns, @Nonnull final List<String> additionalClientArgs) {
        final List<SourceSet> sources = getSources().isEmpty()
                ? ImmutableList.of(project.getConvention().getPlugin(JavaPluginConvention.class)
                        .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME))
                : getSources();

        getTokens().put("class_roots", sources.stream()
                .map(source -> source.getOutput().getClassesDirs().getFiles())
                .flatMap(Collection::stream)
                .distinct().map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));

        getTokens().put("resource_roots", sources.stream()
                .map(source -> source.getOutput().getResourcesDir())
                .distinct().map(File::getAbsolutePath)
                .collect(Collectors.joining(File.pathSeparator)));

        replaceTokens();

        if (isClient()) {
            jvmArgs(additionalClientArgs);
        }

        TaskProvider<Task> prepareRun = project.getTasks().register("prepare" + Utils.capitalize(getTaskName()), Task.class, task -> {
            task.dependsOn(prepareRuns, sources.stream().map(SourceSet::getClassesTaskName).toArray());

            File workDir = new File(getWorkingDirectory());

            if (!workDir.exists()) {
                workDir.mkdirs();
            }
        });

        return project.getTasks().register(getTaskName(), JavaExec.class, task -> {
            task.dependsOn(prepareRun.get());

            File workDir = new File(getWorkingDirectory());

            if (!workDir.exists()) {
                workDir.mkdirs();
            }

            task.setWorkingDir(workDir);
            task.setMain(getMain());

            task.args(getArgs());
            task.jvmArgs(getJvmArgs());
            task.environment(getEnvironment());
            task.systemProperties(getProperties());

            sources.stream().map(SourceSet::getRuntimeClasspath).forEach(task::classpath);

            // Stop after this run task so it doesn't try to execute the run tasks, and their dependencies, of sub projects
            task.doLast(t -> System.exit(0)); // TODO: Find better way to stop gracefully
        });
    }

    @Override
    public String toString() {
        return "RunConfig[project='" + project.getPath() + "', name='" + getName() + "']";
    }

}
