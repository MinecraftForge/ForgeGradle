/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;
import org.gradle.work.DisableCachingByDefault;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@DisableCachingByDefault(because = "Running the game cannot be cached")
abstract class SlimeLauncherExec extends JavaExec implements ForgeGradleTask, HasPublicType {
    static TaskProvider<SlimeLauncherExec> register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, ModuleIdentifier module, String version, String asPath, String asString, boolean single) {
        TaskProvider<SlimeLauncherMetadata> metadata;
        {
            TaskProvider<SlimeLauncherMetadata> t;
            var taskName = "slimeLauncherMetadata" + (single ? "" : "for" + Util.dependencyToCamelCase(module));
            try {
                t = project.getTasks().named(taskName, SlimeLauncherMetadata.class);
            } catch (UnknownDomainObjectException e) {
                var metadataConfiguration = project.getConfigurations().detachedConfiguration(
                    project.getDependencyFactory().create(module.getGroup(), module.getName(), version, "metadata", "zip")
                );
                metadataConfiguration.setTransitive(false);
                metadataConfiguration.attributes(a -> a.attribute(Usage.USAGE_ATTRIBUTE, a.named(Usage.class, "metadata")));

                t = project.getTasks().register(taskName, SlimeLauncherMetadata.class, task -> {
                    task.setDescription("Extracts the Slime Launcher metadata%s.".formatted(single ? "" : " for '%s'".formatted(asString)));

                    task.getMetadata().setFrom(metadataConfiguration);
                });
            }

            metadata = t;
        }

        var taskNameSuffix = (single ? "" : "for" + Util.dependencyToCamelCase(module));
        var runTaskName = sourceSet.getTaskName("run", options.getName()) + taskNameSuffix;
        var generateEclipseRunTaskName = sourceSet.getTaskName("genEclipseRun", options.getName()) + taskNameSuffix;

        var genEclipseRun = project.getTasks().register(generateEclipseRunTaskName, SlimeLauncherEclipseConfiguration.class, task -> {
            task.getRunName().set(options.getName());
            task.setDescription("Generates the '%s' Slime Launcher run configuration for Eclipse.".formatted(options.getName()));
            task.getOutputFile().set(task.getProjectLayout().getProjectDirectory().file(runTaskName + ".launch"));

            var runtimeClasspath = task.getObjects().fileCollection().from(
                task.getProviders().provider(() -> {
                    var runtime = sourceSet.getRuntimeClasspath();
                    var eclipseModel = project.getExtensions().findByType(EclipseModel.class);
                    if (eclipseModel == null)
                        return runtime.getFiles();

                    // We need to build a map of sourcesets to real output paths like Eclipse's plugin does.
                    // There is no known exposure of this stuff, so have to do it ourselves.
                    // https://github.com/gradle/gradle/blob/master/platforms/ide/ide/src/main/java/org/gradle/plugins/ide/eclipse/model/internal/SourceFoldersCreator.java#L220
                    var classpath = eclipseModel.getClasspath();
                    var sortedSourceSets = sortSourceSets(classpath.getSourceSets());
                    var replacements = new HashMap<File, File>();
                    var base = classpath.getBaseSourceOutputDir().getAsFile().get();
                    var claimed = new HashSet<File>();
                    claimed.add(classpath.getDefaultOutputDir());

                    // Gather the output name eclipse will use, and all outputs gradle expects
                    for (var sources : sortedSourceSets) {
                        var name =  sources.getName();
                        var path = new File(base, name);
                        while (claimed.contains(path)) {
                            name += '_';
                            path = new File(base, name);
                        }
                        claimed.add(path);
                        if (sources.getOutput().getResourcesDir() != null)
                            replacements.put(sources.getOutput().getResourcesDir(), path);
                        for (var dir : sources.getOutput().getClassesDirs().getFiles())
                            replacements.put(dir, path);
                    }

                    // Now replace the existing classpath with the ones eclipse will use
                    var ret = new LinkedHashSet<File>(runtime.getFiles().size());
                    for (var file : runtime.getFiles())
                        ret.add(replacements.getOrDefault(file, file));
                    return ret;
                }));
            task.getClasspath().from(runtimeClasspath);
            task.getSourceSetName().set(sourceSet.getName());

            task.getCacheDir().set(task.getObjects().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s".formatted(asPath)).map(task.problems.ensureFileLocation())));
            task.getMetadata().setFrom(metadata.map(SlimeLauncherMetadata::getMetadata));
            task.getRunsJson().set(metadata.flatMap(SlimeLauncherMetadata::getRunsJson));

            task.getOptions().set(options);
        });

        project.getTasks().named("genEclipseRuns", task -> task.dependsOn(genEclipseRun));

        return project.getTasks().register(runTaskName, SlimeLauncherExec.class, task -> {
            task.getRunName().set(options.getName());
            task.getSourceSetName().set(sourceSet.getName());
            task.setDescription("Runs the '%s' Slime Launcher run configuration.".formatted(options.getName()));

            task.classpath(task.getObjectFactory().fileCollection().from(task.getProviderFactory().provider(sourceSet::getRuntimeClasspath)));

            var caches = task.getObjectFactory().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s".formatted(asPath)));
            task.getCacheDir().set(caches.map(task.problems.ensureFileLocation()));
            task.getMetadata().setFrom(metadata.map(SlimeLauncherMetadata::getMetadata));
            task.getRunsJson().set(metadata.flatMap(SlimeLauncherMetadata::getRunsJson));

            task.getOptions().set(options);
        });
    }

    protected abstract @Input Property<String> getRunName();

    protected abstract @Input Property<String> getSourceSetName();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    protected abstract @Internal DirectoryProperty getCacheDir();

    protected abstract @InputFiles ConfigurableFileCollection getMetadata();

    protected abstract @InputFile @Optional RegularFileProperty getRunsJson();

    protected abstract @Input @Optional Property<Boolean> getClient();

    protected abstract @Internal MapProperty<String, String> getForkProperties();

    private final ForgeGradleProblems problems = this.getObjectFactory().newInstance(ForgeGradleProblems.class);

    @Inject
    public SlimeLauncherExec() {
        this.setGroup("Slime Launcher");

        var tool = this.getTool(Tools.SLIMELAUNCHER);
        this.setClasspath(tool.getClasspath());
        if (tool.hasMainClass())
            this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(Util.launcherFor(getProject(),tool.getJavaVersion()));
        this.getModularity().getInferModulePath().set(false);
        this.getForkProperties().set(Util.getForkProperties(getProviderFactory()));
    }

    @Override
    public @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(JavaExec.class);
    }

    @Override
    public void exec() {
        Provider<String> mainClass;
        List<String> args;

        //region Launcher Metadata Inheritance
        Map<String, RunConfig> configs = Map.of();
        var jsons = this.getRunsJson().getAsFile().getOrNull();
        if (jsons != null && jsons.exists()) {
            try {
                configs = JsonData.fromJson(
                    this.getRunsJson().getAsFile().get(),
                    new TypeToken<>() { }
                );
            } catch (JsonIOException e) {
                // continue
            }
        }

        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(configs, this.getSourceSetName().get());

        mainClass = options.getMainClass().filter(Util::isPresent);
        args = new ArrayList<>(options.getArgs().getOrElse(List.of()));
        this.jvmArgs(options.getJvmArgs().get());
        if (!options.getClasspath().isEmpty())
            this.setClasspath(options.getClasspath());
        if (options.getMinHeapSize().filter(Util::isPresent).isPresent())
            this.setMinHeapSize(options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util::isPresent).isPresent())
            this.setMinHeapSize(options.getMaxHeapSize().get());
        this.systemProperties(options.getSystemProperties().get());
        this.environment(options.getEnvironment().get());
        this.workingDir(options.getWorkingDir().get());
        //endregion

        if (!this.getMainClass().get().startsWith("net.minecraftforge.launcher")) {
            this.getLogger().warn("WARNING: Main class is not Slime Launcher! Skipping additional configuration.");
        } else {
            this.args("--main", mainClass.get(),
                "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
                "--metadata", this.getMetadata().getSingleFile().getAbsolutePath(),
                "--");
        }

        this.args(args);

        if (!this.getClient().getOrElse(false))
            this.setStandardInput(System.in);

        try {
            Files.createDirectories(this.getWorkingDir().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try {
            super.exec();
        } catch (Exception e) {
            this.getLogger().error("Something went wrong! Here is some debug info.");
            this.getLogger().error("Args: {}", this.getArgs());
            this.getLogger().error("Options: {}", options);
            throw e;
        }
    }

    private static List<SourceSet> sortSourceSets(@Nullable Iterable<SourceSet> sourceSets) {
        if (sourceSets == null)
            return new ArrayList<>(0);
        var ret = new ArrayList<SourceSet>();
        for (var item : sourceSets)
            ret.add(item);
        ret.sort(Comparator.comparing(SlimeLauncherExec::toComparable));
        return ret;
    }

    private static Integer toComparable(SourceSet sourceSet) {
        String name = sourceSet.getName();
        if (SourceSet.MAIN_SOURCE_SET_NAME.equals(name)) {
            return 0;
        } else if (SourceSet.TEST_SOURCE_SET_NAME.equals(name)) {
            return 1;
        } else {
            return 2;
        }
    }
}
