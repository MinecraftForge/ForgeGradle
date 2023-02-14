/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp;

import net.minecraftforge.gradle.common.util.Artifact;

import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;

public abstract class MCPExtension {
    public static final String EXTENSION_NAME = "mcp";

    protected final Project project;
    private final Property<Artifact> config;

    @Inject
    public MCPExtension(final Project project) {
        this.project = project;
        this.config = project.getObjects().property(Artifact.class);
    }

    public Property<Artifact> getConfig() {
        return this.config;
    }

    public void setConfig(Provider<String> value) {
        getConfig().set(value.map(s -> {
            if (s.indexOf(':') != -1) { // Full artifact
                return Artifact.from(s);
            } else {
                return Artifact.from("de.oceanlabs.mcp:mcp_config:" + s + "@zip");
            }
        }));
    }

    public void setConfig(String value) {
        setConfig(project.provider(() -> value));
    }

    public abstract Property<String> getPipeline();
}
