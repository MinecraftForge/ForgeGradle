package net.minecraftforge.gradle.common;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.GradleConfigurationException;

import org.gradle.api.Project;

import com.google.common.base.Strings;

public class BaseExtension
{
    protected transient Project               project;
    protected String                          version         = "null";
    protected String                          mcpVersion      = "unknown";
    protected String                          runDir          = "run";
    private LinkedList<String>                srgExtra        = new LinkedList<String>();

    protected Map<String, Map<String, int[]>> mcpJson;
    protected boolean                         mappingsSet     = false;
    protected String                          mappingsChannel = null;
    protected int                             mappingsVersion = -1;
    protected String                          customVersion   = null;

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

    @Deprecated
    public void setAssetDir(String value)
    {
        setRunDir(value + "/..");
        project.getLogger().warn("The assetDir is deprecated!  I actually just did all this generalizing stuff just now.. Use runDir instead! runDir set to " + runDir);
        project.getLogger().warn("The runDir should be the location where you want MC to be run, usually he parent of the asset dir");
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

    public String getMappings()
    {
        return mappingsChannel + "_" + (customVersion == null ? mappingsVersion : customVersion);
    }

    public String getMappingsChannel()
    {
        return mappingsChannel;
    }

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
        return customVersion == null ? ""+mappingsVersion : customVersion;
    }

    public boolean mappingsSet()
    {
        return mappingsSet;
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
            throw new IllegalArgumentException("Mappings must be in format 'channel_version'. eg: snapshot_20140910");
        }

        int index = mappings.lastIndexOf('_');
        mappingsChannel = mappings.substring(0, index);
        customVersion = mappings.substring(index + 1);
        
        if (!customVersion.equals("custom"))
        {
            try
            {
                mappingsVersion = Integer.parseInt(customVersion);
                customVersion = null;
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
        if (!mappingsSet || "null".equals(version) || Strings.isNullOrEmpty(version) || customVersion != null)
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
