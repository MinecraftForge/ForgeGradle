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
import org.gradle.api.publish.maven.MavenPom;
import org.gradle.api.publish.maven.MavenPublication;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JarJarProjectExtension  extends GroovyObjectSupport
{
    public static final String EXTENSION_NAME = "jarJar";

    private final Attribute<String> fixedJarJarVersionAttribute = Attribute.of("fixedJarJarVersion", String.class);

    private final Project project;

    public JarJarProjectExtension(final Project project) {this.project = project;}

    public void enable() {
        if (project.getTasks().findByPath(UserDevPlugin.JAR_JAR_TASK_NAME) != null) {
            Objects.requireNonNull(project.getTasks().findByPath(UserDevPlugin.JAR_JAR_TASK_NAME)).setEnabled(true);
        }
    }

    public void fromRuntimeConfiguration() {
        project.getTasks().withType(JarJar.class).all(JarJar::fromRuntimeConfiguration);
    }

    public void pin(Dependency dependency, String version) {
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

    public JarJarProjectExtension dependencies(Action<DependencyFilter> c) {
        project.getTasks().withType(JarJar.class).all(jarJar -> jarJar.dependencies(c));
        return this;
    }

    public MavenPublication component(MavenPublication mavenPublication)
    {
        final MavenPublication cleaned = project.getExtensions().getByType(DependencyManagementExtension.class).component(mavenPublication);

        final Set<JarJar> isEnabled = project.getTasks().withType(JarJar.class).stream().filter(JarJar::isEnabled).collect(Collectors.toSet());
        if (isEnabled.isEmpty())
        {
            return mavenPublication;
        }

        isEnabled.forEach(cleaned::artifact);
        final Set<ResolvedDependency> dependencies = isEnabled.stream().flatMap(jarJar -> jarJar.getResolvedDependencies().stream()).collect(Collectors.toSet());

        cleaned.pom(pom -> {
            pom.withXml(xml -> {
                final NodeList potentialDependenciesList = xml.asNode().getAt(QName.valueOf("{http://maven.apache.org/POM/4.0.0}dependencies"));
                Node dependenciesNode;
                if (potentialDependenciesList.isEmpty()) {
                    dependenciesNode = xml.asNode().appendNode("{http://maven.apache.org/POM/4.0.0}dependencies");
                }
                else {
                    dependenciesNode = (Node) potentialDependenciesList.get(0);
                }

                dependencies.forEach(it -> {
                    final Node dependencyNode = dependenciesNode.appendNode("{http://maven.apache.org/POM/4.0.0}dependency");
                    dependencyNode.appendNode("{http://maven.apache.org/POM/4.0.0}groupId", it.getModuleGroup());
                    dependencyNode.appendNode("{http://maven.apache.org/POM/4.0.0}artifactId", it.getName());
                    dependencyNode.appendNode("{http://maven.apache.org/POM/4.0.0}version", it.getModuleVersion());
                    dependencyNode.appendNode("{http://maven.apache.org/POM/4.0.0}scope", "runtime");
                });
            });
        });

        return cleaned;
    }
}
