package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.userdev.DependencyManagementExtension;
import net.minecraftforge.gradle.userdev.dependency.DefaultDependencyFilter;
import net.minecraftforge.gradle.userdev.dependency.DependencyFilter;
import net.minecraftforge.gradle.userdev.manifest.DefaultInheritManifest;
import net.minecraftforge.gradle.userdev.manifest.InheritManifest;
import net.minecraftforge.jarjar.metadata.*;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import javax.swing.text.html.Option;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public abstract class JarJar extends Jar
{
    private           List<Configuration> configurations;
    private transient DependencyFilter    dependencyFilter;

    private boolean minimizeJar;

    private FileCollection sourceSetsClassesDirs;

    private final ConfigurableFileCollection includedDependencies = getProject().files(new Callable<FileCollection>() {

        @Override
        public FileCollection call() throws Exception {
            return dependencyFilter.resolve(configurations);
        }
    });

    private final ConfigurableFileCollection metadata = getProject().files((Callable<FileCollection>) () -> {
        writeMetadata();
        return getProject().files(getJarJarMetadataPath().toFile());
    });

    private final CopySpec jarJarCopySpec;

    public JarJar() {
        super();
        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE); //As opposed to shadow, we do not filter out our entries early!, So we need to handle them accordingly.
        dependencyFilter = new DefaultDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        configurations = new ArrayList<>();

        this.getInputs().property("minimize", (Callable<Boolean>) () -> minimizeJar);
        this.jarJarCopySpec = this.getMainSpec().addChild();
        this.jarJarCopySpec.into("META-INF/jarjar");
    }

    public JarJar minimize() {
        minimizeJar = true;
        return this;
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    FileCollection getSourceSetsClassesDirs() {
        if (sourceSetsClassesDirs == null) {
            ConfigurableFileCollection allClassesDirs = getProject().getObjects().fileCollection();
            if (minimizeJar) {
                for (SourceSet sourceSet : getProject().getExtensions().getByType(SourceSetContainer.class)) {
                    FileCollection classesDirs = sourceSet.getOutput().getClassesDirs();
                    allClassesDirs.from(classesDirs);
                }
            }
            sourceSetsClassesDirs = allClassesDirs.filter(File::isDirectory);
        }
        return sourceSetsClassesDirs;
    }

    @Override
    public InheritManifest getManifest()
    {
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

    @Classpath
    public FileCollection getMetadata() {
        return metadata;
    }

    public JarJar dependencies(Action<DependencyFilter> c) {
        c.execute(dependencyFilter);
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

    public void configuration(final Configuration configuration)
    {
        this.configurations.add(configuration);
    }

    private void writeMetadata() {
        final Path metadataPath = getJarJarMetadataPath();

        try
        {
            metadataPath.toFile().getParentFile().mkdirs();
            Files.deleteIfExists(metadataPath);
            Files.write(metadataPath, MetadataIOHandler.toLines(createMetadata()), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (IOException e)
        {
            throw new RuntimeException("Failed to write JarJar dependency metadata to disk.", e);
        }
    }

    private Path getJarJarMetadataPath()
    {
        return getProject().getBuildDir().toPath().resolve("jarjar").resolve(getName()).resolve("metadata.json");
    }

    private Metadata createMetadata() {
        return new Metadata(
          this.configurations.stream().flatMap(config -> config.getDependencies().stream())
            .filter(ExternalModuleDependency.class::isInstance)
            .map(ExternalModuleDependency.class::cast)
            .map(this::createDependencyMetadata)
            .collect(Collectors.toList())
        );
    }

    private ContainedJarMetadata createDependencyMetadata(final ExternalModuleDependency dependency) {
        if (!isValidVersionRange(Objects.requireNonNull(getVersionFrom(dependency)))) {
            throw new RuntimeException("The given version specification is invalid: " + getVersionFrom(dependency) + " if you used gradle based range versioning like (2.+), convert this to a maven compatible format ([2.0,3.0)).");
        }

        final ResolvedDependency resolvedDependency = getResolvedDependency(dependency);
        try
        {
            return new ContainedJarMetadata(
              new ContainedJarIdentifier(dependency.getGroup(), dependency.getName()),
              new ContainedVersion(
                VersionRange.createFromVersionSpec(getVersionFrom(dependency)),
                new DefaultArtifactVersion(getVersionFrom(resolvedDependency.getModuleVersion()))
              ),
              "META-INF/jarjar/" + resolvedDependency.getAllModuleArtifacts().iterator().next().getFile().getName(),
              isObfuscated(dependency)
            );
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new RuntimeException("The given version specification is invalid: " + dependency.getVersion() + " if you used gradle based range versioning like (2.+), convert this to a maven compatible format ([2.0,3.0)).", e);
        }
    }

    private String getVersionFrom(final Dependency dependency) {
        final Optional<String> attributeVersion = getProject().getExtensions().getByType(DependencyManagementExtension.class).getPinnedJarJarVersion(dependency);

        return attributeVersion.map(this::getVersionFrom).orElseGet(() -> getVersionFrom(Objects.requireNonNull(dependency.getVersion())));
    }

    private String getVersionFrom(final String version)
    {
        if (version.contains("_mapped_")) {
            return version.split("_mapped_")[0];
        }

        return version;
    }

    private ResolvedDependency getResolvedDependency(final ExternalModuleDependency dependency) {
        ExternalModuleDependency toResolve = dependency.copy();
        toResolve.version(constraint -> constraint.strictly(getVersionFrom(dependency)));

        final Set<ResolvedDependency> deps = getProject().getConfigurations().detachedConfiguration(toResolve).getResolvedConfiguration().getLenientConfiguration().getFirstLevelModuleDependencies();
        if (deps.isEmpty() && Objects.requireNonNull(dependency.getVersion()).contains("+"))
            throw new IllegalArgumentException(String.format("Failed to resolve: %s", dependency));

        return deps.iterator().next();
    }

    private boolean isObfuscated(final Dependency dependency) {
        return Objects.requireNonNull(dependency.getVersion()).contains("_mapped_");
    }

    private boolean isValidVersionRange(final String range) {
        try
        {
            final VersionRange data = VersionRange.createFromVersionSpec(range);
            return data.hasRestrictions() && data.getRecommendedVersion() == null && range.contains("+");
        }
        catch (InvalidVersionSpecificationException e)
        {
            return false;
        }
    }
}
