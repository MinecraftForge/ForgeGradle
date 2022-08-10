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
import groovy.util.Node;
import groovy.util.NodeList;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.gradle.userdev.util.DeobfuscatingVersionUtils;
import net.minecraftforge.gradle.userdev.util.DependencyRemapper;
import net.minecraftforge.gradle.userdev.util.MavenPomUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;

import java.io.File;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

    public Dependency deobf(Object dependency, Closure<?> configure) {
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
                runConfig.lazyToken("minecraft_classpath", () -> oldToken.get() + File.pathSeparator + librariesSupplier.get());
            }
        });
    }
}
