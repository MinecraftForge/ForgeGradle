package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.dev.PatcherConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.Serializable;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.Project;

public class PatcherProject implements Serializable
{
    private static final long serialVersionUID = 1L;

    private final transient Project project;

    private final String name;
    private String patchAfter;
    private String generateFrom = "clean";

    private File rootDir;
    private File patchDir;
    private File sourcesDir;
    private File resourcesDir;
    private File testSourcesDir;
    private File testResourcesDir;

    // TODO: do something about these.. are they even needed???
    private String mainClass;
    private String tweakClass;

    protected PatcherProject(String name, PatcherPlugin plugin)
    {
        this.name = name;
        this.project = plugin.project;
        rootDir = project.getProjectDir();
    }

    public String getName()
    {
        return name;
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
    public String getGenerateFrom()
    {
        return generateFrom;
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param generateFrom
     */
    public void setGenerateFrom(String generateFrom)
    {
        this.generateFrom = generateFrom;
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param patcher
     */
    public void setGenerateFrom(PatcherProject patcher)
    {
        this.generateFrom = patcher.getName();
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param generateFrom
     */
    public void generateFrom(String generateFrom)
    {
        setGenerateFrom(generateFrom);
    }

    /**
     * The project from witch the patches for this project will be generated.
     * By default, patches are not generated at all.
     * To generate patches against the "clean" project, specify "clean" ast the argument.
     * @param patcher
     */
    public void generateFrom(PatcherProject patcher)
    {
        setGenerateFrom(patcher);
    }

    public File getRootDir()
    {
        return rootDir;
    }

    /**
     * The root directory of the project. This may or may not be actually used depending on the other directories.
     * @param rootDir
     */
    public void setRootDir(Object rootDir)
    {
        this.rootDir = project.file(rootDir);
    }
    
    /**
     * The root directory of the project. This may or may not be actually used depending on the other directories.
     * @param rootDir
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
     * The directory where the patches are found, and to witch generated patches should be saved.
     * By defualt this is rootDir/patches
     * @param patchDir
     */
    public void setPatchDir(Object patchDir)
    {
        this.patchDir = project.file(patchDir);
    }
    
    /**
     * The directory where the patches are found, and to witch generated patches should be saved.
     * By defualt this is rootDir/patches
     * @param patchDir
     */
    public void patchDir(Object patchDir)
    {
        setPatchDir(patchDir);
    }

    public File getSourcesDir()
    {
        return getFile(sourcesDir, DEFAULT_SOURCES_DIR);
    }

    /**
     * The directory where the non-patch sources for this project are.
     * By defualt this is rootDir/src/main/java
     * @param sourcesDir
     */
    public void setSourcesDir(Object sourcesDir)
    {
        this.sourcesDir = project.file(sourcesDir);
    }
    
    /**
     * The directory where the non-patch sources for this project are.
     * By defualt this is rootDir/src/main/java
     * @param sourcesDir
     */
    public void sourcesDir(Object sourcesDir)
    {
        setSourcesDir(sourcesDir);
    }

    public File getResourcesDir()
    {
        return getFile(resourcesDir, DEFAULT_RESOURCES_DIR);
    }

    /**
     * The directory where the non-patch resources for this project are.
     * By defualt this is rootDir/src/main/resources
     * @param resourcesDir
     */
    public void setResourcesDir(Object resourcesDir)
    {
        this.resourcesDir = project.file(resourcesDir);
    }
    
    /**
     * The directory where the non-patch resources for this project are.
     * By defualt this is rootDir/src/main/resources
     * @param resourcesDir
     */
    public void resourcesDir(Object resourcesDir)
    {
        setResourcesDir(resourcesDir);
    }
    
    public File getTestSourcesDir()
    {
        return getFile(testSourcesDir,DEFAULT_TEST_SOURCES_DIR);
    }

    /**
     * The directory where the test sourcess for this project are.
     * By defualt this is rootDir/src/test/sources
     * @param testSourcesDir
     */
    public void setTestSourcesDir(Object testSourcesDir)
    {
        this.testSourcesDir = project.file(testSourcesDir);
    }
    
    /**
     * The directory where the test sourcess for this project are.
     * By defualt this is rootDir/src/test/sources
     * @param testSourcesDir
     */
    public void testSourcesDir(Object testSourcesDir)
    {
        setSourcesDir(testSourcesDir);
    }

    public File getTestResourcesDir()
    {
        return getFile(testResourcesDir, DEFAULT_TEST_RESOURCES_DIR);
    }

    /**
     * The directory where the non-patch resources for this project are.
     * By defualt this is rootDir/src/test/resources
     * @param testResources
     */
    public void setTestResourcesDir(Object testResourcesDir)
    {
        this.testResourcesDir = project.file(testResourcesDir);
    }
    
    /**
     * The directory where the non-patch resources for this project are.
     * By defualt this is rootDir/src/test/resources
     * @param testResources
     */
    public void testResourcesDir(Object testResourcesDir)
    {
        setTestResourcesDir(testResourcesDir);
    }

    public String getMainClass()
    {
        return mainClass;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param mainClass
     */
    public void setMainClass(Object mainClass)
    {
        this.mainClass = Constants.resolveString(mainClass);
    }
    
    public void mainClass(Object mainClass)
    {
        setMainClass(mainClass);
    }

    public String getTweakClass()
    {
        return tweakClass;
    }

    /**
     * This is used for the run configs and the manifest of the universal jar.
     * @param tweakClass
     */
    public void setTweakClass(Object tweakClass)
    {
        this.tweakClass = Constants.resolveString(tweakClass);
    }
    
    public void tweakClass(Object mainClass)
    {
        setTweakClass(mainClass);
    }
    
    private File getFile(File field, String defaultPath)
    {
        if (field == null && rootDir != null)
            return new File(getRootDir(), defaultPath);
        else
            return ((File) field);
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedSourcesDir()
    {
        return new Closure<File>(project, this) {
            public File call()
            {
                return getSourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedResourcesDir()
    {
        return new Closure<File>(project, this) {
            public File call()
            {
                return getResourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedTestSourcesDir()
    {
        return new Closure<File>(project, this) {
            public File call()
            {
                return getTestSourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedTestResourcesDir()
    {
        return new Closure<File>(project, this) {
            public File call()
            {
                return getTestResourcesDir();
            }
        };
    }
    
    @SuppressWarnings("serial")
    protected Closure<File> getDelayedPatchesDir()
    {
        return new Closure<File>(project, this) {
            public File call()
            {
                return getPatchDir();
            }
        };
    }
}
