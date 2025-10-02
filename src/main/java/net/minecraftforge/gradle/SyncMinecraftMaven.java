/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This task executes the Minecraft Mavenizer, Forge's standalone tool for generating a local Maven repository for
 * Minecraft artifacts.
 *
 * @see MinecraftExtensionImpl
 */
abstract class SyncMinecraftMaven extends DefaultTask implements ForgeGradleTask, HasPublicType {
    /** The name of the task that is used to sync the Minecraft Maven. */
    static final String NAME = "syncMinecraftMaven";

    static TaskProvider<SyncMinecraftMaven> register(Project project, MinecraftExtensionImpl.ForProjectImpl<?> minecraft) {
        return Util.runFirst(project, project.getTasks().register(NAME, SyncMinecraftMaven.class, task -> {
                task.getRequests().addAll(Request.collect(minecraft.minecraftDependencies));
                task.getOutput().set(minecraft.output);
            })
        );
    }

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    protected abstract @Inject ObjectFactory getObjects();
    protected abstract @Inject ExecOperations getExecOperations();

    @Inject
    public SyncMinecraftMaven() {
        this.setGroup("Build Setup");
        this.setDescription("Syncs the Minecraft dependencies using Minecraft Mavenizer.");

        // JavaExec
        var tool = this.getTool(Tools.MAVENIZER);
        this.getExecutable().convention(tool.getClasspath());
        this.getJavaLauncher().convention(tool.getJavaLauncher());
        this.getMainClass().convention(tool.getMainClass());

        // Minecraft Maven
        var defaultDirectory = this.getObjects().directoryProperty().value(this.globalCaches().dir("mavenizer").map(this.problems.ensureFileLocation()));
        this.getCaches().convention(defaultDirectory.dir("cache").map(this.problems.ensureFileLocation()));
        this.getOutput().convention(defaultDirectory.dir("output").map(this.problems.ensureFileLocation()));

        this.onlyIf(
            "Minecraft Mavenizer will not run if no Minecraft dependencies are present.",
            task -> {
                var requests = ((SyncMinecraftMaven) task).getRequests();
                return requests.isPresent() && !requests.get().isEmpty();
            }
        );
    }

    @Override
    public @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(DefaultTask.class);
    }

    @TaskAction
    public void exec() {
        // TODO [ForgeGradle][MCMaven] Better logging for each request
        this.getRequests().get().forEach(this::exec);
    }

    private void exec(Request request) {
        this.getExecOperations().javaexec(spec -> {
            spec.setClasspath(this.getExecutable());
            spec.setExecutable(this.getJavaLauncher().get().getExecutablePath());
            spec.getMainClass().set(this.getMainClass());

            spec.setArgs(this.argsFor(request));
        }).rethrowFailure();
    }

    private List<String> argsFor(Request request) {
        var args = new ArrayList<>(List.of(
            "--maven",
            "--cache", this.getCaches().get().getAsFile().getAbsolutePath(),
            "--output", this.getOutput().get().getAsFile().getAbsolutePath(),
            "--jdk-cache", this.getCaches().dir("jdks").get().getAsFile().getAbsolutePath(),
            "--artifact", request.module,
            "--version", request.version,
            "--global-auxiliary-variants"
        ));
        if ("parchment".equals(request.mappings.channel())) {
            args.add("--parchment");
            args.add(request.mappings.version());
        }
        return args;
    }

    // JavaExec
    protected abstract @Classpath ConfigurableFileCollection getExecutable();
    protected abstract @Nested Property<JavaLauncher> getJavaLauncher();
    protected abstract @Input Property<String> getMainClass();

    // Minecraft Mavenizer
    protected abstract @InputDirectory DirectoryProperty getCaches();
    protected abstract @InputDirectory DirectoryProperty getOutput();
    protected abstract @Input @Optional SetProperty<Request> getRequests();

    public record Request(String module, String version, MinecraftMappings mappings) implements Serializable {
        public Request(MinecraftDependency minecraft, ExternalModuleDependency module) {
            this(
                "%s:%s".formatted(module.getGroup(), module.getName()),
                Objects.requireNonNull(module.getVersion(), "Minecraft artifact must have a version"),
                Objects.requireNonNull(minecraft.getMappings(), "Minecraft dependencies are not finished")
            );
        }

        private static Set<Request> collect(Collection<? extends MinecraftDependencyInternal> requests) {
            return requests.stream().map(d -> new Request(d, d.getDelegate().get())).collect(Collectors.toSet());
        }
    }
}
