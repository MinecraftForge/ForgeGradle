package net.minecraftforge.gradle.common.mapping;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

import org.gradle.api.Project;
import net.minecraftforge.gradle.common.util.BaseRepo;
import net.minecraftforge.gradle.common.mapping.provider.McpMappingProvider;
import net.minecraftforge.gradle.common.mapping.provider.OfficialMappingProvider;

public class MappingProviders {

    /*
     * Can't use SPI due to Gradle's ClassLoading
     * https://discuss.gradle.org/t/loading-serviceloader-providers-in-plugin-project-apply-project/28121
     *
     * Why a Map? Because Gradle Plugins can get applied multiple times causing multiple registration calls (If they register in apply).
     * 500+ IMappingProviders from the original 9 over the course of 30 minutes, thanks.
     */
    private static final Map<String, IMappingProvider> PROVIDERS = new HashMap<>();

    static {
        /* The default ForgeGradle IMappingProviders */
        register("forge:mcp", new McpMappingProvider());
        register("forge:official", new OfficialMappingProvider());
    }

    /**
     * Registers an {@link IMappingProvider} which will then be considered for resolution of a `mappings.zip`.
     */
    public static void register(String identifier, IMappingProvider provider) {
        PROVIDERS.put(identifier, provider);
    }

    /**
     * Unregisters an {@link IMappingProvider} by identifier
     */
    public static void unregister(String identifier) {
        PROVIDERS.remove(identifier);
    }

    /**
     * @see MappingProviders#getInfo(Project, String, String)
     */
    public static IMappingInfo getInfo(Project project, String mapping) throws IOException {
        int idx = mapping.lastIndexOf('_');

        if (idx == -1) {
            throw new IllegalArgumentException("Invalid mapping format: " + mapping);
        }

        String channel = mapping.substring(0, idx);
        String version = mapping.substring(idx + 1);

        return getInfo(project, channel, version);
    }

    /**
     * @return an IMappingInfo representing a resolved `mappings.zip`
     */
    public static IMappingInfo getInfo(Project project, String channel, String version) throws IOException {
        String mapping = channel + "_" + version;

        final IMappingProvider provider = MappingProviders.getProvider(project, channel);
        if (provider == null) {
            throw new IllegalArgumentException("Unknown mapping provider: " + mapping);
        }

        final IMappingInfo info = provider.getMappingInfo(project, channel, version);
        if (info == null) {
            throw new IllegalArgumentException("Couldn't get mapping info: " + mapping);
        }

        return info;
    }

    /**
     * @return an IMappingProvider that can handle the requested channel
     */
    @Nullable
    public static IMappingProvider getProvider(Project project, String channel) {
        project.getLogger().lifecycle("Current number of providers: " + PROVIDERS.size());
        debug(project, "Looking for: " + channel);
        for (Map.Entry<String, IMappingProvider> entry : PROVIDERS.entrySet()) {
            String key = entry.getKey();
            IMappingProvider provider = entry.getValue();

            debug(project, "    Considering: " + key);
            if (!provider.getMappingChannels().contains(channel)) continue;

            debug(project, "    Selected: " + key);
            return provider;
        }

        return null;
    }

    private static void debug(Project project, String message) {
        if (BaseRepo.DEBUG) project.getLogger().lifecycle(message);
    }
}
