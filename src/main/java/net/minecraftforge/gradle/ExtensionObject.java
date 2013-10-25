package net.minecraftforge.gradle;

import org.gradle.api.Project;

public class ExtensionObject
{
    private Project project;
    
    private String fmlDir;
    private String version = "null";
    private String mainClass;
    private String installerVersion = "null";

    public ExtensionObject(Project project)
    {
        this.project = project;
    }

    public String getFmlDir()
    {
        return fmlDir == null ? project.getProjectDir().getPath() : fmlDir;
    }

    public void setFmlDir(String fmlDir)
    {
        this.fmlDir = fmlDir;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getMainClass()
    {
        return mainClass == null ? "" : mainClass;
    }

    public void setMainClass(String mainClass)
    {
        this.mainClass = mainClass;
    }

    public String getInstallerVersion()
    {
        return installerVersion;
    }

    public void setInstallerVersion(String installerVersion)
    {
        this.installerVersion = installerVersion;
    }
}
