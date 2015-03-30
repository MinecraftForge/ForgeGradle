package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.NamedDomainObjectContainer;

public class PatcherExtension extends BaseExtension
{
    private String installerVersion = "null";
    private NamedDomainObjectContainer<PatcherProject> projectContainer;

    public PatcherExtension(PatcherPlugin plugin)
    {
        super(plugin);
    }

    public String getInstallerVersion()
    {
        return installerVersion;
    }

    public void setInstallerVersion(String installerVersion)
    {
        this.installerVersion = installerVersion;
    }

    public NamedDomainObjectContainer<PatcherProject> getProjects()
    {
        return projectContainer;
    }

    void setProjectContainer(NamedDomainObjectContainer<PatcherProject> projectContainer)
    {
        this.projectContainer = projectContainer;
    }

    @SuppressWarnings("rawtypes")
    public void projects(Closure closure)
    {
        projectContainer.configure(closure);
    }
}
