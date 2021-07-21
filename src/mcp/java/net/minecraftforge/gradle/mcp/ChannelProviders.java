/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.mcp;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChannelProviders {
    private static final Map<String, ChannelProvider> PROVIDERS = new HashMap<>();

    static {
        // Add the default providers
        addProvider(new OfficialChannelProvider());
        addProvider(new ParchmentChannelProvider());
        addProvider(new MCPChannelProvider());
    }

    public static Map<String, ChannelProvider> getProviders() {
        return Collections.unmodifiableMap(PROVIDERS);
    }

    @Nullable
    public static ChannelProvider getProvider(String channel) {
        return PROVIDERS.get(channel);
    }

    public static void addProvider(ChannelProvider provider) {
        for (String channel : provider.getChannels()) {
            PROVIDERS.put(channel, provider);
        }
    }
}
