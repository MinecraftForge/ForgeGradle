/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mcp;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An extension object that holds all registered {@link ChannelProvider}s.
 * Retrieve an instance of this using {@link ExtensionContainer#getByType(Class)} from the {@link Project} instance.
 */
public class ChannelProvidersExtension {
    public static final String EXTENSION_NAME = "channelProviders";
    private final Map<String, ChannelProvider> providers = new ConcurrentHashMap<>();

    public ChannelProvidersExtension() {
        // Add the default providers
        addProvider(new OfficialChannelProvider());
        addProvider(new MCPChannelProvider());
    }

    /**
     * Returns an unmodifiable view of the provider map.
     *
     * @return an unmodifiable view of the provider map
     */
    public Map<String, ChannelProvider> getProviderMap() {
        return Collections.unmodifiableMap(providers);
    }

    /**
     * Retrieves a possibly-null {@link ChannelProvider} from the provider map using the {@code channel} key.
     *
     * @param channel the channel key to use for querying
     * @return a possibly-null {@link ChannelProvider} from the provider map
     */
    @Nullable
    public ChannelProvider getProvider(String channel) {
        return providers.get(channel);
    }

    /**
     * Adds a {@link ChannelProvider} to the provider map using all of its defined channels in {@link ChannelProvider#getChannels()}.
     *
     * @param provider The provider to add to the channel provider map
     * @throws IllegalArgumentException if a channel name from the provider is already registered to another provider
     */
    public void addProvider(ChannelProvider provider) {
        for (String channel : provider.getChannels()) {
            ChannelProvider previous = providers.get(channel);
            if (previous != null)
                throw new IllegalArgumentException(channel + " is already registered to " + previous + " containing channels " + previous.getChannels());
            providers.put(channel, provider);
        }
    }
}
