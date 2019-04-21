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

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import net.minecraftforge.gradle.common.task.DownloadAssets;
import net.minecraftforge.gradle.common.task.ExtractNatives;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public abstract class MinecraftExtension extends GroovyObjectSupport {

    protected final Project project;
    protected final NamedDomainObjectContainer<RunConfig> runs;

    protected String mappings;
    protected List<File> accessTransformers;

    @Inject
    public MinecraftExtension(final Project project) {
        this.project = project;

        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));
    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<RunConfig> runs(Closure closure) {
        return runs.configure(closure);
    }

    public NamedDomainObjectContainer<RunConfig> getRuns() {
        return runs;
    }

    public void propertyMissing(String name, Object value) {
        if (!(value instanceof Closure)) {
            throw new MissingPropertyException(name);
        }

        final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    public void setMappings(String mappings) {
        this.mappings = mappings;
    }

    public abstract void mappings(String channel, String version);

    public void mappings(Map<String, CharSequence> mappings) {
        CharSequence channel = mappings.get("channel");
        CharSequence version = mappings.get("version");

        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify both mappings channel and version");
        }

        mappings(channel.toString(), version.toString());
    }

    public String getMappings() {
        return mappings;
    }

    public void setAccessTransformers(List<File> accessTransformers) {
        this.accessTransformers = new ArrayList<>(accessTransformers);
    }

    public void setAccessTransformers(File... accessTransformers) {
        setAccessTransformers(Arrays.asList(accessTransformers));
    }

    public void setAccessTransformer(File accessTransformers) {
        setAccessTransformers(accessTransformers);
    }

    public void accessTransformer(File... accessTransformers) {
        getAccessTransformers().addAll(Arrays.asList(accessTransformers));
    }

    public void accessTransformers(File... accessTransformers) {
        accessTransformer(accessTransformers);
    }

    public List<File> getAccessTransformers() {
        if (accessTransformers == null) {
            accessTransformers = new ArrayList<>();
        }

        return accessTransformers;
    }

    @SuppressWarnings("UnstableApiUsage")
    public void createRunConfigTasks(final TaskProvider<ExtractNatives> extractNatives, final TaskProvider<DownloadAssets> downloadAssets) {
        createRunConfigTasks(extractNatives.get(), downloadAssets.get());
    }

    @SuppressWarnings("UnstableApiUsage")
    public void createRunConfigTasks(final ExtractNatives extractNatives, final DownloadAssets downloadAssets) {
        final TaskProvider<Task> prepareRuns = project.getTasks().register("prepareRuns", Task.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(extractNatives, downloadAssets);
        });

        final TaskProvider<Task> makeSrcDirs = project.getTasks().register("makeSrcDirs", Task.class, task -> {
            task.doFirst(t -> {
                final JavaPluginConvention java = task.getProject().getConvention().getPlugin(JavaPluginConvention.class);

                java.getSourceSets().forEach(s -> s.getAllSource()
                        .getSrcDirs().stream().filter(f -> !f.exists()).forEach(File::mkdirs));
            });
        });

        getRuns().forEach(RunConfig::mergeParents);

        // Create run configurations _AFTER_ all projects have evaluated so that _ALL_ run configs exist and have been configured
        project.getGradle().projectsEvaluated(gradle -> {
            VersionJson json = null;

            try {
                json = Utils.loadJson(extractNatives.getMeta(), VersionJson.class);
            } catch (IOException ignored) {
            }

            List<String> additionalClientArgs = json != null ? json.getPlatformJvmArgs() : Collections.emptyList();

            getRuns().forEach(RunConfig::mergeChildren);
            getRuns().forEach(run -> run.createRunTask(prepareRuns, additionalClientArgs));

            EclipseHacks.doEclipseFixes(this, extractNatives, downloadAssets, makeSrcDirs);

            IDEUtils.createIDEGenRunsTasks(this, prepareRuns, makeSrcDirs);
        });
    }

}
