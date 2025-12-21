/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.Version;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

final class Util extends SharedUtil {
    static String checkMappingsParam(ForgeGradleProblems problems, @Nullable Object param, String name) {
        if (param == null)
            throw problems.nullMappingsParam(name);

        return param.toString();
    }

    static boolean isPresent(String c) {
        return !c.isBlank();
    }

    static String dependencyToCamelCase(Dependency dependency) {
        return dependencyToCamelCase(dependency.getGroup(), dependency.getName());
    }

    static String dependencyToCamelCase(ModuleIdentifier dependency) {
        return dependencyToCamelCase(dependency.getGroup(), dependency.getName());
    }

    static String dependencyToCamelCase(@Nullable String group, String name) {
        var list = new ArrayList<String>(3);

        if (group != null)
            list.addAll(Arrays.asList(group.split("\\.")));

        list.add(name);

        var builder = new StringBuilder(64);
        for (var s : list) {
            builder.append(StringGroovyMethods.capitalize(s));
        }
        return builder.toString();
    }

    static @Nullable SourceSet getSourceSet(NamedDomainObjectSet<Configuration> configurations, SourceSetContainer sourceSets, Dependency dependency) {
        for (var sourceSet : sourceSets) {
            if (contains(configurations, sourceSet, false, dependency)) {
                return sourceSet;
            }
        }

        return null;
    }
}
