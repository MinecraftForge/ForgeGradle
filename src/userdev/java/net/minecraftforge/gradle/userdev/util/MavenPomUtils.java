/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.util;

import groovy.namespace.QName;
import groovy.util.Node;
import groovy.util.NodeList;
import org.gradle.api.XmlProvider;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for handling working with maven poms.
 */
public class MavenPomUtils {

    public static final String MAVEN_POM_NAMESPACE = "{http://maven.apache.org/POM/4.0.0}";

    private MavenPomUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: MavenPomUtils. This is a utility class");
    }

    @SuppressWarnings("unchecked")
    public static boolean hasChildWithText(final Node node, final String childKey, final String... candidateValues) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                .map(Node.class::cast)
                .collect(Collectors.toList()));

        return childList.stream()
                .anyMatch(el -> Arrays.stream(candidateValues).anyMatch(value -> value.equals(el.text())));
    }

    @SuppressWarnings("unchecked")
    public static boolean hasChildWithContainedText(final Node node, final String childKey, final String expectedValue) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                .map(Node.class::cast)
                .collect(Collectors.toList()));

        return childList.stream()
                .anyMatch(el -> el.text().contains(expectedValue));
    }

    @SuppressWarnings("unchecked")
    public static String getChildText(final Node node, final String childKey) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                .map(Node.class::cast)
                .collect(Collectors.toList()));

        return childList.stream().map(Node::text).findFirst().orElse("");
    }

    @SuppressWarnings("unchecked")
    public static void setChildText(final Node node, final String childKey, final String expectedValue) {
        final NodeList children = node.getAt(QName.valueOf(childKey));
        final List<Node> childList = (List<Node>) (children.stream()
                .map(Node.class::cast)
                .collect(Collectors.toList()));

        childList.forEach(el -> el.setValue(expectedValue));
    }

    public static NodeList getDependenciesNodeList(final XmlProvider xml) {
        Node dependenciesNode = getDependenciesNode(xml);
        return dependenciesNode.getAt(QName.valueOf(MAVEN_POM_NAMESPACE + "*"));
    }

    public static Node getDependenciesNode(final XmlProvider xml) {
        final NodeList potentialDependenciesList = xml.asNode().getAt(QName.valueOf(MAVEN_POM_NAMESPACE + "dependencies"));
        Node dependenciesNode;
        if (potentialDependenciesList.isEmpty()) {
            dependenciesNode = xml.asNode().appendNode("dependencies");
        } else {
            dependenciesNode = (Node) potentialDependenciesList.get(0);
        }
        return dependenciesNode;
    }

    @SuppressWarnings("unchecked")
    public static List<Node> getDependencyNodes(final XmlProvider xml) {
        final NodeList existingDependencies = getDependenciesNodeList(xml);

        return (List<Node>) existingDependencies.stream()
                .map(Node.class::cast)
                .collect(Collectors.toList());
    }
}
