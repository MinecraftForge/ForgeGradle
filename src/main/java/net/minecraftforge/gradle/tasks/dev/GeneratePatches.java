package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.cloudbees.diff.Diff;
import com.cloudbees.diff.Hunk;
import com.cloudbees.diff.PatchException;
import com.google.common.base.Charsets;
import com.google.common.io.Files;


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

    private Set<File> created = new HashSet<File>();

    @TaskAction
    public void doTask() throws IOException, PatchException
    {
        created.clear();
        getPatchDir().mkdirs();

        // fix and create patches.
        processDir(getOriginalDir());
        
        removeOld(getPatchDir());
    }

    private void removeOld(File dir) throws IOException
    {
        final ArrayList<File> directories = new ArrayList<File>();
        FileTree tree = getProject().fileTree(dir);

        tree.visit(new FileVisitor()
        {
            @Override
            public void visitDir(FileVisitDetails dir)
            {
                directories.add(dir.getFile());
            }

            @Override
            public void visitFile(FileVisitDetails f)
            {
                File file = f.getFile();
                if (!created.contains(file))
                {
                    getLogger().debug("Removed patch: " + f.getRelativePath());
                    file.delete();
                }
            }
        });

        // We want things sorted in reverse order. Do that sub folders come before parents
        Collections.sort(directories, new Comparator<File>()
        {
            @Override
            public int compare(File o1, File o2)
            {
                int r = o1.compareTo(o2);
                if (r < 0) return  1;
                if (r > 0) return -1;
                return 0;
            }
        });

        for (File f : directories)
        {
            if (f.listFiles().length == 0)
            {
                getLogger().debug("Removing empty dir: " + f);
                f.delete();
            }
        }
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
            String unidiff = diff.toUnifiedDiff(originalPrefix + relative, changedPrefix + relative, Files.newReader(file, Charsets.UTF_8), Files.newReader(changedFile, Charsets.UTF_8), 3);
            unidiff = unidiff.replace("\r\n", "\n"); //Normalize lines
            unidiff = unidiff.replace("\n" + Hunk.ENDING_NEWLINE + "\n", "\n"); //We give 0 shits about this.

            String olddiff = "";
            if (patchFile.exists())
            {
                olddiff = Files.toString(patchFile, Charsets.UTF_8);
            }

            if (!olddiff.equals(unidiff))
            {
                getLogger().debug("Writing patch: " + patchFile);
                patchFile.getParentFile().mkdirs();
                Files.touch(patchFile);
                Files.write(unidiff, patchFile, Charsets.UTF_8);
            }
            else
            {
                getLogger().debug("Patch did not change");
            }
            created.add(patchFile);
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
