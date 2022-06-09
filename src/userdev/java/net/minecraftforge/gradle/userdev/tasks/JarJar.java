package net.minecraftforge.gradle.userdev.tasks;

import net.minecraftforge.gradle.userdev.dependency.DefaultDependencyFilter;
import net.minecraftforge.gradle.userdev.dependency.DependencyFilter;
import net.minecraftforge.gradle.userdev.manifest.DefaultInheritManifest;
import net.minecraftforge.gradle.userdev.manifest.InheritManifest;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.bundling.Jar;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

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

    public JarJar() {
        super();
        setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE); //As opposed to shadow, we do not filter out our entries early!, So we need to handle them accordingly.
        dependencyFilter = new DefaultDependencyFilter(getProject());
        setManifest(new DefaultInheritManifest(getServices().get(FileResolver.class)));
        configurations = new ArrayList<>();

        this.getInputs().property("minimize", (Callable<Boolean>) () -> minimizeJar);
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
        from(getIncludedDependencies(), copySpec -> copySpec.into("META-INF/jarjar"));
        super.copy();
    }

    @Classpath
    public FileCollection getIncludedDependencies() {
        return includedDependencies;
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
}
