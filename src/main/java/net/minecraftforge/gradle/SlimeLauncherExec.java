/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedTask;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

abstract class SlimeLauncherExec extends ToolExec implements EnhancedTask, HasPublicType {
    static void register(Project project, SourceSet sourceSet, SlimeLauncherOptionsImpl options, Map<String, RunConfig> configs, Dependency dependency, Provider<RegularFile> metadataZip, boolean single) {
        var taskName = sourceSet.getTaskName("run", options.getName());
        project.getTasks().register(single ? taskName : taskName + Util.dependencyToCamelCase(dependency), SlimeLauncherExec.class, task -> {
            task.setDescription("Runs the '%s' Slime Launcher run configuration.".formatted(options.getName()));

            task.classpath(task.getObjectFactory().fileCollection().from(task.getProviderFactory().provider(sourceSet::getRuntimeClasspath)));
            task.getJavaLauncher().unset();

            var caches = task.getObjectFactory().directoryProperty().value(task.globalCaches().dir("slime-launcher/cache/%s/%s/%s".formatted(dependency.getGroup().replace(".", "/"), dependency.getName(), dependency.getVersion())));
            task.getCacheDir().set(caches.map(task.getProblems().ensureFileLocation()));
            task.getMetadataZip().set(metadataZip);

            task.inherit(configs, options.getName());
            options.apply(task);

            if (task.buildAllProjects)
                task.dependsOn(task.getProject().getAllprojects().stream().map(it -> it.getTasks().named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME)).toArray());
        });
    }

    private boolean buildAllProjects;

    @Inject
    public SlimeLauncherExec() {
        super(Tools.SLIMELAUNCHER);

        this.setGroup("Slime Launcher");

        this.getModularity().getInferModulePath().set(false);
    }

    @Override
    public @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(JavaExec.class);
    }

    private void inherit(Map<String, RunConfig> configs, String name) {
        var config = configs.get(name);
        if (config == null) return;

        if (config.parents != null && !config.parents.isEmpty())
            config.parents.forEach(parent -> this.inherit(configs, parent));

        if (config.main != null)
            this.getBootstrapMainClass().set(config.main);

        if (config.args != null && !config.args.isEmpty())
            this.getMcBootstrapArgs().set(List.copyOf(config.args));

        if (config.jvmArgs != null && !config.jvmArgs.isEmpty())
            this.jvmArgs(config.jvmArgs);

        this.getClient().set(config.client);

        this.buildAllProjects = config.buildAllProjects;

        if (config.env != null && !config.env.isEmpty())
            this.environment(config.env);

        if (config.props != null && !config.props.isEmpty())
            this.systemProperties(config.props);
    }

    @Override
    public void exec() {
        if (!this.getMainClass().get().startsWith("net.minecraftforge.launcher")) {
            this.getLogger().lifecycle("Main class is not Slime Launcher! Skipping additional configuration.");
        } else {
            this.args("--main", this.getBootstrapMainClass().get(),
                "--cache", this.getCacheDir().get().getAsFile().getAbsolutePath(),
                "--metadata", this.getMetadataZip().get().getAsFile().getAbsolutePath(),
                "--");

            this.args(this.getMcBootstrapArgs().getOrElse(List.of()).toArray());
        }

        if (!this.getClient().getOrElse(false))
            this.setStandardInput(System.in);

        try {
            Files.createDirectories(this.getWorkingDir().toPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        super.exec();
    }

    protected abstract @InputDirectory DirectoryProperty getCacheDir();

    protected abstract @InputFile RegularFileProperty getMetadataZip();

    protected abstract @Input Property<String> getBootstrapMainClass();

    protected abstract @Input @Optional ListProperty<String> getMcBootstrapArgs();

    protected abstract @Input @Optional Property<Boolean> getClient();
}
