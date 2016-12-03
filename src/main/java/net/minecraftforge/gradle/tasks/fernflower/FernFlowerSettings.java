package net.minecraftforge.gradle.tasks.fernflower;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class FernFlowerSettings implements Serializable {

    private static final long serialVersionUID = -7610967091668947017L;

    private final File cacheDirectory;
    private final File jarFrom;
    private final File jarTo;
    private final File taskLogFile;
    private final Set<File> classpath;
    // note: while this field is String->Object, realistically only Strings
    // should be entered.
    private final Map<String, Object> mapOptions;

    public FernFlowerSettings(File cacheDirectory, File jarFrom, File jarTo, File taskLogFile, Set<File> classpath, Map<String, Object> mapOptions)
    {
        this.cacheDirectory = cacheDirectory;
        this.jarFrom = jarFrom;
        this.jarTo = jarTo;
        this.taskLogFile = taskLogFile;
        this.classpath = classpath;
        this.mapOptions = mapOptions;
    }

    public File getCacheDirectory()
    {
        return cacheDirectory;
    }

    public File getJarFrom()
    {
        return jarFrom;
    }

    public File getJarTo()
    {
        return jarTo;
    }

    public File getTaskLogFile()
    {
        return taskLogFile;
    }

    public Set<File> getClasspath()
    {
        return classpath;
    }

    public Map<String, Object> getMapOptions()
    {
        return mapOptions;
    }

    @Override
    public String toString()
    {
        return "FernFlowerSettings[cacheDirectory=" + cacheDirectory + ",jarFrom=" + jarFrom + ",jarTo=" + jarTo + ",taskLogFile=" + taskLogFile + ",classpath=" + classpath + ",mapOptions=" + mapOptions + "]";
    }

}
