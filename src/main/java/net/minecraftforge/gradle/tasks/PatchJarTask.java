package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.patching.ContextualPatch;
import net.minecraftforge.gradle.patching.ContextualPatch.PatchStatus;
import net.minecraftforge.gradle.tasks.abstractutil.EditJarTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

public class PatchJarTask extends EditJarTask
{
    @InputFiles
    private DelayedFile inPatches;
    
    @Input
    private int maxFuzz = 0;
    
    @Optional
    @Input
    private final Multimap<String, Closure<String>> sourceTransformers = HashMultimap.create();

    private ContextProvider PROVIDER;

    @Override
    public String asRead(String file)
    {
        return file;
    }
    
    @Override
    public void doStuffBefore() throws Throwable
    {
        PROVIDER = new ContextProvider(sourceMap);
    }

    @Override
    public void doStuffMiddle() throws Throwable
    {
        getLogger().info("Reading patches");
        ArrayList<PatchedFile> patches = readPatches(getInPatches());

        boolean fuzzed = false;

        getLogger().info("Applying patches");

        Throwable failure = null;

        for (PatchedFile patch : patches)
        {
            List<ContextualPatch.PatchReport> errors = patch.patch.patch(false);
            for (ContextualPatch.PatchReport report : errors)
            {
                // catch failed patches
                if (!report.getStatus().isSuccess())
                {
                    File reject = patch.makeRejectFile();
                    if (reject.exists())
                    {
                        reject.delete();
                    }
                    getLogger().log(LogLevel.ERROR, "Patching failed: " + PROVIDER.strip(report.getTarget()) + " " + report.getFailure().getMessage());
                    // now spit the hunks
                    int failed = 0;
                    for (ContextualPatch.HunkReport hunk : report.getHunks())
                    {
                        // catch the failed hunks
                        if (!hunk.getStatus().isSuccess())
                        {
                            failed++;
                            getLogger().error("  " + hunk.getHunkID() + ": " + (hunk.getFailure() != null ? hunk.getFailure().getMessage() : "") + " @ " + hunk.getIndex());
                            Files.append(String.format("++++ REJECTED PATCH %d\n", hunk.getHunkID()), reject, Charsets.UTF_8);
                            Files.append(Joiner.on('\n').join(hunk.hunk.lines), reject, Charsets.UTF_8);
                            Files.append(String.format("\n++++ END PATCH\n"), reject, Charsets.UTF_8);
                        }
                        else if (hunk.getStatus() == PatchStatus.Fuzzed)
                        {
                            getLogger().info("  " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz() + "!");
                        }
                    }
                    getLogger().log(LogLevel.ERROR, "  " + failed + "/" + report.getHunks().size() + " failed");
                    getLogger().log(LogLevel.ERROR, "  Rejects written to " + reject.getAbsolutePath());

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
                        if (hunk.getStatus() == PatchStatus.Fuzzed)
                        {
                            getLogger().info("  " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz() + "!");
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

//        if (failure != null)
//        {
//            throw failure;
//        }
        
        for (String key : sourceTransformers.keySet())
        {
            if (!sourceMap.containsKey(key))
                continue;
            
            String file = sourceMap.get(key);
            
            for (Closure<String> val : sourceTransformers.get(key))
                file = val.call(file);
            
            sourceMap.put(key, file);
        }
    }

    private ArrayList<PatchedFile> readPatches(FileCollection patchFiles) throws IOException
    {
        ArrayList<PatchedFile> patches = new ArrayList<PatchedFile>();

        for (File file : patchFiles.getFiles())
        {
            if (file.getPath().endsWith(".patch"))
            {
                patches.add(readPatch(file));
            }
        }

        return patches;
    }

    public PatchedFile readPatch(File file) throws IOException
    {
        getLogger().debug("Reading patch file: " + file);
        return new PatchedFile(file);
    }

    private class PatchedFile {
        public final File fileToPatch;
        public final ContextualPatch patch;

        public PatchedFile(File file) throws IOException
        {
            this.fileToPatch = file;
            this.patch = ContextualPatch.create(Files.toString(file, Charset.defaultCharset()), PROVIDER).setAccessC14N(true).setMaxFuzz(getMaxFuzz());
        }

        public File makeRejectFile()
        {
            return new File(fileToPatch.getParentFile(),fileToPatch.getName()+".rej");
        }
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

    public FileCollection getInPatches()
    {
        File file = inPatches.call();
        if (file.isDirectory())
            return getProject().fileTree(file);
        else if (file.getName().endsWith(".zip") || file.getName().endsWith(".jar"))
            return getProject().zipTree(file);
        else if (file.getName().endsWith(".tar") || file.getName().endsWith(".gz"))
            return getProject().tarTree(file);
        else
            return getProject().files(file);
    }

    public void setInPatches(DelayedFile inPatches)
    {
        this.inPatches = inPatches;
    }

    @Override
    public void doStuffAfter() throws Throwable
    {
        // TODO Auto-generated method stub

    }

    public int getMaxFuzz()
    {
        return maxFuzz;
    }

    public void setMaxFuzz(int maxFuzz)
    {
        this.maxFuzz = maxFuzz;
    }
    
    public Multimap<String, Closure<String>> getSourceTransformers()
    {
        return sourceTransformers;
    }
    
    public void addSourceTransformers(Multimap<String, Closure<String>> inputs)
    {
        sourceTransformers.putAll(inputs);
    }
    
    public void addSourceTransformer(String className, Closure<String> transformer)
    {
        sourceTransformers.put(className, transformer);
    }
}
