package net.minecraftforge.gradle.userdev.jarjar;

import groovy.lang.GroovyObjectSupport;
import groovy.util.Node;
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
                final Node dependenciesNode = xml.asNode().appendNode("dependencies");
                dependencies.forEach(it -> {
                    final Node dependencyNode = dependenciesNode.appendNode("dependency");
                    dependencyNode.appendNode("groupId", it.getModuleGroup());
                    dependencyNode.appendNode("artifactId", it.getName());
                    dependencyNode.appendNode("version", it.getModuleVersion());
                    dependencyNode.appendNode("scope", "runtime");
                });
            });
        });

        return cleaned;
    }
}
