/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.userdev.jarjar;

import groovy.lang.GroovyObjectSupport;
import groovy.util.Node;
import net.minecraftforge.gradle.userdev.DependencyManagementExtension;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.gradle.userdev.dependency.DependencyFilter;
import net.minecraftforge.gradle.userdev.tasks.JarJar;
import net.minecraftforge.gradle.userdev.util.MavenPomUtils;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.*;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.publish.maven.MavenPublication;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class JarJarProjectExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "jarJar";

    private final Attribute<String> fixedJarJarVersionAttribute = Attribute.of("fixedJarJarVersion", String.class);
    private final Attribute<String> jarJarRangeAttribute = Attribute.of("jarJarRange", String.class);

    private final Project project;
    private boolean disabled;
    private boolean disableDefaultSources;

    public JarJarProjectExtension(final Project project) {
        this.project = project;
    }

    public void enable() {
        if (!this.disabled)
            enable(true);
    }

    private void enable(boolean enabled) {
        final Task task = project.getTasks().findByPath(UserDevPlugin.JAR_JAR_TASK_NAME);
        if (task != null) {
            task.setEnabled(enabled);
        }
    }

    public void disable() {
        disable(true);
    }

    public void disable(boolean disable) {
        this.disabled = disable;
        if (disable) {
            enable(false);
        }
    }

    public boolean getDefaultSourcesDisabled() {
        return this.disableDefaultSources;
    }

    public void disableDefaultSources() {
        disableDefaultSources(true);
    }

    public void disableDefaultSources(boolean value) {
        this.disableDefaultSources = value;
    }

    public void fromRuntimeConfiguration() {
        enable();
        project.getTasks().withType(JarJar.class).configureEach(JarJar::fromRuntimeConfiguration);
    }

    public void pin(Dependency dependency, String version) {
        enable();
        if (dependency instanceof ModuleDependency) {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            moduleDependency.attributes(attributeContainer -> attributeContainer.attribute(fixedJarJarVersionAttribute, version));
        }
    }

    public Optional<String> getPin(Dependency dependency) {
        if (dependency instanceof ModuleDependency) {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            return Optional.ofNullable(moduleDependency.getAttributes().getAttribute(fixedJarJarVersionAttribute));
        }
        return Optional.empty();
    }

    public void ranged(Dependency dependency, String range) {
        enable();
        if (dependency instanceof ModuleDependency) {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            moduleDependency.attributes(attributeContainer -> attributeContainer.attribute(jarJarRangeAttribute, range));
        }
    }

    public Optional<String> getRange(Dependency dependency) {
        if (dependency instanceof ModuleDependency) {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            return Optional.ofNullable(moduleDependency.getAttributes().getAttribute(jarJarRangeAttribute));
        }
        return Optional.empty();
    }

    public JarJarProjectExtension dependencies(Action<DependencyFilter> c) {
        enable();
        project.getTasks().withType(JarJar.class).configureEach(jarJar -> jarJar.dependencies(c));
        return this;
    }

    public MavenPublication component(MavenPublication mavenPublication) {
        enable();
        project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);
        project.getTasks().withType(JarJar.class).configureEach(task -> component(mavenPublication, task, false));

        return mavenPublication;
    }

    public MavenPublication component(MavenPublication mavenPublication, boolean handleDependencies) {
        enable();
        project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);
        project.getTasks().withType(JarJar.class).configureEach(task -> component(mavenPublication, task, false));

        return mavenPublication;
    }

    public MavenPublication component(MavenPublication mavenPublication, JarJar task) {
        enable();
        return component(mavenPublication, task, true, true);
    }

    public MavenPublication cleanedComponent(MavenPublication mavenPublication, JarJar task, boolean handleDependencies) {
        enable();
        return component(mavenPublication, task, true, handleDependencies);
    }

    private MavenPublication component(MavenPublication mavenPublication, JarJar task, boolean handleCleaning) {
        return component(mavenPublication, task, handleCleaning, true);
    }

    private MavenPublication component(MavenPublication mavenPublication, JarJar task, boolean handleCleaning, boolean handleDependencies) {
        if (!task.isEnabled()) {
            return mavenPublication;
        }

        if (handleCleaning) {
            project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);
        }

        mavenPublication.artifact(task, mavenArtifact -> {
            mavenArtifact.setClassifier(task.getArchiveClassifier().get());
            mavenArtifact.setExtension(task.getArchiveExtension().get());
        });

        if (handleDependencies) {
            final Set<ResolvedDependency> dependencies = task.getResolvedDependencies();

            mavenPublication.pom(pom -> {
                pom.withXml(xml -> {
                    Node dependenciesNode = MavenPomUtils.getDependenciesNode(xml);
                    final List<Node> dependenciesNodeList = MavenPomUtils.getDependencyNodes(xml);

                    // From all dependencies
                    dependencies.forEach(dependency ->
                            dependenciesNodeList.stream()
                                    .filter(el -> MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "artifactId", dependency.getModuleName())
                                            && MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "groupId", dependency.getModuleGroup()))
                                    .forEach(el -> MavenPomUtils.setChildText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "version", dependency.getModuleVersion()))
                    );

                    dependencies.stream()
                            .filter(dependency -> dependenciesNodeList.stream()
                                    .noneMatch(el -> MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "artifactId", dependency.getModuleName())
                                            && MavenPomUtils.hasChildWithText(el, MavenPomUtils.MAVEN_POM_NAMESPACE + "groupId", dependency.getModuleGroup())))
                            .forEach(it -> {
                                final Node dependencyNode = dependenciesNode.appendNode("dependency");
                                dependencyNode.appendNode("groupId", it.getModuleGroup());
                                dependencyNode.appendNode("artifactId", it.getModuleName());
                                dependencyNode.appendNode("version", it.getModuleVersion());
                                dependencyNode.appendNode("scope", "runtime");
                            });
                });
            });
        }


        return mavenPublication;
    }
}
