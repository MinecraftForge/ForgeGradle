package net.minecraftforge.gradle.patcher;

import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.NamedDomainObjectContainer;

public class PatcherExtension extends BaseExtension
{
    private Object                                     versionJson;
    private Object                                     workspaceDir;
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
    
    public File getVersionJson()
    {
        if (versionJson == null)
            return null;
        
        return (File) (versionJson = project.file(versionJson));
    }

    public void setversionJson(Object versionJson)
    {
        this.versionJson = versionJson;
    }

    public File getWorkspaceDir()
    {
        if (workspaceDir == null)
        {
            return null;
        }
        
        return (File) (workspaceDir = project.file(workspaceDir));
    }

    public void setWorkspaceDir(Object workspaceDir)
    {
        this.workspaceDir = workspaceDir;
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
