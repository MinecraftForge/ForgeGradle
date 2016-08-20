/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.patcher;

import java.io.File;

import org.gradle.api.NamedDomainObjectContainer;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;

public class PatcherExtension extends BaseExtension
{
    private Object                                     versionJson;
    private Object                                     workspaceDir;
    private String                                     installerVersion = "null";
    private NamedDomainObjectContainer<PatcherProject> projectContainer;
    private boolean                                    buildUserdev     = false;
    private boolean                                    buildInstaller   = false;

    public boolean isBuildUserdev()
    {
        return buildUserdev;
    }

    public void setBuildUserdev(boolean buildUserdev)
    {
        this.buildUserdev = buildUserdev;
    }

    public boolean isBuildInstaller()
    {
        return buildInstaller;
    }

    public void setBuildInstaller(boolean buildInstaller)
    {
        this.buildInstaller = buildInstaller;
    }

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
        replacer.putReplacement(PatcherConstants.REPLACE_INSTALLER, installerVersion);
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
    
    @SuppressWarnings("rawtypes")
    public void project(String projName, Closure closure)
    {
        project.configure(projectContainer.maybeCreate(projName), closure);
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
        return new Closure<File>(PatcherExtension.class) {
            public File call()
            {
                return getWorkspaceDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedSubWorkspaceDir(final String path)
    {
        return new Closure<File>(PatcherExtension.class) {
            public File call()
            {
                return new File(getWorkspaceDir(), path);
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedVersionJson()
    {
        return new Closure<File>(PatcherExtension.class) {
            public File call()
            {
                return getVersionJson();
            }
        };
    }
}
