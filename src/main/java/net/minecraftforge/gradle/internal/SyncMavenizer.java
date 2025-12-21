/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftMappings;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.UnknownDomainObjectException;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.process.ExecResult;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;

@DisableCachingByDefault(because = "Mavenizer uses its own in-house caching")
abstract class SyncMavenizer extends ToolExec {
    static TaskProvider<SyncMavenizer> register(Project project, ExternalModuleDependency dependency, Provider<? extends MinecraftMappings> mappings, Provider<? extends Directory> output) {
        var version = dependency.getVersion();
        var taskName = "syncMavenizerFor"
            + StringGroovyMethods.capitalize(dependency.getName())
            + (version == null ? "" : version.replace(".", "").replace('-', '_'));

        var tasks = project.getTasks();
        try {
            return tasks.named(taskName, SyncMavenizer.class);
        } catch (UnknownDomainObjectException e) {
            return project.getTasks().register(taskName, SyncMavenizer.class, task -> {
                task.getOutput().set(output);
                task.getModule().set(dependency.getModule());
                task.getVersion().set(dependency.getVersion());
                task.getMappings().set(mappings);
            });
        }
    }

    protected abstract @Internal DirectoryProperty getCaches();

    protected abstract @Internal DirectoryProperty getOutput();

    protected abstract @Input Property<ModuleIdentifier> getModule();

    protected abstract @Input Property<String> getVersion();

    protected abstract @Input Property<MinecraftMappings> getMappings();

    protected abstract @Input @Optional ListProperty<String> getRepositories();

    private void addRepositories(Iterable<? extends MavenArtifactRepository> repositories) {
        for (var repository : repositories) {
            if (repository.getName().equals("MinecraftMavenizer"))
                continue;

            String url;
            {
                var s = repository.getUrl().toString();
                if (!s.endsWith("/"))
                    s += "/";
                url = s;
            }

            this.getRepositories().add(this.getProviders().provider(() -> repository.getName() + ',' + url));
        }
    }

    @Inject
    public SyncMavenizer() {
        super(Tools.MAVENIZER);

        this.getCaches().convention(this.defaultToolDir.dir("caches"));

        var minecraft = ((MinecraftExtensionInternal.ForProject) getProject().getExtensions().getByType(MinecraftExtensionForProject.class));
        this.addRepositories(minecraft.getRepositories());
    }

    @Override
    protected ExecResult exec() throws IOException {
        return super.exec().rethrowFailure().assertNormalExitValue();
    }

    @Override
    protected void addArguments() {
        super.addArguments();

        this.args("--maven");
        this.args("--cache", this.getCaches());
        this.args("--output", this.getOutput());
        this.args("--jdk-cache", this.getCaches());
        this.args("--artifact", this.getModule());
        this.args("--version", this.getVersion());
        this.args("--global-auxiliary-variants");

        if ("parchment".equals(this.getMappings().get().getChannel())) {
            this.args("--parchment", this.getMappings().get().getVersion());
        }

        for (var repository : this.getRepositories().getOrElse(List.of())) {
            this.args("--repository", repository);
        }
    }
}
