package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.ClosureBackedAction;

public class DevExtension extends BaseExtension
{
    private String fmlDir;
    private String forgeDir;
    private String bukkitDir;
    private String mainClass;
    private String tweakClass;
    private boolean makeJavadoc = true;
    private String installerVersion = "null";
    private Action<Project> subprojects = null;
    private Action<Project> cleanProject = null;
    private Action<Project> dirtyProject = null;

    public DevExtension(DevBasePlugin plugin)
    {
        super(plugin);
    }

    public String getFmlDir()
    {
        return fmlDir == null ? project.getProjectDir().getPath().replace('\\', '/') : fmlDir.replace('\\', '/');
    }

    public void setFmlDir(String fmlDir)
    {
        this.fmlDir = fmlDir;
    }
    
    public String getForgeDir()
    {
        return forgeDir == null ? project.getProjectDir().getPath().replace('\\', '/') : forgeDir.replace('\\', '/');
    }

    public void setForgeDir(String forgeDir)
    {
        this.forgeDir = forgeDir;
    }
    
    public String getBukkitDir()
    {
        return bukkitDir == null ? project.getProjectDir().getPath().replace('\\', '/') : bukkitDir.replace('\\', '/');
    }

    public void setBukkitDir(String bukkitDir)
    {
        this.bukkitDir = bukkitDir;
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
        return tweakClass == null ? "" : tweakClass;
    }

    public void setTweakClass(String tweakClass)
    {
        this.tweakClass = tweakClass;
    }

    public Action<Project> getSubprojects()
    {
        return subprojects;
    }

    public void setSubprojects(Action<Project> subprojects)
    {
        this.subprojects = subprojects;
    }
    
    @SuppressWarnings("rawtypes")
    public void subprojects(Closure subprojects)
    {
        this.subprojects = new ClosureBackedAction<Project>(subprojects);
    }

    public Action<Project> getCleanProject()
    {
        return cleanProject;
    }

    public void setCleanProject(Action<Project> cleanProject)
    {
        this.cleanProject = cleanProject;
    }
    
    @SuppressWarnings("rawtypes")
    public void cleanProject(Closure subprojects)
    {
        this.cleanProject = new ClosureBackedAction<Project>(subprojects);
    }

    public Action<Project> getDirtyProject()
    {
        return dirtyProject;
    }

    public void setDirtyProject(Action<Project> dirtyProject)
    {
        this.dirtyProject = dirtyProject;
    }
    
    @SuppressWarnings("rawtypes")
    public void dirtyProject(Closure subprojects)
    {
        this.dirtyProject = new ClosureBackedAction<Project>(subprojects);
    }

    public boolean getMakeJavadoc()
    {
        return makeJavadoc;
    }

    public void setMakeJavadoc(boolean makeJavadoc)
    {
        this.makeJavadoc = makeJavadoc;
    }
}
