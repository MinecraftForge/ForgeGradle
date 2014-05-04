package net.minecraftforge.gradle.tasks.dev;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.cloudbees.diff.Diff;
import com.cloudbees.diff.Hunk;
import com.cloudbees.diff.PatchException;
import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class GeneratePatches extends DefaultTask
{
    @OutputDirectory
    DelayedFile patchDir;

    @InputFiles
    DelayedFile changed;

    @InputFiles
    DelayedFile original;

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
        processFiles(getSupplier(original.call()), getSupplier(changed.call()));
        
        removeOld(getPatchDir());
    }

    private InputSupplier getSupplier(File file) throws IOException
    {
        if (file.isDirectory())
            return new FolderSupplier(file);

        ZipInputSupplier ret = new ZipInputSupplier();
        ret.readZip(file);
        return ret;
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

    public void processFiles(InputSupplier original, InputSupplier changed) throws IOException
    {
        List<String> paths = original.gatherAll("");
        for (String path : paths)
        {
            path = path.replace('\\', '/');
            InputStream o = original.getInput(path);
            InputStream c = changed.getInput(path);
            try
            {
                processFile(path, o, c);
            }
            finally
            {
                if (o != null) o.close();
                if (c != null) c.close();
            }
        }
    }

    public void processFile(String relative, InputStream original, InputStream changed) throws IOException
    {
        getLogger().debug("Diffing: " + relative);

        File patchFile = new File(getPatchDir(), relative + ".patch");

        if (changed == null)
        {
            getLogger().debug("    Changed File does not exist");
            return;
        }

        // We have to cache the bytes because diff reads the stream twice.. why.. who knows.
        byte[] oData = ByteStreams.toByteArray(original);
        byte[] cData = ByteStreams.toByteArray(changed);

        Diff diff = Diff.diff(new InputStreamReader(new ByteArrayInputStream(oData), Charsets.UTF_8), new InputStreamReader(new ByteArrayInputStream(cData), Charsets.UTF_8), false);

        if (!relative.startsWith("/"))
            relative = "/" + relative;

        if (!diff.isEmpty())
        {
            String unidiff = diff.toUnifiedDiff(originalPrefix + relative, changedPrefix + relative, 
                    new InputStreamReader(new ByteArrayInputStream(oData), Charsets.UTF_8), 
                    new InputStreamReader(new ByteArrayInputStream(cData), Charsets.UTF_8), 3);
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

    public FileCollection getChanged()
    {
        File f = changed.call();
        if (f.isDirectory())
            return getProject().fileTree(f);
        else
            return getProject().files(f);
    }

    public void setChanged(DelayedFile changed)
    {
        this.changed = changed;
    }

    public FileCollection getOriginal()
    {
        File f = original.call();
        if (f.isDirectory())
            return getProject().fileTree(f);
        else
            return getProject().files(f);
    }

    public void setOriginal(DelayedFile original)
    {
        this.original = original;
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
