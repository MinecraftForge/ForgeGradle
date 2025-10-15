/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.SharedUtil;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;

/** Internal utilities. Documented for maintainability, NOT for public consumption. */
final class Util extends SharedUtil {
    static boolean isPresent(String c) {
        return !c.isBlank();
    }

    static String dependencyToCamelCase(Dependency dependency) {
        var list = new ArrayList<String>(3);

        var group = dependency.getGroup();
        if (group != null)
            list.addAll(Arrays.asList(group.split("\\.")));

        list.add(dependency.getName());

        try {
            list.add(InvokerHelper.getProperty(dependency, "classifer").toString());
        } catch (Exception ignored) {
            // No classifier, not a problem
        }

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
