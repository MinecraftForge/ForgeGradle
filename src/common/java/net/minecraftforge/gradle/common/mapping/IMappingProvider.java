package net.minecraftforge.gradle.common.mapping;

import java.io.IOException;
import java.util.Collection;

import javax.annotation.Nullable;

import org.gradle.api.Project;
import net.minecraftforge.gradle.common.mapping.provider.McpMappingProvider;
import net.minecraftforge.gradle.common.mapping.provider.OfficialMappingProvider;

/**
 * @see McpMappingProvider
 * @see OfficialMappingProvider
 */
public interface IMappingProvider {

    /**
     * Channels should match the regex of [a-z_]+
     * @return The collection of channels that this provider handles.
     */
    Collection<String> getMappingChannels();

    /**
     * Supplies a location to an `mappings.zip`, generating/downloading it if necessary <br>
     * Channels should match the regex of [a-z_]+ <br>
     * Versions should be any maven artifact / filesystem compatible string <br>
     * @param project The current gradle project
     * @param channel The requested channel
     * @param version The requested version
     * @return An enhanced Supplier for the location of the `mappings.zip`
     * @throws IOException
     */
    @Nullable
    IMappingInfo getMappingInfo(Project project, String channel, String version) throws IOException;

}
