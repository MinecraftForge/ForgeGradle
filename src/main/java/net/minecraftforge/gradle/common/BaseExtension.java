package net.minecraftforge.gradle.common;

import org.gradle.api.Project;

public class BaseExtension
{
    protected Project project;
    protected String version = "null";
    protected String mcpVersion = "unknown";
    protected String assetDir = "eclipse/assets";

    public BaseExtension(Project project)
    {
        this.project = project;
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

    public void setAorkDir(String value)
    {
        this.assetDir = value;
    }

    public String getAssetDir()
    {
        return this.assetDir;
    }
}
