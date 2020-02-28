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
import net.minecraftforge.gradle.common.util.runs.RunConfigGenerator;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;

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

    protected String mapping_channel;
    protected String mapping_version;
    protected List<File> accessTransformers;
    protected List<File> sideAnnotationStrippers;
    protected Mirror mirror;

    @Inject
    public MinecraftExtension(final Project project) {
        this.project = project;
        mirror = new Mirror();
        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));
    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<RunConfig> runs(@SuppressWarnings("rawtypes") Closure closure) {
        return runs.configure(closure);
    }

    public NamedDomainObjectContainer<RunConfig> getRuns() {
        return runs;
    }

    public void propertyMissing(String name, Object value) {
        if (!(value instanceof Closure)) {
            throw new MissingPropertyException(name);
        }

        @SuppressWarnings("rawtypes")
        final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    @Deprecated  //Remove when we can break things.
    public void setMappings(String mappings) {
        int idx = mappings.lastIndexOf('_');
        if (idx == -1)
            throw new RuntimeException("Invalid mapping string format, must be {channel}_{version}. Consider using mappings(channel, version) directly.");
        String channel = mappings.substring(0, idx);
        String version = mappings.substring(idx + 1);
        mappings(channel, version);
    }

    public void mappings(String channel, String version) {
        this.mapping_channel = channel;
        this.mapping_version = version;
    }

    public void mappings(Map<String, CharSequence> mappings) {
        CharSequence channel = mappings.get("channel");
        CharSequence version = mappings.get("version");

        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify both mappings channel and version");
        }

        mappings(channel.toString(), version.toString());
    }

    public void mirrors(Map<String,String> mirrors){
        String minecraftAssetsURL = mirrors.get("assets");
        String minecraftLibraryURL = mirrors.get("libraries");
        String forgeMaven = mirrors.get("forgemaven");
        String mavenCentral= mirrors.get("mavencentral");
        this.mirror.setForgeMaven(addSlash(forgeMaven));
        this.mirror.setMavenCentral(addSlash(mavenCentral));
        this.mirror.setMinecraftAssetsURL(addSlash(minecraftAssetsURL));
        this.mirror.setMinecraftLibraryURL(addSlash(minecraftLibraryURL));
    }

    public Mirror getMirror() {
        return this.mirror;
    }

    private String addSlash(String url) {
        if(url == null) return null;
        if(url.substring(url.length()-1, url.length()).equals("/")) {
            return url;
        }
        return url+"/";
    }

    public String getMappings() {
        return mapping_channel == null || mapping_version == null ? null : mapping_channel + '_' + mapping_version;
    }
    public String getMappingChannel() {
        return mapping_channel;
    }
    public void setMappingChannel(String value) {
        this.mapping_channel = value;
    }
    public String getMappingVersion() {
        return mapping_version;
    }
    public void setMappingVersion(String value) {
        this.mapping_version = value;
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

    public void setSideAnnotationStrippers(List<File> value) {
        this.sideAnnotationStrippers = new ArrayList<>(value);
    }
    public void setSideAnnotationStrippers(File... value) {
        setSideAnnotationStrippers(Arrays.asList(value));
    }
    public void setSideAnnotationStripper(File value) {
        getSideAnnotationStrippers().add(value);
    }
    public void setSideAnnotationStripper(File... values) {
        for (File value : values)
            setSideAnnotationStripper(value);
    }
    public void sideAnnotationStripper(File... values) {
        setSideAnnotationStripper(values);
    }
    public void sideAnnotationStrippers(File... values) {
        sideAnnotationStripper(values);
    }
    public List<File> getSideAnnotationStrippers() {
        if (sideAnnotationStrippers == null) {
            sideAnnotationStrippers = new ArrayList<>();
        }
        return sideAnnotationStrippers;
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
            getRuns().forEach(run -> RunConfigGenerator.createRunTask(run, project, prepareRuns, additionalClientArgs));

            EclipseHacks.doEclipseFixes(this, extractNatives, downloadAssets, makeSrcDirs);

            RunConfigGenerator.createIDEGenRunsTasks(this, prepareRuns, makeSrcDirs, additionalClientArgs);
        });
    }
    public class Mirror{
        private String ForgeMaven=null;
        private String MavenCentral=null;
        private String MinecraftAssetsURL=null;
        private String MinecraftLibraryURL=null;

        public void setForgeMaven(String minecraftVersionURL) {
            ForgeMaven = minecraftVersionURL;
        }

        public void setMinecraftAssetsURL(String minecraftAssetsURL) {
            MinecraftAssetsURL = minecraftAssetsURL;
        }

        public void setMinecraftLibraryURL(String minecraftLibraryURL) { MinecraftLibraryURL = minecraftLibraryURL; }

        public void setMavenCentral(String mavenCentral) {
            MavenCentral = mavenCentral;
        }

        public String getMavenCentral() {
            return MavenCentral;
        }

        public String getForgeMaven() {
            return ForgeMaven;
        }

        public String getMinecraftAssetsURL() {
            return MinecraftAssetsURL;
        }

        public String getMinecraftLibraryURL() {
            return MinecraftLibraryURL;
        }

        public ArrayList<String> getArrayOfMirrors(){
            ArrayList<String> mirrors = new ArrayList<>();
            mirrors.add(this.ForgeMaven);
            mirrors.add(this.MavenCentral);
            mirrors.add(this.MinecraftAssetsURL);
            mirrors.add(this.MinecraftLibraryURL);
            return mirrors;
        }

    }

}
