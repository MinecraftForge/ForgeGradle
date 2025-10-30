/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import java.io.Serializable;

/// The mappings used for the Minecraft dependency.
///
/// @see MinecraftMappingsContainer#mappings(String, String)
public interface MinecraftMappings extends Serializable {
    /// Gets the channel for these mappings (i.e. `official`)
    ///
    /// @return The channel
    String getChannel();

    /// Gets the version for these mappings (i.e. `1.21.10`)
    ///
    /// @return The version
    String getVersion();
}
