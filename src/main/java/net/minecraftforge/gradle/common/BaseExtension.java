package net.minecraftforge.gradle.common;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import joptsimple.internal.Strings;
import net.minecraftforge.gradle.GradleConfigurationException;

import org.gradle.api.Project;

import com.google.common.collect.ImmutableMap;

public class BaseExtension
{
    protected static final transient Map<String, String> MCP_VERSION_MAP = ImmutableMap.of("1.8", "9.10");
    
    protected transient Project               project;
    protected String                          version         = "null";
    protected String                          mcpVersion      = "unknown";
    protected String                          runDir          = "run";
    private LinkedList<String>                srgExtra        = new LinkedList<String>();

    protected Map<String, Map<String, int[]>> mcpJson;
    protected boolean                         mappingsSet     = false;
    protected String                          mappingsChannel = null;
    protected int                             mappingsVersion = -1;
    // custom version for custom mappings
    protected String                          mappingsCustom   = null;

    public BaseExtension(BasePlugin<? extends BaseExtension> plugin)
    {
        this.project = plugin.project;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
        
        mcpVersion = MCP_VERSION_MAP.get(version);
        if (mcpVersion == null)
            mcpVersion = "unknown";

        // maybe they set the mappings first
        checkMappings();
    }

    public String getMcpVersion()
    {
        return mcpVersion;
    }

    public void setMcpVersion(String mcpVersion)
    {
        this.mcpVersion = mcpVersion;
    }

    public void setRunDir(String value)
    {
        this.runDir = value;
    }

    public String getRunDir()
    {
        return this.runDir;
    }

    public LinkedList<String> getSrgExtra()
    {
        return srgExtra;
    }

    public void srgExtra(String in)
    {
        srgExtra.add(in);
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
        return mappingsCustom == null ? ""+mappingsVersion : mappingsCustom;
    }

    public void setMappings(String mappings)
    {
        if (Strings.isNullOrEmpty(mappings))
        {
            mappingsChannel = null;
            mappingsVersion = -1;
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
        if (mappingsChannel == null || "null".equals(version) || Strings.isNullOrEmpty(version) || mappingsCustom != null)
            return;

        // check if it exists
        Map<String, int[]> versionMap = mcpJson.get(version);
        if (versionMap == null)
            throw new GradleConfigurationException("There are no mappings for MC " + version);

        String channel = getMappingsChannelNoSubtype();
        int[] channelList = versionMap.get(channel);
        if (channelList == null)
            throw new GradleConfigurationException("There is no such MCP mapping channel named " + channel);

        // all is well with the world
        if (searchArray(channelList, mappingsVersion))
            return;

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
                        throw new GradleConfigurationException("This mapping '" + getMappings() + "' exists only for MC " + mcEntry.getKey() + "!");
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

    private static boolean searchArray(int[] array, int key)
    {
        Arrays.sort(array);
        int foundIndex = Arrays.binarySearch(array, key);
        return foundIndex >= 0 && array[foundIndex] == key;
    }
}
