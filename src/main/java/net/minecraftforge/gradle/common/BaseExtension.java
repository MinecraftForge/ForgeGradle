/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.common;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.google.common.primitives.Ints;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.delayed.ReplacementProvider;
import org.gradle.api.Project;

import java.net.URL;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

public abstract class BaseExtension
{
    protected static final transient Map<String, String> MCP_VERSION_MAP = ImmutableMap.of("1.8", "9.10");

    private static final String MIN_VERSION = "1.12";
    // The max supported version of minecraft. null means latest is supported
    private static final String MAX_VERSION = null;

    public final String forgeGradleVersion;

    protected transient Project             project;
    protected transient ReplacementProvider replacer;
    protected String                        version;
    protected String                        mcpVersion = "unknown";

    // this should never be touched except by the base plugin in this package
    Map<String, Map<String, int[]>>        mcpJson;
    protected boolean                      mappingsSet     = false;
    protected String                       mappingsChannel = null;
    protected int                          mappingsVersion = -1;
    // custom version for custom mappings
    protected String                mappingsCustom  = null;

    private boolean suppressVersionTest;

    public BaseExtension(BasePlugin<? extends BaseExtension> plugin)
    {
        this.project = plugin.project;
        this.replacer = plugin.replacer;

        String version;
        try
        {
            URL url = BaseExtension.class.getClassLoader().getResource("forgegradle.version.txt");
            version = Resources.toString(url, Constants.CHARSET).trim();

            if (version.equals("${version}"))
            {
                version = "2.1-SNAPSHOT";// fallback
            }

        }
        catch (Exception e)
        {
            // again, the fallback
            version = "2.0-SNAPSHOT";
        }

        forgeGradleVersion = version;
    }

    public void setSuppressVersionTest(boolean b) {
        this.suppressVersionTest = b;
    }

    /**
     * Get the Minecraft version
     *
     * @return The Minecraft version
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * Set the Minecraft version
     *
     * @param version The Minecraft version
     */
    public void setVersion(String version)
    {
        this.version = version;

        replacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

        mcpVersion = MCP_VERSION_MAP.get(version);

        // maybe they set the mappings first
        checkMappings();
    }

    /**
     * Get the MCP data version
     *
     * @return The MCP data version
     */
    public String getMcpVersion()
    {
        return mcpVersion == null ? "unknown" : mcpVersion;
    }

    public void setMcpVersion(String mcpVersion)
    {
        this.mcpVersion = mcpVersion;
    }

    public void copyFrom(BaseExtension ext)
    {
        if ("null".equals(version))
        {
            setVersion(ext.getVersion());
        }

        if ("unknown".equals(mcpVersion))
        {
            setMcpVersion(ext.getMcpVersion());
        }
    }

    /**
     * Get the MCP mappings channel and version<br>
     * Examples: {@code stable_17, snapshot_20151113}
     *
     * @return The channel and version in format: {@code channel_version}.
     *
     * @see <a href="http://export.mcpbot.bspk.rs/">http://export.mcpbot.bspk.rs/</a>
     */
    public String getMappings()
    {
        return mappingsChannel + "_" + (mappingsCustom == null ? mappingsVersion : mappingsCustom);
    }

    /**
     * Get the MCP mappings channel
     *
     * @return The channel
     *
     * @see <a href="http://export.mcpbot.bspk.rs/">http://export.mcpbot.bspk.rs/</a>
     */
    public String getMappingsChannel()
    {
        return mappingsChannel;
    }

    /**
     * Strips the _nodoc and _verbose channel subtypes from the channel name.
     *
     * @return The channel without subtype
     */
    public String getMappingsChannelNoSubtype()
    {
        int underscore = mappingsChannel.indexOf('_');
        if (underscore <= 0) // already has docs.
            return mappingsChannel;
        else
            return mappingsChannel.substring(0, underscore);
    }

    /**
     * Get the MCP mappings version
     *
     * @return The version
     *
     * @see <a href="http://export.mcpbot.bspk.rs/">http://export.mcpbot.bspk.rs/</a>
     */
    public String getMappingsVersion()
    {
        return mappingsCustom == null ? "" + mappingsVersion : mappingsCustom;
    }

