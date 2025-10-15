/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import com.google.gson.JsonIOException;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.util.data.json.JsonData;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

abstract class SlimeLauncherExec extends JavaExec implements ForgeGradleTask, HasPublicType {
    static void register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, Dependency dependency, boolean single) {
        TaskProvider<SlimeLauncherMetadata> metadata;
        {
            TaskProvider<SlimeLauncherMetadata> t;
            var taskName = "slimeLauncherMetadata" + (single ? "" : "for" + Util.dependencyToCamelCase(dependency));
            try {
                t = project.getTasks().named(taskName, SlimeLauncherMetadata.class);
            } catch (UnknownDomainObjectException e) {
                var metadataZip = project.getObjects().fileProperty().fileProvider(project.getProviders().provider(() ->
                    project.getConfigurations().detachedConfiguration(
                        project.getDependencyFactory().create(dependency.getGroup(), dependency.getName(), dependency.getVersion(), "metadata", "zip")
                    ).getSingleFile()
                ));

                t = project.getTasks().register(taskName, SlimeLauncherMetadata.class, task -> {
                    task.setDescription("Extracts the Slime Launcher metadata%s.".formatted(single ? "" : " for '%s'".formatted(Util.toString(dependency))));

                    task.getMetadataZip().set(metadataZip);
                });
            }

            metadata = t;
        }

        var taskName = sourceSet.getTaskName("run", options.getName()) + (single ? "" : "for" + Util.dependencyToCamelCase(dependency));
        project.getTasks().register(taskName, SlimeLauncherExec.class, task -> {
            task.getRunName().set(options.getName());
            task.setDescription("Runs the '%s' Slime Launcher run configuration.".formatted(options.getName()));

            task.classpath(task.getObjectFactory().fileCollection().from(task.getProviderFactory().provider(sourceSet::getRuntimeClasspath)));
            task.getJavaLauncher().unset();

            var caches = task.getObjectFactory().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s".formatted(Util.pathify(dependency))));
            task.getCacheDir().set(caches.map(task.problems.ensureFileLocation()));
            task.getRunsJson().set(metadata.flatMap(SlimeLauncherMetadata::getRunsJson));

            task.getOptions().set(options);
        });
    }

    protected abstract @Input Property<String> getRunName();

    protected abstract @Nested Property<SlimeLauncherOptions> getOptions();

    protected abstract @Internal DirectoryProperty getCacheDir();

    protected abstract @InputFile RegularFileProperty getRunsJson();

    protected abstract @Input Property<String> getDelegateMainClass();

    protected abstract @Input @Optional ListProperty<String> getDelegateArgs();

    protected abstract @Input @Optional Property<Boolean> getClient();

    private final ForgeGradleProblems problems = this.getObjectFactory().newInstance(ForgeGradleProblems.class);

    @Inject
    public SlimeLauncherExec() {
        this.setGroup("Slime Launcher");

        var tool = this.getTool(Tools.SLIMELAUNCHER);

        this.setClasspath(tool.getClasspath());

        if (tool.hasMainClass())
            this.getMainClass().set(tool.getMainClass());
        this.getJavaLauncher().set(tool.getJavaLauncher());
        this.getModularity().getInferModulePath().set(false);
    }

    @Override
    public @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(JavaExec.class);
    }

    @Override
    public void exec() {
        Provider<String> mainClass;
        Provider<List<String>> args;

        //region Launcher Metadata Inheritance
        Map<String, RunConfig> configs = Map.of();
        try {
            configs = JsonData.fromJson(
                this.getRunsJson().getAsFile().get(),
                new TypeToken<>() { }
            );
        } catch (JsonIOException e) {
            // continue
        }

        var options = (SlimeLauncherOptionsImpl) this.getOptions().get();
        options.inherit(configs);

        mainClass = this.getDelegateMainClass().orElse(options.getMainClass().filter(Util::isPresent));
        args = this.getDelegateArgs().orElse(options.getArgs());
        this.jvmArgs(options.getJvmArgs());
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
                "--metadata", this.getRunsJson().get().getAsFile().getAbsolutePath(),
                "--");
        }

        this.args(args.get().toArray());

        if (!this.getClient().getOrElse(false))
            this.setStandardInput(System.in);

        try {
            Files.createDirectories(this.getWorkingDir().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.getLogger().info("{} {}", this.getClasspath().getAsPath(), String.join(" ", this.getArgs()));
        super.exec();
    }
}
