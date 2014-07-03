package net.minecraftforge.gradle.tasks.user;

import com.google.common.base.Charsets;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import groovy.lang.Closure;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import net.minecraftforge.gradle.user.UserConstants;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class CreateStartTask extends CachedTask
{
    @Input
    private DelayedString assetIndex;
    @Input
    private DelayedFile assetsDir;
    @Input
    private DelayedString version;
    @Input
    private DelayedString tweaker;
    @Input
    private String theResource = getResource();

    @Cached
    @OutputFile
    private DelayedFile outputFile;

    @TaskAction
    public void doTask() throws IOException
    {
        getLogger().lifecycle("extracting resaource");
        String file = theResource;
        file = file.replace("@@MCVERSION@@", getVersion());
        file = file.replace("@@ASSETINDEX@@", getAssetIndex());
        file = file.replace("@@ASSETSDIR@@", getAssetsDir());
        file = file.replace("@@TWEAKER@@", getTweaker());

        File tempSrc = new File(getTemporaryDir(), "GradleStart.java");
        tempSrc.getParentFile().mkdirs();
        Files.write(file, tempSrc, Charsets.UTF_8);

        getLogger().lifecycle("compiling");
        getProject().exec(new Closure<ExecSpec>(this)
        {
            private static final long serialVersionUID = 4608694547855396167L;

            public ExecSpec call()
            {
                ExecSpec exec = (ExecSpec) getDelegate();

                exec.setExecutable("javac");
                exec.setWorkingDir(getTemporaryDir());

                exec.args("-classpath"); // classpath
                exec.args(getProject().getConfigurations().getByName(UserConstants.CONFIG_DEPS).getAsPath());
                exec.args("-d"); // output
                exec.args(getTemporaryDir());
                //exec.args(getOutputFile().getParentFile().getAbsolutePath());
                exec.args("GradleStart.java");

                return exec;
            }

            public ExecSpec call(Object obj)
            {
                return call();
            }
        });

        getLogger().lifecycle("copying to cache");
        Files.copy(new File(getTemporaryDir(), "GradleStart.class"), getOutputFile());
    }

    private String getResource()
    {
        try
        {
            return Resources.toString(getClass().getClassLoader().getResource("GradleStart.java"), Charsets.UTF_8);
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
            return "";
        }

    }

    public String getAssetIndex()
    {
        return assetIndex.call();
    }

    public void setAssetIndex(DelayedString assetIndex)
    {
        this.assetIndex = assetIndex;
    }

    public String getAssetsDir() throws IOException
    {
        return assetsDir.call().getCanonicalPath();
    }

    public void setAssetsDir(DelayedFile assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    public String getVersion()
    {
        return version.call();
    }

    public void setVersion(DelayedString version)
    {
        this.version = version;
    }

    public String getTweaker()
    {
        return tweaker.call();
    }

    public void setTweaker(DelayedString version)
    {
        this.tweaker = version;
    }

    public File getOutputFile()
    {
        return outputFile.call();
    }

    public void setOutputFile(DelayedFile outputFile)
    {
        this.outputFile = outputFile;
    }
}
