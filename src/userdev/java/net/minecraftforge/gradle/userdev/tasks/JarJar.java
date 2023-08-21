/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.gradle.userdev.dependency.DefaultDependencyFilter;
import net.minecraftforge.gradle.userdev.dependency.DefaultDependencyVersionInformationHandler;
import net.minecraftforge.gradle.userdev.dependency.DependencyFilter;
import net.minecraftforge.gradle.userdev.dependency.DependencyVersionInformationHandler;
import net.minecraftforge.gradle.userdev.jarjar.JarJarProjectExtension;
import net.minecraftforge.gradle.userdev.manifest.DefaultInheritManifest;
import net.minecraftforge.gradle.userdev.manifest.InheritManifest;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingVersionUtils;
import net.minecraftforge.jarjar.metadata.*;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.Action;
import org.gradle.api.artifacts.*;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public abstract class JarJar extends Jar {
    private final List<Configuration> configurations;
    private transient DependencyFilter dependencyFilter;
    private transient DependencyVersionInformationHandler dependencyVersionInformationHandler;

    private FileCollection sourceSetsClassesDirs;

    private final ConfigurableFileCollection includedDependencies = getProject().files((Callable<FileCollection>) () -> getProject().files(
            getResolvedDependencies().stream().flatMap(d -> d.getAllModuleArtifacts().stream()).map(ResolvedArtifact::getFile).toArray()
    ));

    private final ConfigurableFileCollection metadata = getProject().files((Callable<FileCollection>) () -> {
        writeMetadata();
        return getProject().files(getJarJarMetadataPath().toFile());
    });

    private final CopySpec jarJarCopySpec;

    public JarJar() {
        super();
        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE); //As opposed to shadow, we do not filter out our entries early!, So we need to handle them accordingly.
        dependencyFilter = new DefaultDependencyFilter(getProject());
        dependencyVersionInformationHandler = new DefaultDependencyVersionInformationHandler(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        configurations = new ArrayList<>();

        this.jarJarCopySpec = this.getMainSpec().addChild();
        this.jarJarCopySpec.into("META-INF/jarjar");
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getSourceSetsClassesDirs() {
        if (sourceSetsClassesDirs == null) {
            ConfigurableFileCollection allClassesDirs = getProject().getObjects().fileCollection();
            sourceSetsClassesDirs = allClassesDirs.filter(File::isDirectory);
        }
        return sourceSetsClassesDirs;
    }

    @Override
    public InheritManifest getManifest() {
        return (InheritManifest) super.getManifest();
    }

    @TaskAction
    protected void copy() {
        this.jarJarCopySpec.from(getIncludedDependencies());
        this.jarJarCopySpec.from(getMetadata());
        super.copy();
    }

    @Classpath
    public FileCollection getIncludedDependencies() {
        return includedDependencies;
    }

    @Internal
    public Set<ResolvedDependency> getResolvedDependencies() {
        return this.configurations.stream().flatMap(config -> config.getAllDependencies().stream())
                .filter(ModuleDependency.class::isInstance)
                .map(ModuleDependency.class::cast)
                .map(this::getResolvedDependency)
                .filter(this.dependencyFilter::isIncluded)
                .collect(Collectors.toSet());
    }

    @Classpath
    public FileCollection getMetadata() {
        return metadata;
    }

    public JarJar dependencies(Action<DependencyFilter> c) {
        c.execute(dependencyFilter);
        return this;
    }

    public JarJar versionInformation(Action<DependencyVersionInformationHandler> c) {
        c.execute(dependencyVersionInformationHandler);
        return this;
    }

    @Classpath
    @org.gradle.api.tasks.Optional
    public List<Configuration> getConfigurations() {
        return this.configurations;
    }

    public void setConfigurations(List<Configuration> configurations) {
        this.configurations.clear();
        this.configurations.addAll(configurations);
    }

    @Internal
    public DependencyFilter getDependencyFilter() {
        return this.dependencyFilter;
    }

    public void setDependencyFilter(DependencyFilter filter) {
        this.dependencyFilter = filter;
    }

    public void configuration(@Nullable final Configuration configuration) {
        if (configuration == null) {
            return;
        }

        this.configurations.add(configuration);
    }

    public void fromRuntimeConfiguration() {
        final Configuration runtimeConfiguration = getProject().getConfigurations().findByName("runtimeClasspath");
        if (runtimeConfiguration != null) {
            this.configuration(runtimeConfiguration);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void writeMetadata() {
        final Path metadataPath = getJarJarMetadataPath();

        try {
            metadataPath.toFile().getParentFile().mkdirs();
            Files.deleteIfExists(metadataPath);
            Files.write(metadataPath, MetadataIOHandler.toLines(createMetadata()), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write JarJar dependency metadata to disk.", e);
        }
    }

    private Path getJarJarMetadataPath() {
        return getProject().getLayout().getBuildDirectory().getAsFile().get().toPath().resolve("jarjar").resolve(getName()).resolve("metadata.json");
    }

    private Metadata createMetadata() {
        return new Metadata(
                this.configurations.stream().flatMap(config -> config.getAllDependencies().stream())
                        .filter(ModuleDependency.class::isInstance)
                        .map(ModuleDependency.class::cast)
                        .map(this::createDependencyMetadata)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList())
        );
    }

    private Optional<ContainedJarMetadata> createDependencyMetadata(final ModuleDependency dependency) {
        if (!dependencyFilter.isIncluded(dependency)) {
            return Optional.empty();
        }

        if (!isValidVersionRange(Objects.requireNonNull(getVersionRangeFrom(dependency)))) {
            throw createInvalidVersionRangeException(dependency, null);
        }

        final ResolvedDependency resolvedDependency = getResolvedDependency(dependency);
        if (!dependencyFilter.isIncluded(resolvedDependency)) {
            //Skipping this file since the dependency filter does not want this to be included at all!
            return Optional.empty();
        }

        try {
            return Optional.of(new ContainedJarMetadata(
                    new ContainedJarIdentifier(dependency.getGroup(), dependency.getName()),
                    new ContainedVersion(
                            VersionRange.createFromVersionSpec(getVersionRangeFrom(dependency)),
                            new DefaultArtifactVersion(DeobfuscatingVersionUtils.adaptDeobfuscatedVersion(resolvedDependency.getModuleVersion()))
                    ),
                    "META-INF/jarjar/" + resolvedDependency.getAllModuleArtifacts().iterator().next().getFile().getName(),
                    isObfuscated(dependency)
            ));
        } catch (InvalidVersionSpecificationException e) {
            throw createInvalidVersionRangeException(dependency, e);
        }
    }

    private RuntimeException createInvalidVersionRangeException(final ModuleDependency dependency, final Throwable cause) {
        return new RuntimeException("The given version specification is invalid: " + getVersionRangeFrom(dependency)
                + ". If you used gradle based range versioning like 2.+, convert this to a maven compatible format: [2.0,3.0).", cause);
    }

    private String getVersionRangeFrom(final ModuleDependency dependency) {
        final Optional<String> versionRange = dependencyVersionInformationHandler.getVersionRange(dependency)
                .map(DeobfuscatingVersionUtils::adaptDeobfuscatedVersionRange);
        if (versionRange.isPresent()) {
            return versionRange.get();
        }
        final Optional<String> attributeVersion = getProject().getExtensions().getByType(JarJarProjectExtension.class).getRange(dependency);

        return attributeVersion.map(DeobfuscatingVersionUtils::adaptDeobfuscatedVersionRange).orElseGet(() -> DeobfuscatingVersionUtils.adaptDeobfuscatedVersion(Objects.requireNonNull(dependency.getVersion())));
    }

    private String getVersionFrom(final ModuleDependency dependency) {
        final Optional<String> version = dependencyVersionInformationHandler.getVersion(dependency)
                .map(DeobfuscatingVersionUtils::adaptDeobfuscatedVersion);
        if (version.isPresent()) {
            return version.get();
        }
        final Optional<String> attributeVersion = getProject().getExtensions().getByType(JarJarProjectExtension.class).getPin(dependency);

        return attributeVersion.map(DeobfuscatingVersionUtils::adaptDeobfuscatedVersion).orElseGet(() -> DeobfuscatingVersionUtils.adaptDeobfuscatedVersion(Objects.requireNonNull(dependency.getVersion())));
    }

    private ResolvedDependency getResolvedDependency(final ModuleDependency dependency) {
        ModuleDependency toResolve = dependency.copy();
        if (toResolve instanceof ExternalModuleDependency) {
            final ExternalModuleDependency externalDependency = (ExternalModuleDependency) toResolve;
            externalDependency.version(constraint -> constraint.strictly(getVersionFrom(dependency)));
        }

        final Set<ResolvedDependency> deps = getProject().getConfigurations().detachedConfiguration(toResolve).getResolvedConfiguration().getFirstLevelModuleDependencies();
        if (deps.isEmpty()) {
            throw new IllegalArgumentException(String.format("Failed to resolve: %s", toResolve));
        }

        return deps.iterator().next();
    }

    private boolean isObfuscated(final Dependency dependency) {
        if (dependency instanceof ProjectDependency) {
            final ProjectDependency projectDependency = (ProjectDependency) dependency;
            return projectDependency.getDependencyProject().getPlugins().hasPlugin(UserDevPlugin.class);
        }

        return Objects.requireNonNull(dependency.getVersion()).contains("_mapped_");
    }

    private boolean isValidVersionRange(final String range) {
        try {
            final VersionRange data = VersionRange.createFromVersionSpec(range);
            return data.hasRestrictions() && data.getRecommendedVersion() == null && !range.contains("+");
        } catch (InvalidVersionSpecificationException e) {
            return false;
        }
    }
}