    /**
     * Set the MCP mappings channel and version<br>
     * The format is: {@code channel_version}.<br>
     * Examples: {@code stable_17, snapshot_20151113}
     *
     * @param mappings The channel and version
     *
     * @see <a href="http://export.mcpbot.bspk.rs/">http://export.mcpbot.bspk.rs/</a>
     */
    public void setMappings(String mappings)
    {
        if (Strings.isNullOrEmpty(mappings))
        {
            mappingsChannel = null;
            mappingsVersion = -1;

            replacer.putReplacement(Constants.REPLACE_MCP_CHANNEL, mappingsChannel);
            replacer.putReplacement(Constants.REPLACE_MCP_VERSION, getMappingsVersion());

            return;
        }

        mappings = mappings.toLowerCase();

        if (!mappings.contains("_"))
        {
            throw new IllegalArgumentException("Mappings must be in format 'channel_version' or 'custom_something'. eg: snapshot_20140910 custom_AbrarIsCool");
        }

        int index = mappings.lastIndexOf('_');
        mappingsChannel = mappings.substring(0, index);
        mappingsCustom = mappings.substring(index + 1);

        if (!mappingsCustom.equals("custom"))
        {
            try
            {
                mappingsVersion = Integer.parseInt(mappingsCustom);
                mappingsCustom = null;
            }
            catch (NumberFormatException e)
            {
                throw new GradleConfigurationException("The mappings version must be a number! eg: channel_### or channel_custom (for custom mappings).");
            }
        }

        mappingsSet = true;

        replacer.putReplacement(Constants.REPLACE_MCP_CHANNEL, mappingsChannel);
        replacer.putReplacement(Constants.REPLACE_MCP_VERSION, getMappingsVersion());

        // check
        checkMappings();
    }

    /**
     * Checks that the set mappings are valid based on the channel, version, and MC version.
     * If the mappings are invalid, this method will throw a runtime exception.
     */
    protected void checkMappings()
    {

        if (!Strings.isNullOrEmpty(version)
                && (compareVersion(version, MIN_VERSION) < 0 || compareVersion(version, MAX_VERSION) > 0)
                && !suppressVersionTest)
        {
                // version not supported
                throw new GradleConfigurationException(String.format("ForgeGradle %s does not support Minecraft %s. MIN: %s, MAX: %s",
                        forgeGradleVersion, version, MIN_VERSION, nullStringTo(MAX_VERSION, "none")));

        }

        // mappings or mc version are null
        if (mappingsChannel == null || Strings.isNullOrEmpty(version))
            return;

        // set now.
        replacer.putReplacement(Constants.REPLACE_MCP_MCVERSION, version);

        // gotta do this after setting the MC version
        if (mappingsCustom != null)
            return;

        // check if it exists
        Map<String, int[]> versionMap = mcpJson.get(version);
        String channel = getMappingsChannelNoSubtype();
        if (versionMap != null)
        {
            int[] channelList = versionMap.get(channel);
            if (channelList == null)
                throw new GradleConfigurationException("There is no such MCP mapping channel named " + channel);

            // all is well with the world
            if (searchArray(channelList, mappingsVersion))
                return;
        }

        // if it gets here.. it wasnt found. Now we try to actually find it..
        for (Entry<String, Map<String, int[]>> mcEntry : mcpJson.entrySet())
        {
            for (Entry<String, int[]> channelEntry : mcEntry.getValue().entrySet())
            {
                // found it!
                if (searchArray(channelEntry.getValue(), mappingsVersion))
                {
                    boolean rightMc = mcEntry.getKey().equals(version);
                    boolean rightChannel = channelEntry.getKey().equals(channel);

                    // right channel, but wrong mc
                    if (rightChannel && !rightMc)
                    {
                        project.getLogger().warn("This mapping '" + getMappings() + "' was designed for MC " + mcEntry.getKey() + "! Use at your own peril.");
                        replacer.putReplacement(Constants.REPLACE_MCP_MCVERSION, mcEntry.getKey()); // set MC version
                        return;
                        // throw new GradleConfigurationException("This mapping '" + getMappings() + "' exists only for MC " + mcEntry.getKey() + "!");
                    }

                    // right MC , but wrong channel
                    else if (rightMc && !rightChannel)
                    {
                        throw new GradleConfigurationException("This mapping '" + getMappings() + "' doesnt exist! perhaps you meant '" + channelEntry.getKey() + "_" + mappingsVersion + "'");
                    }
                }
            }
        }

        // wasnt found
        throw new GradleConfigurationException("The specified mapping '" + getMappings() + "' does not exist!");
    }

    @SuppressWarnings("unused")
    private static String getMappedString(String key, String value) {
        if (value == null) return "";
        return key + ": " + value;
    }

    /**
     * Compares 2 versions. Returns -1 if version1 is outdated, +1 if version2 is outdated, and 0 if they are the same.
     * @param version1
     * @param version2
     * @return
     */
    private static int compareVersion(String version1, String version2)
    {
        if (version2 == null || version2.equals(version1))
            return 0;

        int[] v1 = stringToInt(version1.split("\\."));
        int[] v2 = stringToInt(version2.split("\\."));

        // FIXME 1.9.0 > 1.9
        return Ints.lexicographicalComparator().compare(v1, v2);
    }

    private static int[] stringToInt(String[] strings)
    {
        int[] ints = new int[strings.length];
        for (int i = 0; i < ints.length; i++)
        {
            Integer v = Ints.tryParse(strings[i]);
            ints[i] = v == null ? 0 : v;
        }
        return ints;
    }

    private static String nullStringTo(String string, String to) {
        return string == null ? to : string;
    }

    private static boolean searchArray(int[] array, int key)
    {
        Arrays.sort(array);
        int foundIndex = Arrays.binarySearch(array, key);
        return foundIndex >= 0 && array[foundIndex] == key;
    }
}
