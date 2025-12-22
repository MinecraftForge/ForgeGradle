/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import groovy.transform.NamedVariant;

import java.util.Map;

/// A container for Minecraft mappings, primarily used to determine the mappings to be used by the Minecraft
/// dependency.
///
/// @see MinecraftExtension
/// @see MinecraftDependency
public interface MinecraftMappingsContainer {
    /// Gets the mappings for this container.
    ///
    /// @return The mappings to be used
    /// @throws IllegalStateException If the mappings are not set when they are queried, an [IllegalStateException]
    /// @see #mappings(String, String)
    MinecraftMappings getMappings();

    /**
     * Sets the mappings to use for the Minecraft dependency.
     * <p>This method includes a generated named variant that can make declaration in your buildscript easier.</p>
     * <pre><code>
     * minecraft {
     *     mappings channel: 'official', version: '1.21.11'
     * }
     * </code></pre>
     *
     * @param channel The mappings channel
     * @param version The mappings version
     * @throws IllegalArgumentException If any parameter is {@code null}
     * @apiNote Mappings should only be declared once. A warning will be reported on re-declaration.
     * @see <a href="https://docs.groovy-lang.org/latest/html/api/groovy/transform/NamedVariant.html">NamedVariant</a>
     */
    @NamedVariant
    void mappings(String channel, String version);

    /// Sets the mappings to use for the Minecraft dependency.
    ///
    /// @param namedArgs The named arguments
    /// @throws IllegalArgumentException If any parameter is `null`
    /// @apiNote Mappings should only be declared once. A warning will be reported on re-declaration.
    /// @see #mappings(String, String)
    void mappings(
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
    );
}
