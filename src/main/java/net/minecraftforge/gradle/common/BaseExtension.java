/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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

import java.net.URL;
import java.util.Arrays;
import java.util.Map;

import org.gradle.api.Project;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

import gnu.trove.TIntObjectHashMap;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.delayed.ReplacementProvider;

public abstract class BaseExtension
{
    protected static final transient Map<String, String> MCP_VERSION_MAP = ImmutableMap.of("1.8", "9.10");

    public final String forgeGradleVersion;

    protected transient Project             project;
    protected transient ReplacementProvider replacer;
    protected String                        version;
    protected String                        mcpVersion = "unknown";

    // this should never be touched except by the base plugin in this package
    Map<String, TIntObjectHashMap<String>> mcpJson;
    protected boolean                      mappingsSet     = false;
    protected String                       mappingsChannel = null;
    protected int                          mappingsVersion = -1;
    // custom version for custom mappings
    protected String                mappingsCustom  = null;

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
                version = "2.0-SNAPSHOT";// fallback
            }

        }
        catch (Exception e)
        {
            // again, the fallback
            version = "2.0-SNAPSHOT";
        }

        forgeGradleVersion = version;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;

        replacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

        mcpVersion = MCP_VERSION_MAP.get(version);

        // maybe they set the mappings first
        checkMappings();
    }

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
     * @return channel_version
     */
    public String getMappings()
    {
        return mappingsChannel + "_" + (mappingsCustom == null ? mappingsVersion : mappingsCustom);
    }

    public String getMappingsChannel()
    {
        return mappingsChannel;
    }

    /**
     * Strips the _nodoc and _verbose channel subtypes from the channel name.
     * @return channel without subtype
     */
    public String getMappingsChannelNoSubtype()
    {
        int underscore = mappingsChannel.indexOf('_');
        if (underscore <= 0) // already has docs.
            return mappingsChannel;
        else
            return mappingsChannel.substring(0, underscore);
    }

    public String getMappingsVersion()
    {
        return mappingsCustom == null ? "" + mappingsVersion : mappingsCustom;
    }

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
        // mappings or mc version are null
        if (mappingsChannel == null || Strings.isNullOrEmpty(version) || mappingsCustom != null)
            return;
        
        // check if the channel exists
        String channel = getMappingsChannelNoSubtype();
        TIntObjectHashMap<String> channelMap = mcpJson.get(channel);
        if (channelMap == null)
        {
            throw new GradleConfigurationException("There is no such MCP mapping channel named " + channel);
        }
        
        System.out.println("checking -> " + getMappings());
        System.out.println("channel map -> " + channelMap);
        String mappingMc = channelMap.get(mappingsVersion);
        
        if (version.equals(mappingMc))
        {
            // all good.
            return;
        }
        else if (mappingMc == null)
        {
            throw new GradleConfigurationException("The specified mapping '" + getMappings() + "' does not exist!");
        }
        else
        {
            project.getLogger().warn("This set of MCP mappings was designed for MC "+version+". Use at your own peril.");
            return;
        }
    }
}
