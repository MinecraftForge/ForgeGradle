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

package net.minecraftforge.gradle.userdev;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DependencyManagementExtension extends GroovyObjectSupport {
    public static final String EXTENSION_NAME = "fg";
    private final Project project;
    private final DependencyRemapper remapper;
    public DependencyManagementExtension(Project project, DependencyRemapper remapper) {
        this.project = project;
        this.remapper = remapper;
    }

    @SuppressWarnings("unused")
    public Dependency deobf(Object dependency) {
        return deobf(dependency, null);
    }

    public Dependency deobf(Object dependency, Closure<?> configure){
        Dependency baseDependency = project.getDependencies().create(dependency, configure);
        project.getConfigurations().getByName(UserDevPlugin.OBF).getDependencies().add(baseDependency);

        return remapper.remap(baseDependency);
    }

    @SuppressWarnings({"ConstantConditions", "unchecked"})
    public MavenPublication component(MavenPublication mavenPublication) {
        project.getTasks().withType(GenerateModuleMetadata.class).forEach(generateModuleMetadata -> generateModuleMetadata.setEnabled(false));

        mavenPublication.suppressAllPomMetadataWarnings(); //We have weird handling of stuff and things when it comes to versions and other features. No need to spam the log when that happens.

        mavenPublication.pom(pom -> {
            pom.withXml(xml -> {
                final NodeList potentialDependenciesList = xml.asNode().getAt(QName.valueOf("{http://maven.apache.org/POM/4.0.0}dependencies"));
                Node dependenciesNode;
                if (potentialDependenciesList.isEmpty()) {
                    dependenciesNode = xml.asNode().appendNode("{http://maven.apache.org/POM/4.0.0}dependencies");
                }
                else {
                    dependenciesNode = (Node) potentialDependenciesList.get(0);
                }
                final NodeList dependencies = dependenciesNode.getAt(QName.valueOf("{http://maven.apache.org/POM/4.0.0}*")); //grab all dependency nodes in a neat list;

                final List<Node> dependenciesNodeList = (List<Node>) dependencies.stream()
                                                                       .filter(Node.class::isInstance)
                                                                       .map(Node.class::cast)
                                                                       .collect(Collectors.toList());

                dependenciesNodeList.stream()
                  .filter(el -> hasChildWithText(el, "{http://maven.apache.org/POM/4.0.0}artifactId", "forge") && hasChildWithText(el, "{http://maven.apache.org/POM/4.0.0}groupId", "net.minecraftforge"))
                  .forEach(el -> el.parent().remove(el));


                dependenciesNodeList.stream()
                  .filter(el -> hasChildWithContainedText(el, "{http://maven.apache.org/POM/4.0.0}version", "_mapped_"))
                  .forEach(el -> setChildText(el, "{http://maven.apache.org/POM/4.0.0}version", getVersionFrom(getChildText(el, "{http://maven.apache.org/POM/4.0.0}version"))));
            });
        });

        return mavenPublication;
    }

    @SuppressWarnings("unchecked")
    private boolean hasChildWithText(final Node node, final String childKey, final String expectedValue) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                                                   .map(Node.class::cast)
                                                   .collect(Collectors.toList()));

        return childList.stream()
                        .anyMatch(el -> el.text().equals(expectedValue));
    }

    @SuppressWarnings("unchecked")
    private boolean hasChildWithContainedText(final Node node, final String childKey, final String expectedValue) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                                                     .map(Node.class::cast)
                                                     .collect(Collectors.toList()));

        return childList.stream()
                 .anyMatch(el -> el.text().contains(expectedValue));
    }

    @SuppressWarnings("unchecked")
    private String getChildText(final Node node, final String childKey) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                                                     .map(Node.class::cast)
                                                     .collect(Collectors.toList()));

        return childList.stream().map(Node::text).findFirst().orElse("");
    }

    @SuppressWarnings("unchecked")
    private void setChildText(final Node node, final String childKey, final String expectedValue) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                                                     .map(Node.class::cast)
                                                     .collect(Collectors.toList()));

        childList.forEach(el -> el.setValue(expectedValue));
    }

    private String getVersionFrom(final String version)
    {
        if (version.contains("_mapped_")) {
            return version.split("_mapped_")[0];
        }

        return version;
    }
}
