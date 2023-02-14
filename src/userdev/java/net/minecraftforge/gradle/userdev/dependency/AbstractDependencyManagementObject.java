/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.dependency;

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;

import java.util.regex.Pattern;

public class AbstractDependencyManagementObject implements DependencyManagementObject {

    protected final Project project;

    public AbstractDependencyManagementObject(Project project) {
        this.project = project;
    }

    protected static ArtifactIdentifier createArtifactIdentifier(final ResolvedDependency dependency) {
        return new ArtifactIdentifier(dependency.getModuleGroup(), dependency.getModuleName(), dependency.getModuleVersion());
    }

    protected static ArtifactIdentifier createArtifactIdentifier(final ModuleDependency dependency) {
        return new ArtifactIdentifier(dependency.getGroup(), dependency.getName(), dependency.getVersion());
    }

    public Spec<? super DependencyManagementObject.ArtifactIdentifier> dependency(Object notation) {
        return dependency(project.getDependencies().create(notation));
    }

    public Spec<? super DependencyManagementObject.ArtifactIdentifier> dependency(Dependency dependency) {
        return this.dependency(new Closure<Boolean>(null) {

            @SuppressWarnings("ConstantConditions")
            @Override
            public Boolean call(final Object it) {
                if (it instanceof DependencyManagementObject.ArtifactIdentifier) {
                    final DependencyManagementObject.ArtifactIdentifier identifier = (DependencyManagementObject.ArtifactIdentifier) it;
                    return (dependency.getGroup() == null || Pattern.matches(dependency.getGroup(), identifier.getGroup())) &&
                            (dependency.getName() == null || Pattern.matches(dependency.getName(), identifier.getName())) &&
                            (dependency.getVersion() == null || Pattern.matches(dependency.getVersion(), identifier.getVersion()));
                }

                return false;
            }
        });
    }

    public Spec<? super DependencyManagementObject.ArtifactIdentifier> dependency(Closure<Boolean> spec) {
        return Specs.convertClosureToSpec(spec);
    }
}
