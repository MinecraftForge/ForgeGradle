package net.minecraftforge.gradle.common;

import java.util.LinkedList;

import org.gradle.api.Project;

public class BaseExtension
{
    protected Project project;
    protected String version = "null";
    protected String mcpVersion = "unknown";
    protected String runDir = "run";
    private LinkedList<String> srgExtra = new LinkedList<String>();

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
}
