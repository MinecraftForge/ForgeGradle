/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovy.lang.GroovyObjectSupport;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;
import groovy.util.Node;
import groovy.util.NodeList;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingRepo;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingVersionUtils;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import net.minecraftforge.gradle.userdev.util.MavenPomUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ExternalModuleDependencyBundle;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DependencyManagementExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "fg";
    private final Project project;
    private final DependencyRemapper remapper;
    private final DeobfuscatingRepo deobfuscatingRepo;
    private final ArtifactRepository repository;

    public DependencyManagementExtension(Project project, DependencyRemapper remapper, DeobfuscatingRepo deobfuscatingRepo) {
        this.project = project;
        this.remapper = remapper;
        this.deobfuscatingRepo = deobfuscatingRepo;
        this.repository = new BaseRepo.Builder()
                .add(deobfuscatingRepo)
                .attach(project, "bundled_deobf_repo");

        project.afterEvaluate(this::afterEvaluate);
    }

    private void afterEvaluate(Project project) {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        for (SourceSet sourceSet : java.getSourceSets()) {
            if (!containsForge(project, sourceSet)
                || !isDeclaringMixinUsage(project, sourceSet))
                continue;

            for (Configuration configuration : getConfigurationsToAddME(project, sourceSet)) {
                configuration.withDependencies(dependencies -> dependencies.add(project.getDependencies().create("io.github.llamalad7:mixinextras-common:0.5.0")));
            }
        }
    }

    private static boolean containsForge(Project project, SourceSet sourceSet) {
        Configuration configuration = project.getConfigurations().findByName(sourceSet.getCompileClasspathConfigurationName());
        if (configuration == null) return false;

        return !configuration.getAllDependencies().matching(
            dependency -> "net.minecraftforge".equals(dependency.getGroup()) && "forge".equals(dependency.getName())
        ).isEmpty();
    }

    // NOTE: Returns null if Jar task doesn't have "MixinConfigs" or "MixinConnector"
    private static boolean isDeclaringMixinUsage(Project project, SourceSet sourceSet) {
        Task task = project.getTasks().findByName(sourceSet.getJarTaskName());
        if (!(task instanceof Jar))
            return false;

        Jar jar = (Jar) task;
        Attributes attributes = jar.getManifest().getAttributes();
        if (!(attributes.containsKey("MixinConfigs") || attributes.containsKey("MixinConnector")))
            return false;

        return true;
    }

    private static Configuration[] getConfigurationsToAddME(Project project, SourceSet sourceSet) {
        ConfigurationContainer configurations = project.getConfigurations();
        boolean addToCompileOnly = alreadyContainsMixinExtras(configurations.findByName(sourceSet.getCompileClasspathConfigurationName()));
        boolean addToAnnotationProcessor = alreadyContainsMixinExtras(configurations.findByName(sourceSet.getAnnotationProcessorConfigurationName()));

        if (addToCompileOnly && addToAnnotationProcessor)
            return new Configuration[] {configurations.getByName(sourceSet.getCompileOnlyConfigurationName()), configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName())};
        else if (addToCompileOnly)
            return new Configuration[] {configurations.getByName(sourceSet.getCompileOnlyConfigurationName())};
        else if (addToAnnotationProcessor)
            return new Configuration[] {configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName())};
        else
            return new Configuration[] { };
    }

    private static boolean alreadyContainsMixinExtras(@Nullable Configuration configuration) {
        return configuration != null && !configuration.getAllDependencies().matching(
            dependency -> "io.github.llamalad7".equals(dependency.getGroup()) && dependency.getName().startsWith("mixinextras")
        ).isEmpty();
    }

    public DeobfuscatingRepo getDeobfuscatingRepo() {
        return deobfuscatingRepo;
    }

    public ArtifactRepository getRepository() {
        return repository;
    }

    @SuppressWarnings("unused")
    public Dependency deobf(Object dependency) {
        //noinspection DataFlowIssue -- null closure is allowed here
        return deobf(dependency, null);
    }

    public Dependency deobf(
        Object dependency,
        @DelegatesTo(Dependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.Dependency")
        Closure<?> configure
    ) {
        Dependency baseDependency = project.getDependencies().create(dependency, configure);
        project.getDependencies().add(UserDevPlugin.OBF, baseDependency);

        return remapper.remap(baseDependency);
    }

    @SuppressWarnings("unused")
    public <T> Provider<?> deobf(Provider<T> dependency) {
        //noinspection DataFlowIssue -- null closure is allowed here
        return deobf(dependency, null);
    }

    public <T> Provider<?> deobf(
        Provider<T> dependency,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency")
        Closure<?> configure
    ) {
        if (dependency.isPresent() && dependency.get() instanceof ExternalModuleDependencyBundle) {
            project.getDependencies().addProvider(UserDevPlugin.OBF, dependency, baseDependency -> {
                //noinspection ConstantValue -- null closure is allowed here
                if (configure != null)
                    configure.call(baseDependency);
            });

            // this provider MUST return ExternalModuleDependencyBundle
            // The only way to coerce the type of it is to use a property, since we can set the type manually on creation.
            // ProviderInternal#getType uses the generic argument to determine what type it is.
            // Provider#map and #flatMap do NOT preserve the resultant type, which fucks with adding bundles to configurations.
            return project.getObjects().property(ExternalModuleDependencyBundle.class).value(project.provider(() -> {
                ExternalModuleDependencyBundle newBundle = new RemappedExternalModuleDependencyBundle();
                for (MinimalExternalModuleDependency d : (ExternalModuleDependencyBundle) dependency.get()) {
                    //noinspection ConstantValue -- null closure is allowed here
                    if (configure != null)
                        configure.call(d);

                    newBundle.add((MinimalExternalModuleDependency) remapper.remap(d));
                }
                return newBundle;
            }));
        } else {
            // Rationale: single dependencies may have additional data that must be calculated at configuration time
            // The most obvious example being usage of DependencyHandler#variantOf.
            // If the dependency isn't created immediately, that information is lost when copying the base dependency (i don't know why)
            // It's a rather negligible performance hit and FG6 was already doing this anyway, so it's not a big deal.
            // Still going to return a Provider<?> though, since we also handle bundles in this method
            Property<Dependency> provider = project.getObjects().property(Dependency.class).value(dependency.map(d -> this.deobf(d, configure)));
            provider.finalizeValue();
            return provider;
        }
    }

    @SuppressWarnings("unused")
    public <T> Provider<?> deobf(ProviderConvertible<T> dependency) {
        //noinspection DataFlowIssue -- null closure is allowed here
        return deobf(dependency, null);
    }

    public <T> Provider<?> deobf(
        ProviderConvertible<T> dependency,
        @DelegatesTo(ExternalModuleDependency.class)
        @ClosureParams(value = SimpleType.class, options = "org.gradle.api.artifacts.ExternalModuleDependency")
        Closure<?> configure
    ) {
        return deobf(dependency.asProvider(), configure);
    }

    private static class RemappedExternalModuleDependencyBundle extends ArrayList<MinimalExternalModuleDependency> implements ExternalModuleDependencyBundle { }

    @SuppressWarnings({"ConstantConditions", "unchecked"})
    public MavenPublication component(MavenPublication mavenPublication) {
        project.getTasks().withType(GenerateModuleMetadata.class).forEach(generateModuleMetadata -> generateModuleMetadata.setEnabled(false));

        mavenPublication.suppressAllPomMetadataWarnings(); //We have weird handling of stuff and things when it comes to versions and other features. No need to spam the log when that happens.

        mavenPublication.pom(pom -> {
            pom.withXml(xml -> {
                final NodeList dependencies = MavenPomUtils.getDependenciesNodeList(xml);

                final List<Node> dependenciesNodeList = (List<Node>) dependencies.stream()
                        .filter(Node.class::isInstance)
                        .map(Node.class::cast)
                        .collect(Collectors.toList());

                dependenciesNodeList.stream()
                        .filter(el -> MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "artifactId", "forge", "fmlonly")
                                && MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "groupId", "net.minecraftforge"))
                        .forEach(el -> el.parent().remove(el));

                dependenciesNodeList.stream()
                        .filter(el -> MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "artifactId", "client", "server", "joined")
                                && MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "groupId", "net.minecraft"))
                        .forEach(el -> el.parent().remove(el));

                dependenciesNodeList.stream()
                        .filter(el -> MavenPomUtils.hasChildWithContainedText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "version", "_mapped_"))
                        .forEach(el -> MavenPomUtils.setChildText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "version", DeobfuscatingVersionUtils.adaptDeobfuscatedVersion(MavenPomUtils.getChildText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "version"))));
            });
        });

        return mavenPublication;
    }

    public void configureMinecraftLibraryConfiguration(Configuration configuration) {
        MinecraftExtension minecraftExtension = this.project.getExtensions().findByType(MinecraftExtension.class);
        if (minecraftExtension == null)
            return;

        minecraftExtension.getRuns().configureEach(runConfig -> {
            Supplier<String> librariesSupplier = () -> configuration.copyRecursive().resolve().stream()
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(File.pathSeparator));
            Supplier<String> oldToken = runConfig.getLazyTokens().get("minecraft_classpath");
            if (oldToken == null) {
                runConfig.lazyToken("minecraft_classpath", librariesSupplier);
            } else {
                runConfig.lazyToken("minecraft_classpath", () -> {
                    String existing = oldToken.get();
                    String candidate = librariesSupplier.get();

                    return candidate.trim().isEmpty()
                            ? existing
                            : existing + File.pathSeparator + candidate;
                });
            }
        });
    }
}
