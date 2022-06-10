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
        mavenPublication.suppressAllPomMetadataWarnings(); //We have weird handling of stuff and things when it comes to versions and other features. No need to spam the log when that happens.

        mavenPublication.pom(pom -> {
            pom.withXml(xml -> {
                final Node dependenciesNode = xml.asNode().appendNode("dependencies");
                final NodeList dependencies = dependenciesNode.getAt(QName.valueOf("*")); //grab all dependency nodes in a neat list;

                final List<Node> dependenciesNodeList = (List<Node>) dependencies.stream()
                                                                       .filter(Node.class::isInstance)
                                                                       .map(Node.class::cast)
                                                                       .collect(Collectors.toList());

                dependenciesNodeList.stream()
                  .filter(el -> el.attribute("artifactId") == "forge" && el.attribute("groupId") == "net.minecraftforge")
                  .forEach(el -> el.parent().remove(el));


                dependenciesNodeList.stream()
                  .filter(el -> el.attribute("version") instanceof String)
                  .filter(el -> ((String) el.attribute("version")).contains("_mapped_"))
                  .forEach(el -> el.attributes().put("version", getVersionFrom((String) el.attribute("version"))));
            });
        });

        return mavenPublication;
    }

    private String getVersionFrom(final String version)
    {
        if (version.contains("_mapped_")) {
            return version.split("_mapped_")[0];
        }

        return version;
    }
}
