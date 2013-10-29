package net.minecraftforge.gradle.common;

import org.gradle.api.Project;

public class BaseExtension
{
    protected Project project;
    private String version = "null";
    
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
}
