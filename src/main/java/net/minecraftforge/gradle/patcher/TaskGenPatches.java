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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import net.minecraftforge.gradle.util.SequencedInputSupplier;
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

class TaskGenPatches extends DefaultTask
{
    //@formatter:off
    @OutputDirectory private Object patchDir;
    private final List<Object>      originals = new LinkedList<Object>();
    private final List<Object>      changed = new LinkedList<Object>();
    @Input private String           originalPrefix = "";
    @Input private String           changedPrefix = "";
    //@formatter:on
    
    //@formatter:off
    public TaskGenPatches() { super(); }
    //@formatter:on

    private Set<File> created = new HashSet<File>();

    @TaskAction
    public void doTask() throws IOException, PatchException
    {
        created.clear();
        getPatchDir().mkdirs();

        // fix and create patches.
        processFiles(getSupplier(getOriginalSource()), getSupplier(getChangedSource()));
        
        removeOld(getPatchDir());
    }
    
    private static InputSupplier getSupplier(List<File> files) throws IOException
    {
        SequencedInputSupplier supplier = new SequencedInputSupplier(files.size() + 1);
        
        for (File f : files)
        {
            if (f.isDirectory())
                supplier.add(new FolderSupplier(f));
            else
            {
                ZipInputSupplier supp = new ZipInputSupplier();
                supp.readZip(f);
                supplier.add(supp);
            }
        }
        
        return supplier;
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
                File file;
                try
                {
                    file = f.getFile().getCanonicalFile();
                    if (!created.contains(file))
                    {
                        getLogger().debug("Removed patch: " + f.getRelativePath());
                        file.delete();
                    }
                }
                catch (IOException e)
                {
                    // impossibru
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

        File patchFile = new File(getPatchDir(), relative + ".patch").getCanonicalFile();

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
    
    @InputFiles
    public FileCollection getOriginalSources()
    {
        return getProject().files(originals);
    }

    public List<File> getOriginalSource()
    {
        List<File> files = new LinkedList<File>();
        for (Object f : originals)
            files.add(getProject().file(f));
        return files;
    }

    public void addOriginalSource(Object in)
    {
        this.originals.add(in);
    }
    
    @InputFiles
    public FileCollection getChangedSources()
    {
        return getProject().files(changed);
    }

    public List<File> getChangedSource()
    {
        List<File> files = new LinkedList<File>();
        for (Object f : changed)
            files.add(getProject().file(f));
        return files;
    }

    public void addChangedSource(Object in)
    {
        this.changed.add(in);
    }

    public File getPatchDir()
    {
        return getProject().file(patchDir);
    }

    public void setPatchDir(Object patchDir)
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
