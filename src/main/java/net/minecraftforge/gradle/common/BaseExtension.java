package net.minecraftforge.gradle.common;

import org.gradle.api.Project;

public class BaseExtension
{
    protected Project project;
    protected String version = "null";
    protected String mcpVersion = "unknown";

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
}
