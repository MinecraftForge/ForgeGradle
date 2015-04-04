package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.extrastuff.FFPatcher;
import net.minecraftforge.gradle.extrastuff.FmlCleanup;
import net.minecraftforge.gradle.extrastuff.GLConstantFixer;
import net.minecraftforge.gradle.extrastuff.McpCleanup;
import net.minecraftforge.gradle.patching.ContextualPatch;
import net.minecraftforge.gradle.patching.ContextualPatch.HunkReport;
import net.minecraftforge.gradle.patching.ContextualPatch.PatchReport;
import net.minecraftforge.gradle.patching.ContextualPatch.PatchStatus;
import net.minecraftforge.gradle.tasks.abstractutil.EditJarTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.process.JavaExecSpec;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.Files;

public class DecompileTask extends EditJarTask
{
    @InputFile
    private Object fernFlower;

    @InputFiles
    private Object patchDir;

    @InputFile
    private Object astyleConfig;

    private static final Pattern BEFORE = Pattern.compile("(?m)((case|default).+(?:\\r\\n|\\r|\\n))(?:\\r\\n|\\r|\\n)");
    private static final Pattern AFTER  = Pattern.compile("(?m)(?:\\r\\n|\\r|\\n)((?:\\r\\n|\\r|\\n)[ \\t]+(case|default))");

    @Override
    public void doStuffBefore() throws Exception
    {
        File in = getInJar();
        
        // this sets the resolvedInJar so that this new file is
        // read and loaded later in the task.
        resolvedInJar = new File(getTemporaryDir(), in.getName());
        
        getLogger().info("Decompiling Jar");
        decompile(getInJar(), getTemporaryDir(), getFernFlower());
    }
    
    @Override
    public String asRead(String file)
    {
        return FFPatcher.processFile(file);
    }

