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
import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import net.minecraftforge.gradle.userdev.DependencyManagementExtension;
import net.minecraftforge.gradle.userdev.UserDevPlugin;
import net.minecraftforge.gradle.userdev.dependency.DependencyFilter;
import net.minecraftforge.gradle.userdev.tasks.JarJar;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPublication;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JarJarProjectExtension extends GroovyObjectSupport
{
    public static final  String EXTENSION_NAME      = "jarJar";
    private static final String MAVEN_POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}";

    private final Attribute<String> fixedJarJarVersionAttribute = Attribute.of("fixedJarJarVersion", String.class);
    private final Attribute<String> jarJarRangeAttribute        = Attribute.of("jarJarRange", String.class);

    private final Project project;

    public JarJarProjectExtension(final Project project) {this.project = project;}

    public void enable()
    {
        if (project.getTasks().findByPath(UserDevPlugin.JAR_JAR_TASK_NAME) != null)
        {
            Objects.requireNonNull(project.getTasks().findByPath(UserDevPlugin.JAR_JAR_TASK_NAME)).setEnabled(true);
        }
    }

    public void fromRuntimeConfiguration()
    {
        enable();
        project.getTasks().withType(JarJar.class).all(JarJar::fromRuntimeConfiguration);
    }

    public void pin(Dependency dependency, String version)
    {
        enable();
        if (dependency instanceof ModuleDependency)
        {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            moduleDependency.attributes(attributeContainer -> attributeContainer.attribute(fixedJarJarVersionAttribute, version));
        }
    }

    public Optional<String> getPin(Dependency dependency)
    {
        if (dependency instanceof ModuleDependency)
        {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            return Optional.ofNullable(moduleDependency.getAttributes().getAttribute(fixedJarJarVersionAttribute));
        }
        return Optional.empty();
    }

    public void ranged(Dependency dependency, String range)
    {
        enable();
        if (dependency instanceof ModuleDependency)
        {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            moduleDependency.attributes(attributeContainer -> attributeContainer.attribute(jarJarRangeAttribute, range));
        }
    }

    public Optional<String> getRange(Dependency dependency)
    {
        if (dependency instanceof ModuleDependency)
        {
            final ModuleDependency moduleDependency = (ModuleDependency) dependency;
            return Optional.ofNullable(moduleDependency.getAttributes().getAttribute(jarJarRangeAttribute));
        }
        return Optional.empty();
    }

    public JarJarProjectExtension dependencies(Action<DependencyFilter> c)
    {
        enable();
        project.getTasks().withType(JarJar.class).all(jarJar -> jarJar.dependencies(c));
        return this;
    }

    public MavenPublication component(MavenPublication mavenPublication)
    {
        enable();
        project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);
        project.getTasks().withType(JarJar.class).all(task -> component(mavenPublication, task, false));

        return mavenPublication;
    }

    public MavenPublication component(MavenPublication mavenPublication, JarJar task)
    {
        enable();
        return component(mavenPublication, task, true);
    }

    private MavenPublication component(MavenPublication mavenPublication, JarJar task, boolean handleCleaning)
    {
        project.afterEvaluate(p -> {
            if (!task.isEnabled())
            {
                return;
            }

            if (handleCleaning)
            {
                project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);
            }


            mavenPublication.artifact(task, mavenArtifact -> {
                mavenArtifact.setClassifier(task.getArchiveClassifier().get());
                mavenArtifact.setExtension(task.getArchiveExtension().get());
            });


            final Set<ResolvedDependency> dependencies = task.getResolvedDependencies();

            mavenPublication.pom(pom -> {
                pom.withXml(xml -> {
                    final NodeList potentialDependenciesList = xml.asNode().getAt(QName.valueOf(MAVEN_POM_NAMESPACE + "dependencies"));
                    Node dependenciesNode;
                    if (potentialDependenciesList.isEmpty())
                    {
                        dependenciesNode = xml.asNode().appendNode("dependencies");
                    }
                    else
                    {
                        dependenciesNode = (Node) potentialDependenciesList.get(0);
                    }

                    //Yes this potentially can generate duplicate entries, but it should not really matter.
                    //Two potential scenarios occur: The original dep which is used to compile is a range, no problem that should resolve.
                    //The other is where JarJar is told to use a version which not compatible with the original dependency, nothing we can do developers
                    //shooting themselves in the foot is not really something we can prevent.
                    dependencies.forEach(it -> {
                        final Node dependencyNode = dependenciesNode.appendNode("dependency");
                        dependencyNode.appendNode("groupId", it.getModuleGroup());
                        dependencyNode.appendNode("artifactId", it.getModuleName());
                        dependencyNode.appendNode("version", it.getModuleVersion());
                        dependencyNode.appendNode("scope", "runtime");
                    });
                });
            });
        });

        return mavenPublication;
    }
}
