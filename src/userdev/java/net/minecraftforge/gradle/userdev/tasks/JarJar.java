package net.minecraftforge.gradle.userdev.tasks;

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
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
    @Optional
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
            .map(this::createDependencyMetadata)
            .collect(Collectors.toList())
        );
    }

    private ContainedJarMetadata createDependencyMetadata(final Dependency dependency) {
        final ResolvedDependency resolvedDependency = getResolvedDependency(dependency);
        try
        {
            return new ContainedJarMetadata(
              new ContainedJarIdentifier(dependency.getGroup(), dependency.getName()),
              new ContainedVersion(
                VersionRange.createFromVersionSpec(dependency.getVersion()),
                new DefaultArtifactVersion(resolvedDependency.getModuleVersion())
              ),
              "META-INF/jarjar/" + resolvedDependency.getAllModuleArtifacts().iterator().next().getFile().getName(),
              isObfuscated(dependency)
            );
        }
        catch (InvalidVersionSpecificationException e)
        {
            throw new RuntimeException("The given version specification is invalid: " + dependency.getVersion() + " is you used gradle based range versioning like (2.+), convert this to a maven compatible format ([2.0,3.0)).", e);
        }
    }

    private ResolvedDependency getResolvedDependency(final Dependency dependency) {
        final Set<ResolvedDependency> deps = getProject().getConfigurations().detachedConfiguration(dependency).getResolvedConfiguration().getLenientConfiguration().getFirstLevelModuleDependencies();
        if (deps.isEmpty())
            throw new IllegalArgumentException(String.format("Failed to resolve: %s", dependency));

        return deps.iterator().next();
    }

    private boolean isObfuscated(final Dependency dependency) {
        return false;
    }
}
