package net.minecraftforge.gradle.tasks;

import com.cloudbees.diff.Diff;
import com.cloudbees.diff.Hunk;
import com.cloudbees.diff.PatchException;
import com.google.common.io.Files;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

public class GeneratePatches extends DefaultTask
{
    @InputDirectory
    DelayedFile patchDir;

    @InputDirectory
    DelayedFile changedDir;

    @InputDirectory
    DelayedFile originalDir;

    @Input
    String originalPrefix = "";
    
    @Input
    String changedPrefix = "";

    @TaskAction
    public void doTask() throws IOException, PatchException
    {
        getPatchDir().mkdirs();

        // fix and create patches.
        processDir(getOriginalDir());
    }

    public void processDir(File dir) throws IOException
    {
        for (File file : dir.listFiles())
        {
            if (file.isDirectory())
            {
                processDir(file);
            }
            else if (file.getPath().endsWith(".java"))
            {
                processFile(file);
            }
        }
    }

    public void processFile(File file) throws IOException
    {
        getLogger().debug("Original File: " + file);
        String relative = file.getAbsolutePath().substring(getOriginalDir().getAbsolutePath().length()).replace('\\', '/');

        File patchFile = new File(getPatchDir(), relative + ".patch");
        File changedFile = new File(getChangedDir(), relative);

        getLogger().debug("Changed File: " + changedFile);

        if (!changedFile.exists())
        {
            getLogger().debug("Changed File does not exist");
            return;
        }

        Diff diff = Diff.diff(file, changedFile, false);

        if (!diff.isEmpty())
        {
            String unidiff = diff.toUnifiedDiff(originalPrefix + relative, changedPrefix + relative, Files.newReader(file, Charset.defaultCharset()), Files.newReader(changedFile, Charset.defaultCharset()), 3);
            unidiff = unidiff.replace("\r\n", "\n"); //Normalize lines
            unidiff = unidiff.replace("\n" + Hunk.ENDING_NEWLINE + "\n", "\n"); //We give 0 shits about this.

            String olddiff = "";
            if (patchFile.exists())
            {
                olddiff = Files.toString(patchFile, Charset.defaultCharset());
            }

            if (!olddiff.equals(unidiff))
            {
                getLogger().debug("Writing patch: " + patchFile);
                patchFile.getParentFile().mkdirs();
                Files.touch(patchFile);
                Files.write(unidiff, patchFile, Charset.defaultCharset());
            }
            else
            {
                getLogger().debug("Patch did not change");
            }
        }
    }

    public File getChangedDir()
    {
        return changedDir.call();
    }

    public void setChangedDir(DelayedFile changedDir)
    {
        this.changedDir = changedDir;
    }

    public File getOriginalDir()
    {
        return originalDir.call();
    }

    public void setOriginalDir(DelayedFile originalDir)
    {
        this.originalDir = originalDir;
    }

    public File getPatchDir()
    {
        return patchDir.call();
    }

    public void setPatchDir(DelayedFile patchDir)
    {
        this.patchDir = patchDir;
    }

    public String getOriginalPrefix()
    {
        return originalPrefix;
    }

    public void setOriginalPrefix(String originalPrefix)
    {
        this.originalPrefix = originalPrefix;
    }

    public String getChangedPrefix()
    {
        return changedPrefix;
    }

    public void setChangedPrefix(String changedPrefix)
    {
        this.changedPrefix = changedPrefix;
    }
}
