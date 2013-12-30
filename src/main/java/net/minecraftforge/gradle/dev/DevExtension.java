package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.Project;

public class DevExtension extends BaseExtension
{
    private String fmlDir;
    private String mainClass;
    private String tweakClass;
    private String installerVersion = "null";

    public DevExtension(Project project)
    {
        super(project);
    }

    public String getFmlDir()
    {
        return fmlDir == null ? project.getProjectDir().getPath().replace('\\', '/') : fmlDir.replace('\\', '/');
    }

    public void setFmlDir(String fmlDir)
    {
        this.fmlDir = fmlDir;
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

    public String getTweakClass()
    {
        return tweakClass;
    }

    public void setTweakClass(String tweakClass)
    {
        this.tweakClass = tweakClass;
    }
}
