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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

final class Util extends SharedUtil {
    static String checkMappingsParam(ForgeGradleProblems problems, @Nullable Object param, String name) {
        if (param == null || param.toString().isEmpty())
            throw problems.nullMappingsParam(name);

        return param.toString();
    }

    static boolean isPresent(String c) {
        return !c.isBlank();
    }

    static String dependencyToCamelCase(ModuleIdentifier dependency) {
        return dependencyToCamelCase(dependency.getGroup(), dependency.getName());
    }

    static String dependencyToCamelCase(@Nullable String group, String name) {
        var list = new ArrayList<String>(3);

        boolean isForge = "net.minecraftforge".equals(group) && "forge".equals(name);

        if (group != null && !isForge)
            list.addAll(Arrays.asList(group.split("\\.")));

        // TODO: [ForgeGradle] Add version distinction for run task names
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

    // Copied straight from FG6
    // Replace tokens in a string that are wrapped in {}
    // Supports escaping {} or \ using \
    static String replaceTokens(Map<String, ?> tokens, String value, @Nullable Set<String> unknown) {
        if (value.length() <= 2 || value.indexOf('{') == -1)
            return value;

        var buf = new StringBuilder();

        for (int x = 0; x < value.length(); x++) {
            char c = value.charAt(x);
            if (c == '\\') {
                if (x == value.length() - 1)
                    throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                buf.append(value.charAt(++x));
            } else if (c == '{' || c ==  '\'') {
                StringBuilder key = new StringBuilder();
                for (int y = x + 1; y <= value.length(); y++) {
                    if (y == value.length())
                        throw new IllegalArgumentException("Illegal pattern (Unclosed " + c + "): " + value);
                    char d = value.charAt(y);
                    if (d == '\\') {
                        if (y == value.length() - 1)
                            throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                        key.append(value.charAt(++y));
                    } else if (c == '{' && d == '}') {
                        //noinspection ReassignedVariable,SuspiciousNameCombination
                        x = y;
                        break;
                    } else if (c == '\'' && d == '\'') {
                        //noinspection ReassignedVariable,SuspiciousNameCombination
                        x = y;
                        break;
                    } else
                        key.append(d);
                }
                if (c == '\'')
                    buf.append(key);
                else {
                    Object v = tokens.get(key.toString());
                    if (v instanceof Supplier)
                        v = ((Supplier<?>) v).get();

                    if (v == null) {
                        if (unknown != null)
                            unknown.add(key.toString());
                        buf.append('{').append(key).append('}');
                    } else {
                        buf.append(v);
                    }
                }
            } else {
                buf.append(c);
            }
        }

        return buf.toString();
    }

}
