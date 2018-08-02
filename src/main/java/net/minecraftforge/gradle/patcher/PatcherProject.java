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

import static net.minecraftforge.gradle.patcher.PatcherConstants.DEFAULT_RES_DIR;
import static net.minecraftforge.gradle.patcher.PatcherConstants.DEFAULT_SRC_DIR;
import static net.minecraftforge.gradle.patcher.PatcherConstants.DEFAULT_TEST_RES_DIR;
import static net.minecraftforge.gradle.patcher.PatcherConstants.DEFAULT_TEST_SRC_DIR;
import groovy.lang.Closure;

import java.io.File;
import java.io.Serializable;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.Project;

import com.google.common.base.Strings;

public class PatcherProject implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final transient Project project;

    private final String name;
    private final String capName;
    private String patchAfter = "clean";
    private String genPatchesFrom;

    private File rootDir;
    private File patchDir;
    private File sourcesDir;
    private File resourcesDir;
    private File testSourcesDir;
    private File testResourcesDir;

    private String mainClassClient = "GradleStart";
    private String mainClassServer = "GradleStartServer";
    private String tweakClassClient = "";
    private String tweakClassServer = "";
    private String runArgsClient = "";
    private String runArgsServer = "";
    
    private String patchPrefixOriginal = "";
    private String patchPrefixChanged = "";
    
    private boolean genMcpPatches = false;
    private boolean applyMcpPatches = false;

    protected PatcherProject(String name, PatcherPlugin plugin)
    {
        this.name = name;
        this.project = plugin.project;
        rootDir = project.getProjectDir();
        
        // make capName
        char char1 = name.charAt(0);
        capName = Character.toUpperCase(char1) + name.substring(1);
    }

    public String getName()
    {
        return name;
    }
    
    public String getCapName()
    {
        return capName;
    }

    /**
     * @return NULL if only applies to clean
     */
    public String getPatchAfter()
    {
        return patchAfter;
    }

    /**
     * Sets the project after which this project will apply its patches.
     * All patches apply on top of the clean project anyways.
     * @param patchAfter project to patch after
     */
    public void setPatchAfter(String patchAfter)
    {
        if (Strings.isNullOrEmpty(patchAfter))
            this.patchAfter = "clean";
        else
            this.patchAfter = patchAfter;
    }

    /**
     * Sets the project after which this project will apply its patches
     * All patches apply on top of the clean project anyways.
     * @param patchAfter project to patch after
     */
    public void patchAfter(String patchAfter)
    {
        setPatchAfter(patchAfter);
    }

    /**
     * Sets the project after which this project will apply its patches
     * All patches apply on top of the clean project anyways.
     * @param patcher project to patch after
     */
    public void setPatchAfter(PatcherProject patcher)
    {
        this.patchAfter = patcher.getName();
    }

    /**
     * Sets the project after which this project will apply its patches
     * All patches apply on top of the clean project anyways.
     * @param patcher project to patch after
     */
    public void patchAfter(PatcherProject patcher)
    {
        setPatchAfter(patcher);
    }

    /**
     * @return NULL to not generate patches, "clean" to generate from the clean project.
     */
    public String getGenPatchesFrom()
    {
        return genPatchesFrom;
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param generateFrom Project to generate patches from
     */
    public void setGenPatchesFrom(String generateFrom)
    {
        this.genPatchesFrom = generateFrom;
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param patcher Project to generate patches from
     */
    public void setGenPatchesFrom(PatcherProject patcher)
    {
        this.genPatchesFrom = patcher.getName();
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param generateFrom Project to generate patches from
     */
    public void genPatchesFrom(String generateFrom)
    {
        setGenPatchesFrom(generateFrom);
    }
    
    protected boolean doesGenPatches()
    {
        return genPatchesFrom != null;
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param patcher Project to generate patches from
     */
    public void generateFrom(PatcherProject patcher)
    {
        setGenPatchesFrom(patcher);
    }

    public File getRootDir()
    {
        return rootDir;
    }

    /**
     * The root directory of the project. This may or may not be actually used depending on the other directories.
     * @param rootDir root directory of the project
     */
    public void setRootDir(Object rootDir)
    {
        this.rootDir = project.file(rootDir);
    }
    
    /**
     * The root directory of the project. This may or may not be actually used depending on the other directories.
     * @param rootDir root directory of the project
     */
    public void rootDir(Object rootDir)
    {
        setRootDir(rootDir);
    }

    public File getPatchDir()
    {
        return patchDir;
    }

    /**
     * The directory where the patches are found, and to whitch generated patches should be saved.
     * By default this is rootDir/patches
     * @param patchDir patch directory of the project
     */
    public void setPatchDir(Object patchDir)
    {
        this.patchDir = project.file(patchDir);
    }
    
    /**
     * The directory where the patches are found, and to witch generated patches should be saved.
     * By default this is rootDir/patches
     * @param patchDir patch directory of the project
     */
    public void patchDir(Object patchDir)
    {
        setPatchDir(patchDir);
    }

    public File getSourcesDir()
    {
        return getFile(sourcesDir, DEFAULT_SRC_DIR);
    }

    /**
     * The directory where the non-patch sources for this project are.
     * By default this is rootDir/src/main/java
     * @param sourcesDir non-MC source directory of the project
     */
    public void setSourcesDir(Object sourcesDir)
    {
        this.sourcesDir = project.file(sourcesDir);
    }
    
    /**
     * The directory where the non-patch sources for this project are.
     * By default this is rootDir/src/main/java
     * @param sourcesDir non-MC source directory of the project
     */
    public void sourcesDir(Object sourcesDir)
    {
        setSourcesDir(sourcesDir);
    }

    public File getResourcesDir()
    {
        return getFile(resourcesDir, DEFAULT_RES_DIR);
    }

    /**
     * The directory where the non-patch resources for this project are.
     * By default this is rootDir/src/main/resources
     * @param resourcesDir non-MC resource directory of the project
     */
    public void setResourcesDir(Object resourcesDir)
    {
        this.resourcesDir = project.file(resourcesDir);
    }
    
    /**
     * The directory where the non-patch resources for this project are.
     * By default this is rootDir/src/main/resources
     * @param resourcesDir non-MC resource directory of the project
     */
    public void resourcesDir(Object resourcesDir)
    {
        setResourcesDir(resourcesDir);
    }
    
    public File getTestSourcesDir()
    {
        return getFile(testSourcesDir,DEFAULT_TEST_SRC_DIR);
    }

    /**
     * The directory where the test sourcess for this project are.
     * By default this is rootDir/src/test/sources
     * @param testSourcesDir test source directory of the project
     */
    public void setTestSourcesDir(Object testSourcesDir)
    {
        this.testSourcesDir = project.file(testSourcesDir);
    }
    
    /**
     * The directory where the test sourcess for this project are.
     * By default this is rootDir/src/test/sources
     * @param testSourcesDir test source directory of the project
     */
    public void testSourcesDir(Object testSourcesDir)
    {
        setSourcesDir(testSourcesDir);
    }

    public File getTestResourcesDir()
    {
        return getFile(testResourcesDir, DEFAULT_TEST_RES_DIR);
    }

    /**
     * The directory where the non-patch resources for this project are.
     * By default this is rootDir/src/test/resources
     * @param testResourcesDir test resource directory of the project
     */
    public void setTestResourcesDir(Object testResourcesDir)
    {
        this.testResourcesDir = project.file(testResourcesDir);
    }
    
    /**
     * The directory where the non-patch resources for this project are.
     * By default this is rootDir/src/test/resources
     * @param testResourcesDir test resource directory of the project
     */
    public void testResourcesDir(Object testResourcesDir)
    {
        setTestResourcesDir(testResourcesDir);
    }

    public String getMainClassClient()
    {
        return mainClassClient;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param mainClass main class name
     */
    public void setMainClassClient(Object mainClass)
    {
        this.mainClassClient = Constants.resolveString(mainClass);
    }
    
    public void mainClassClient(Object mainClass)
    {
        setMainClassClient(mainClass);
    }

    public String getTweakClassClient()
    {
        return tweakClassClient;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param tweakClass tweaker class name
     */
    public void setTweakClassClient(Object tweakClass)
    {
        this.tweakClassClient = Constants.resolveString(tweakClass);
    }
    
    public void tweakClassClient(Object mainClass)
    {
        setTweakClassClient(mainClass);
    }
    
    public String getRunArgsClient()
    {
        return runArgsClient;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param runArgs arguments
     */
    public void setRunArgsClient(Object runArgs)
    {
        this.runArgsClient = Constants.resolveString(runArgs);
    }
    
    public void runArgsClient(Object mainClass)
    {
        setRunArgsClient(mainClass);
    }
    
    public String getMainClassServer()
    {
        return mainClassServer;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param mainClass main class name
     */
    public void setMainClassServer(Object mainClass)
    {
        this.mainClassServer = Constants.resolveString(mainClass);
    }
    
    public void mainClassServer(Object mainClass)
    {
        setMainClassServer(mainClass);
    }

    public String getTweakClassServer()
    {
        return tweakClassServer;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param tweakClass tweaker class name
     */
    public void setTweakClassServer(Object tweakClass)
    {
        this.tweakClassServer = Constants.resolveString(tweakClass);
    }
    
    public void tweakClassServer(Object mainClass)
    {
        setTweakClassServer(mainClass);
    }
    
    public String getRunArgsServer()
    {
        return runArgsServer;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param runArgs arguments
     */
    public void setRunArgsServer(Object runArgs)
    {
        this.runArgsServer = Constants.resolveString(runArgs);
    }
    
    public void runArgsServer(Object mainClass)
    {
        setRunArgsServer(mainClass);
    }
    
    public String getPatchPrefixOriginal()
    {
        return patchPrefixOriginal;
    }

    /**
     * The path prefix of the "original" path in the patch files.
     * @param patchPrefixOriginal prefix
     */
    public void setPatchPrefixOriginal(Object patchPrefixOriginal)
    {
        this.patchPrefixOriginal = Constants.resolveString(patchPrefixOriginal);
    }
    
    public void patchPrefixOriginal(Object patchPrefixOriginal)
    {
        setPatchPrefixOriginal(patchPrefixOriginal);
    }
    
    public String getPatchPrefixChanged()
    {
        return patchPrefixChanged;
    }

    /**
     * The path prefix of the "changed" path in the patch files.
     * @param patchPrefixChanged prefix
     */
    public void setPatchPrefixChanged(Object patchPrefixChanged)
    {
        this.patchPrefixChanged = Constants.resolveString(patchPrefixChanged);
    }
    
    public void patchPrefixChanged(Object patchPrefixChanged)
    {
        setPatchPrefixChanged(patchPrefixChanged);
    }
    
    // ------------------------
    // HELPERS
    // ------------------------
    
    /**
     * Validates the object to ensure its been configured correctly and isnt missing something.
     */
    protected void validate()
    {
        if (rootDir == null && patchDir == null)
            throw new GradleConfigurationException("PatchDir not specified for project '"+ name +"'");
    }
    
    private File getFile(File field, String defaultPath)
    {
        if (field == null && rootDir != null)
            return new File(getRootDir(), defaultPath);
        else
            return ((File) field);
    }

    public boolean isGenMcpPatches()
    {
        return genMcpPatches;
    }

    public void setGenMcpPatches(boolean genMcpPatches)
    {
        this.genMcpPatches = genMcpPatches;
    }

    public boolean isApplyMcpPatches()
    {
        return applyMcpPatches;
    }

    public void setApplyMcpPatches(boolean applyMcpPatches)
    {
        this.applyMcpPatches = applyMcpPatches;
    }
    
    // ------------------------
    // DELAYED GETTERS
    // ------------------------
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedMainClassClient()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getMainClassClient();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedTweakClassClient()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getTweakClassClient();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedRunArgsClient()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getRunArgsClient();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedMainClassServer()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getMainClassServer();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedTweakClassServer()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getTweakClassServer();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<String> getDelayedRunArgsServer()
    {
        return new Closure<String>(PatcherProject.class) {
            public String call()
            {
                return getRunArgsServer();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedSourcesDir()
    {
        return new Closure<File>(PatcherProject.class) {
            public File call()
            {
                return getSourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedResourcesDir()
    {
        return new Closure<File>(PatcherProject.class) {
            public File call()
            {
                return getResourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedTestSourcesDir()
    {
        return new Closure<File>(PatcherProject.class) {
            public File call()
            {
                return getTestSourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedTestResourcesDir()
    {
        return new Closure<File>(PatcherProject.class) {
            public File call()
            {
                return getTestResourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedPatchDir()
    {
        return new Closure<File>(PatcherProject.class) {
            public File call()
            {
                return getPatchDir();
            }
        };
    }
}