    @Override
    public void doStuffMiddle(Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception
    {
        Multimap<String, File> patchesMap = ArrayListMultimap.create();
        for (File f : getPatches())
        {
            String name = f.getName();
            int patchIndex = name.indexOf(".patch");

            // 6 is the length of ".patch" + 3 to account for .## at the end of the file.
            if (patchIndex < 0 || patchIndex < name.length() - 9)
                continue;

            patchesMap.put(name.substring(0, patchIndex), f);
        }
        
        // setup formatter
        ASFormatter formatter = new ASFormatter();
        OptParser parser = new OptParser(formatter);
        parser.parseOptionFile(getAstyleConfig());
        
        GLConstantFixer oglFixer = new GLConstantFixer();
        
        // START THREADING
        ExecutorCompletionService<ThreadTaskOutput> executor = new ExecutorCompletionService<ThreadTaskOutput>(
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        
        // add tasks
        for (Entry<String, String> entry : sourceMap.entrySet())
        {
            // do that string replace because the patchFile names are seperated with . and not /
            Collection<File> patchFiles = patchesMap.get(entry.getKey().replace('\\', '/').replace('/', '.'));
            executor.submit(new ThreadTask(entry.getKey(), entry.getValue(), formatter, oglFixer, patchFiles));
        }
        
        // grab thread outputs
        Future<ThreadTaskOutput> future = null;
        while((future = executor.take()) != null)
        {
            ThreadTaskOutput output = future.get();
            sourceMap.put(output.name, output.outString);
            printPatchErrors(getLogger(), output.patchErrors);
        }
    }

    private void decompile(final File inJar, final File outJar, final File fernFlower)
    {
        getProject().javaexec(new Closure<JavaExecSpec>(this)
        {
            private static final long serialVersionUID = 4608694547855396167L;

            public JavaExecSpec call()
            {
                JavaExecSpec exec = (JavaExecSpec) getDelegate();

                exec.args(
                        fernFlower.getAbsolutePath(),
                        "-din=1",
                        "-rbr=0",
                        "-dgs=1",
                        "-asc=1",
                        "-log=ERROR",
                        inJar.getAbsolutePath(),
                        outJar.getAbsolutePath()
                );

                exec.setMain("-jar");
                exec.setWorkingDir(fernFlower.getParentFile());

                exec.classpath(Constants.getClassPath());
                exec.setStandardOutput(Constants.getTaskLogStream(getProject(), getName() + ".log"));

                exec.setMaxHeapSize("512M");

                return exec;
            }

            public JavaExecSpec call(Object obj)
            {
                return call();
            }
        });
    }

    private static void printPatchErrors(Logger logger, List<PatchReport> errors) throws Exception
    {
        boolean fuzzed = false;
        for (PatchReport report : errors)
        {
            if (!report.getStatus().isSuccess())
            {
                logger.log(LogLevel.ERROR, "Patching failed: " + report.getTarget(), report.getFailure());

                for (HunkReport hunk : report.getHunks())
                {
                    if (!hunk.getStatus().isSuccess())
                    {
                        logger.error("Hunk " + hunk.getHunkID() + " failed!");
                    }
                }

                Throwables.propagate(report.getFailure());
            }
            else if (report.getStatus() == PatchStatus.Fuzzed) // catch fuzzed patches
            {
                logger.log(LogLevel.INFO, "Patching fuzzed: " + report.getTarget(), report.getFailure());
                fuzzed = true;

                for (HunkReport hunk : report.getHunks())
                {
                    if (!hunk.getStatus().isSuccess())
                    {
                        logger.info("Hunk " + hunk.getHunkID() + " fuzzed " + hunk.getFuzz() + "!");
                    }
                }
            }
            else
            {
                logger.debug("Patch succeeded: " + report.getTarget());
            }
        }
        if (fuzzed)
            logger.lifecycle("Patches Fuzzed!");
    }

    private static ContextualPatch findPatch(Collection<File> files, ContextProvider provider) throws Exception
    {
        ContextualPatch patch = null;
        for (File f : files)
        {
            patch = ContextualPatch.create(Files.toString(f, Charset.defaultCharset()), provider);
            List<PatchReport> errors = patch.patch(true);

            boolean success = true;
            for (PatchReport rep : errors)
            {
                if (!rep.getStatus().isSuccess()) success = false;
            }
            if (success) break;
        }
        return patch;
    }

    public File getAstyleConfig()
    {
        return getProject().file(astyleConfig);
    }

    public void setAstyleConfig(Object astyleConfig)
    {
        this.astyleConfig = astyleConfig;
    }

    public File getFernFlower()
    {
        return getProject().file(fernFlower);
    }

    public void setFernFlower(Object fernFlower)
    {
        this.fernFlower = fernFlower;
    }

    @InputFiles
    public FileCollection getPatches()
    {
        return getProject().fileTree(patchDir);
    }

    public void setPatches(Object patchesDir)
    {
        this.patchDir = patchesDir;
    }

    /**
     * A private inner class to be used with the MCPPatches only.
     */
    private static class ContextProvider implements ContextualPatch.IContextProvider
    {
        private static final Pattern LINE_PATTERN = Pattern.compile("\r\n|\r|\n");
        private List<String> data;

        public ContextProvider(String file)
        {
            data = Splitter.on(LINE_PATTERN).splitToList(file);;
        }

        @Override
        public List<String> getData(String target)
        {
            return data;
        }

        @Override
        public void setData(String target, List<String> data)
        {
            this.data = data;
        }
        
        public String getAsString()
        {
            return Joiner.on(Constants.NEWLINE).join(data);
        }
    }
    
    private static class ThreadTaskOutput
    {
        public final String name;
        public final String outString;
        public final List<PatchReport> patchErrors;
        public ThreadTaskOutput(String name, String outString, List<PatchReport> patchErrors)
        {
            this.name = name;
            this.outString = outString;
            if (patchErrors == null)
                this.patchErrors = new ArrayList<PatchReport>(0);
            else
                this.patchErrors = patchErrors;
        }
    }
    
    private static class ThreadTask implements Callable<ThreadTaskOutput>
    {
        private final ASFormatter formatter;
        private final GLConstantFixer oglFixer;
        private final Collection<File> patchFiles;
        private final String inputName;
        private final String inputSource;
        
        public ThreadTask(String inputName, String inputSource, ASFormatter formatter, GLConstantFixer oglFixer, Collection<File> patchFiles)
        {
            super();
            this.inputName = inputName;
            this.inputSource = inputSource;
            this.formatter = formatter;
            this.oglFixer = oglFixer;
            this.patchFiles = patchFiles;
        }
        
        @Override
        public ThreadTaskOutput call() throws Exception
        {
            String workingString = inputSource;
            
            // patch the file
            List<PatchReport> patchErrors = null;
            if (!patchFiles.isEmpty())
            {
                ContextProvider provider = new ContextProvider(workingString);
                ContextualPatch patch = findPatch(patchFiles, provider);
                patchErrors = patch.patch(false);
                workingString = provider.getAsString();
            }
            
            //getLogger().debug("Processing file: " + file);

            //getLogger().debug("processing comments");
            workingString = McpCleanup.stripComments(workingString);

            //getLogger().debug("fixing imports comments");
            workingString = McpCleanup.fixImports(workingString);

            //getLogger().debug("various other cleanup");
            workingString = McpCleanup.cleanup(workingString);

            //getLogger().debug("fixing OGL constants");
            workingString = oglFixer.fixOGL(workingString);

            //getLogger().debug("formatting source");
            Reader reader = new StringReader(workingString);
            Writer writer = new StringWriter();
            formatter.format(reader, writer);
            reader.close();
            writer.flush();
            writer.close();
            workingString = writer.toString();

            //getLogger().debug("applying FML transformations");
            workingString = BEFORE.matcher(workingString).replaceAll("$1");
            workingString = AFTER.matcher(workingString).replaceAll("$1");
            workingString = FmlCleanup.renameClass(workingString);
            
            return new ThreadTaskOutput(inputName, workingString, patchErrors);
        }
    }

    //@formatter:off
    
    @Override
    public void doStuffAfter() throws Exception { }

    @Override
    protected boolean storeJarInRam() { return false; }
}
