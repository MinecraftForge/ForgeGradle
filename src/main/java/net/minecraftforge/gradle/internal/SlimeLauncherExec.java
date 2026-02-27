/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import com.google.gson.reflect.TypeToken;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Project;
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
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@DisableCachingByDefault(because = "Running the game cannot be cached")
abstract class SlimeLauncherExec extends JavaExec implements ForgeGradleTask, HasPublicType, SlimeLauncherRunTask {
    static TaskProvider<SlimeLauncherExec> register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, MinecraftDependencyInternal mcdep) {
        var minecraft = ((MinecraftExtensionInternal.ForProject)project.getExtensions().getByType(MinecraftExtensionForProject.class));
        var metadata = mcdep.getMetadataTask();
        var single = minecraft.getDependencies().size() > 1;

        var taskNameSuffix = (single ? "" : "For" + Util.dependencyToCamelCase(mcdep.getModule()));
        var runTaskName = sourceSet.getTaskName("run", options.getName()) + taskNameSuffix;
        var genEclipse = SlimeLauncherEclipseConfiguration.register(project, sourceSet, options, mcdep, runTaskName);

        return project.getTasks().register(runTaskName, SlimeLauncherExec.class, task -> {
            task.getRunName().set(options.getName());
            task.getSourceSetName().set(sourceSet.getName());
            task.setDescription("Runs the '%s' Slime Launcher run configuration.".formatted(options.getName()));

            var inst = mcdep.getMavenizerInstance();
            var runtimeClasspath = task.getObjectFactory().fileCollection().from(task.getProviderFactory().provider(sourceSet::getRuntimeClasspath));
            task.classpath(runtimeClasspath);
            task.getRuntimeClasspath().setFrom(runtimeClasspath); // main classpath gets polluted by Slimelauncher so keep a copy
            task.getMinecraftClasspath().setFrom(mcdep.getMinecraftDependencies());
            task.getMappingChannel().set(inst.getMappingChannel());
            task.getMappingVersion().set(inst.getMappingVersion());
            // We need a way to reverse this file, cuz we want srg->mcp and this is mcp->srg
            //task.getSrgToMcp().set(project.file(inst.getToSrgFile()));

            task.getCacheDir().set(task.getObjectFactory().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s".formatted(mcdep.getPath())).map(task.problems.ensureFileLocation())));
            task.getLocalCacheDir().set(task.getObjectFactory().directoryProperty().value(task.localCaches().dir("slime-launcher/cache/%s".formatted(mcdep.getPath())).map(task.problems.ensureFileLocation())));
            task.getMetadata().setFrom(metadata.map(SlimeLauncherMetadata::getMetadata));
            task.getRunsJson().set(metadata.flatMap(SlimeLauncherMetadata::getRunsJson));

            task.getOptions().set(options);
        });
    }

    protected abstract @Input Property<String> getRunName();

    public abstract @Input Property<String> getSourceSetName();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    protected abstract @Internal DirectoryProperty getCacheDir();
    public abstract @Internal @Override DirectoryProperty getLocalCacheDir();
    public abstract @InputFiles @Override ConfigurableFileCollection getMetadata();
    public abstract @InputFiles @Override ConfigurableFileCollection getMinecraftClasspath();
    public abstract @InputFiles @Override ConfigurableFileCollection getRuntimeClasspath();
    public abstract @Input @Override Property<String> getMappingChannel();
    public abstract @Input @Override Property<String> getMappingVersion();
    //protected abstract @InputFile @Override RegularFileProperty getSrgToMcp();

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

        //region Launcher Metadata Inheritance
        Map<String, RunConfig> configs = Map.of();
        var jsons = this.getRunsJson().getAsFile().getOrNull();
        if (jsons != null && jsons.exists())
            configs = JsonData.fromJson(jsons, new TypeToken<>() { });

        var options = ((SlimeLauncherOptionsInternal) this.getOptions().get()).inherit(configs, this.getSourceSetName().get());
        var tokens = SlimeLauncherRunHelper.buildTokens(this);
        var unknown = new HashSet<String>();

        mainClass = options.getMainClass().filter(Util::isPresent);
        if (!this.getMainClass().get().startsWith("net.minecraftforge.launcher")) {
            this.getLogger().warn("WARNING: Main class is not Slime Launcher! Skipping additional configuration.");
        } else {
            var slimeArgs = List.of(
                "--main", mainClass.get(),
                "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
                "--metadata", this.getMetadata().getSingleFile().getAbsolutePath(),
                "--"
            );
            // Set need to add slime args first, so grab a copy and reset
            var args = new ArrayList<>(slimeArgs);
            args.addAll(this.getArgs());
            this.setArgs(args);
        }

        var args = new ArrayList<String>();
        for (var arg : options.getArgs().getOrElse(List.of()))
            args.add(Util.replaceTokens(tokens, arg, unknown));
        this.args(args);

        var jvmArgs = new ArrayList<String>();
        for (var arg : options.getJvmArgs().getOrElse(List.of()))
            jvmArgs.add(Util.replaceTokens(tokens, arg, unknown));
        this.jvmArgs(jvmArgs);

        if (!options.getClasspath().isEmpty())
            this.setClasspath(options.getClasspath());
        if (options.getMinHeapSize().filter(Util::isPresent).isPresent())
            this.setMinHeapSize(options.getMinHeapSize().get());
        if (options.getMaxHeapSize().filter(Util::isPresent).isPresent())
            this.setMinHeapSize(options.getMaxHeapSize().get());

        var system = new HashMap<String, String>();
        for (var entry : options.getSystemProperties().get().entrySet()) {
            var value = Util.replaceTokens(tokens, entry.getValue(), unknown);
            system.put(entry.getKey(), value);
            this.systemProperty(entry.getKey(), value);
        }

        var env = new HashMap<String, String>();
        for (var entry : options.getEnvironment().get().entrySet()) {
            var value = Util.replaceTokens(tokens, entry.getValue(), unknown);
            env.put(entry.getKey(), value);
            this.environment(entry.getKey(), value);
        }

        this.workingDir(options.getWorkingDir().get());
        //endregion

        if (!this.getClient().getOrElse(false))
            this.setStandardInput(System.in);

        for (var token : unknown)
            getLogger().lifecycle("Unknown Run Token: {}", token);

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
            this.getLogger().error("JVM Args: {}", this.getJvmArgs());
            this.getLogger().error("Environment:");
            for (var entry : env.entrySet())
                this.getLogger().error("\t{}: {}", entry.getKey(), entry.getValue());
            this.getLogger().error("System:");
            for (var entry : system.entrySet())
                this.getLogger().error("\t{}: {}", entry.getKey(), entry.getValue());
            this.getLogger().error("Options: {}", options);
            throw e;
        }
    }
}
