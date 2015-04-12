package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.NamedDomainObjectContainer;

public class PatcherExtension extends BaseExtension
{
    private File                                       workspaceDir;
    private String                                     installerVersion = "null";
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

    public File getWorkspaceDir()
    {
        return workspaceDir;
    }

    public void setWorkspaceDir(Object workspaceDir)
    {
        this.workspaceDir = project.file(workspaceDir);
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedWorkspaceDir()
    {
        return new Closure<File>(null) {
            public File call()
            {
                return getWorkspaceDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedSubWorkspaceDir(final String path)
    {
        return new Closure<File>(null) {
            public File call()
            {
                return new File(getWorkspaceDir(), path);
            }
        };
    }
}
