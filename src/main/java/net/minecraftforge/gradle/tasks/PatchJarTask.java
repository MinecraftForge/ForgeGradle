package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.patching.ContextualPatch;
import net.minecraftforge.gradle.tasks.abstractutil.EditJarTask;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputDirectory;

import com.google.common.base.Joiner;
import com.google.common.io.Files;

public class PatchJarTask extends EditJarTask
{
    @InputDirectory
    private DelayedFile inPatches;

    private ContextProvider PROVIDER;

    @Override
    public String asRead(String file)
    {
        return file;
    }

    @Override
    public void doStuff() throws Throwable
    {
        PROVIDER = new ContextProvider(sourceMap);
        
        getLogger().info("Reading patches");
        ArrayList<ContextualPatch> patches = readPatches(getInPatches());

        boolean fuzzed = false;

        getLogger().info("Applying patches");

        Throwable failure = null;

        for (ContextualPatch patch : patches)
        {
            List<ContextualPatch.PatchReport> errors = patch.patch(false);
            for (ContextualPatch.PatchReport report : errors)
            {
                // catch failed patches
                if (!report.getStatus().isSuccess())
                {
                    getLogger().log(LogLevel.ERROR, "Patching failed: " + PROVIDER.strip(report.getTarget()) + " " + report.getFailure().getMessage());

                    // now spit the hunks
                    for (ContextualPatch.HunkReport hunk : report.getHunks())
                    {
                        // catch the failed hunks
                        if (!hunk.getStatus().isSuccess())
                        {
                            getLogger().error("Hunk " + hunk.getHunkID() + " failed! " + (hunk.getFailure() != null ? hunk.getFailure().getMessage() : ""));
                        }
                    }

                    if (failure == null) failure = report.getFailure();
                }
                // catch fuzzed patches
                else if (report.getStatus() == ContextualPatch.PatchStatus.Fuzzed)
                {
                    getLogger().log(LogLevel.INFO, "Patching fuzzed: " + PROVIDER.strip(report.getTarget()));

                    // set the boolean for later use
                    fuzzed = true;

                    // now spit the hunks
                    for (ContextualPatch.HunkReport hunk : report.getHunks())
                    {
                        // catch the failed hunks
                        if (!hunk.getStatus().isSuccess())
                        {
                            getLogger().info("Hunk " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz()+"!");
                        }
                    }

                    if (failure == null) failure = report.getFailure();
                }

                // sucesful patches
                else
                {
                    getLogger().info("Patch succeeded: " + PROVIDER.strip(report.getTarget()));
                }
            }
        }

        if (fuzzed)
        {
            getLogger().lifecycle("Patches Fuzzed!");
        }

        if (failure != null)
        {
            throw failure;
        }
    }

    private ArrayList<ContextualPatch> readPatches(File dir) throws IOException
    {
        ArrayList<ContextualPatch> patches = new ArrayList<ContextualPatch>();

        for (File file : dir.listFiles())
        {
            if (file.isDirectory())
            {
                patches.addAll(readPatches(file));
            }
            else if (file.getPath().endsWith(".patch"))
            {
                patches.add(readPatch(file));
            }
        }

        return patches;
    }

    public ContextualPatch readPatch(File file) throws IOException
    {
        getLogger().debug("Reading patch file: " + file);
        return ContextualPatch.create(Files.toString(file, Charset.defaultCharset()), PROVIDER).setAccessC14N(true).setMaxFuzz(0);
    }

    /**
     * A private inner class to be used with the FmlPatches
     */
    private class ContextProvider implements ContextualPatch.IContextProvider
    {
        private Map<String, String> fileMap;

        private final int STRIP = 3;

        public ContextProvider(Map<String, String> fileMap)
        {
            this.fileMap = fileMap;
        }

        public String strip(String target)
        {
            target = target.replace('\\', '/');
            int index = 0;
            for (int x = 0; x < STRIP; x++)
            {
                index = target.indexOf('/', index) + 1;
            }
            return target.substring(index);
        }

        @Override
        public List<String> getData(String target)
        {
            target = strip(target);

            if (fileMap.containsKey(target))
            {
                String[] lines = fileMap.get(target).split("\r\n|\r|\n");
                List<String> ret = new ArrayList<String>();
                for (String line : lines)
                {
                    ret.add(line);
                }
                return ret;
            }

            return null;
        }

        @Override
        public void setData(String target, List<String> data)
        {
            target = strip(target);
            fileMap.put(target, Joiner.on(Constants.NEWLINE).join(data));
        }
    }

    public File getInPatches()
    {
        return inPatches.call();
    }

    public void setInPatches(DelayedFile inPatches)
    {
        this.inPatches = inPatches;
    }
}
