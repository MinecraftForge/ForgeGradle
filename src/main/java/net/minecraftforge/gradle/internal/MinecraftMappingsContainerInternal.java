/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import net.minecraftforge.gradle.MinecraftMappingsContainer;
import org.jspecify.annotations.NullUnmarked;

import java.util.Map;

interface MinecraftMappingsContainerInternal extends MinecraftMappingsContainer {
    // NOTE: Overridden with @UnknownNullability, null is checked in MinecraftMappingsImpl
    @Override
    @NullUnmarked
    void mappings(String channel, String version);

    @Override
    default void mappings(
        @NamedParams({
            @NamedParam(
                type = String.class,
                value = "channel",
                required = true
            ),
            @NamedParam(
                type = String.class,
                value = "version",
                required = true
            )
        }) Map<?, ?> namedArgs
    ) {
        this.mappings(
            namedArgs.containsKey("channel") ? namedArgs.get("channel").toString() : null,
            namedArgs.containsKey("version") ? namedArgs.get("version").toString() : null
        );
    }
}
