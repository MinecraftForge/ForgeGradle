package net.minecraftforge.gradle.common;

import java.util.HashMap;
import java.util.LinkedList;

import joptsimple.internal.Strings;

import org.gradle.api.Project;

public class BaseExtension
{
    protected Project project;
    protected String version = "null";
    protected String mcpVersion = "unknown";
    protected String runDir = "run";
    protected HashMap<Object, Object> ext = new HashMap<Object, Object>();
    private LinkedList<String> srgExtra = new LinkedList<String>();
    
    protected boolean mappingsSet = false; 
    protected String mappingsChannel;
    protected String mappingsVersion;

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
    }

    public String getMcpVersion()
    {
        return mcpVersion;
    }

    public void setMcpVersion(String mcpVersion)
    {
        this.mcpVersion = mcpVersion;
    }
    
    public Object getExt(String name)
    {
        return ext.get(name);
    }

    public void setExt(Object name, Object value)
    {
        ext.put(name, value);
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
        runDir = value + "/..";
        project.getLogger().lifecycle("The assetDir is deprecated!  Use runDir instead! runDir set to "+runDir);
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
        return mappingsChannel + "_" + mappingsVersion;
    }
    
    public String getMappingsChannel()
    {
        return mappingsChannel;
    }
    
    public String getMappingsVersion()
    {
        return mappingsVersion;
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
            mappingsVersion = null;
            return;
        }
        
        if (!mappings.contains("_"))
        {
            throw new IllegalArgumentException("Mappings must be in format 'channel_version'. eg: snapshot_20140910");
        }
        
        int index = mappings.lastIndexOf('_');
        mappingsChannel = mappings.substring(0,  index);
        mappingsVersion = mappings.substring(index +1);
        mappingsSet = true;
    }
}
