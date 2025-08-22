/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.jetbrains.annotations.UnknownNullability;

import java.io.Serializable;
import java.util.Objects;

/// The mappings used for the Minecraft dependency.
///
/// @param channel The channel to use (i.e. `'official'`)
/// @param version The version to use (i.e. `'1.21.5'`)
/// @see MinecraftExtensionForProject#mappings(String, String)
public record MinecraftMappings(String channel, String version) implements Serializable {
    /// Creates a new Mappings object.
    ///
    /// Both the channel and version are required, and cannot be null.
    ///
    /// @param channel The channel to use
    /// @param version The version to use
    /// @throws NullPointerException If any parameter is `null`
    public MinecraftMappings(String channel, String version) {
        this.channel = Objects.requireNonNull(channel, "Mappings channel cannot be null");
        this.version = Objects.requireNonNull(version, "Mappings version cannot be null");
    }

    static String checkParam(ForgeGradleProblems problems, @UnknownNullability Object param, String name) {
        if (param == null)
            throw problems.nullMappingsParam(name);

        return param.toString();
    }
}
