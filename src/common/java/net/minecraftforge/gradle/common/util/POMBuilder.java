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

package net.minecraftforge.gradle.common.util;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nullable;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class POMBuilder {

    private static final Pattern PATTERN_ARTIFACT = Pattern.compile(
            "^(?<group>[^:]+):(?<name>[^:]+)(?::(?<version>[^:@]+))(?::(?<classifier>[^:@]+))?(?:@(?<extension>[^:]+))?$");

    private final String group, name, version;
    private final Dependencies dependencies = new Dependencies();
    @Nullable
    private String description;

    public POMBuilder(String group, String name, String version) {
        this.group = group;
        this.name = name;
        this.version = version;
    }

    public POMBuilder description(String description) {
        this.description = description;
        return this;
    }

    public POMBuilder dependencies(Consumer<Dependencies> configurator) {
        configurator.accept(dependencies);
        return this;
    }

    public Dependencies dependencies() {
        return dependencies;
    }

    public String tryBuild() {
        try {
            return build();
        } catch (Exception ex) {
            return null;
        }
    }

    public String build() throws ParserConfigurationException, TransformerException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element project = doc.createElement("project");
        project.setAttribute("xsi:schemaLocation", "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd");
        project.setAttribute("xmlns", "http://maven.apache.org/POM/4.0.0");
        project.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
        doc.appendChild(project);

        set(doc, project, "modelVersion", "4.0.0");
        set(doc, project, "groupId", group);
        set(doc, project, "artifactId", name);
        set(doc, project, "version", version);
        set(doc, project, "name", name);
        if (description != null) {
            set(doc, project, "description", description);
        }

        if (!dependencies.dependencies.isEmpty()) {
            Element dependencies = doc.createElement("dependencies");
            for (Dependencies.Dependency dependency : this.dependencies.dependencies) {
                Element dep = doc.createElement("dependency");
                set(doc, dep, "groupId", dependency.group);
                set(doc, dep, "artifactId", dependency.name);
                set(doc, dep, "version", dependency.version);
                if (dependency.classifier != null && !"jar".equals(dependency.classifier)) {
                    set(doc, dep, "classifier", dependency.classifier);
                }
                if (dependency.extension != null) {
                    set(doc, dep, "type", dependency.extension);
                }
                if (dependency.scope != null) {
                    set(doc, dep, "scope", dependency.scope);
                }
                dependencies.appendChild(dep);
            }
            project.appendChild(dependencies);
        }

        doc.normalizeDocument();

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes"); //Make it pretty
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        DOMSource source = new DOMSource(doc);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(baos);
        transformer.transform(source, result);

        return new String(baos.toByteArray());
    }

    private static void set(Document doc, Element parent, String name, String value) {
        Element description = doc.createElement(name);
        description.appendChild(doc.createTextNode(value));
        parent.appendChild(description);
    }

    public class Dependencies {

        private final Set<Dependency> dependencies = new LinkedHashSet<>();

        private Dependencies() {
        }

        public Dependency add(String artifact, @Nullable String scope) {
            Matcher matcher = PATTERN_ARTIFACT.matcher(artifact);
            if (!matcher.matches()) throw new IllegalArgumentException("Invalid maven artifact specifier: " + artifact);
            return add(matcher.group("group"), matcher.group("name"), matcher.group("version"),
                    matcher.group("classifier"), matcher.group("extension"), scope);
        }

        public Dependency addHard(String artifact, @Nullable String scope) {
            Matcher matcher = PATTERN_ARTIFACT.matcher(artifact);
            if (!matcher.matches()) throw new IllegalArgumentException("Invalid maven artifact specifier: " + artifact);
            return add(matcher.group("group"), matcher.group("name"), '[' + matcher.group("version") + ']',
                    matcher.group("classifier"), matcher.group("extension"), scope);
        }

        public Dependency add(String group, String name, String version,
                              @Nullable String classifier, @Nullable String extension, @Nullable String scope) {
            Dependency dep = new Dependency(group, name, version, classifier, extension, scope);
            dependencies.add(dep);
            return dep;
        }

        public class Dependency {
            private final String group, name, version;
            @Nullable
            private String classifier, extension, scope;

            private Dependency(String group, String name, String version,
                               @Nullable String classifier, @Nullable String extension, @Nullable String scope) {
                this.group = group;
                this.name = name;
                this.version = version;
                this.classifier = classifier;
                this.extension = extension;
                this.scope = scope;
            }

            public void withClassifier(@Nullable String classifier) {
                this.classifier = classifier;
            }
        }

    }

}
