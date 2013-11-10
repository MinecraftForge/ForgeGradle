package net.minecraftforge.gradle.tasks;

import argo.saj.InvalidSyntaxException;

import com.github.abrarsyed.jastyle.ASFormatter;
import com.github.abrarsyed.jastyle.OptParser;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.patching.ContextualPatch;
import net.minecraftforge.gradle.sourcemanip.FFPatcher;
import net.minecraftforge.gradle.sourcemanip.FmlCleanup;
import net.minecraftforge.gradle.sourcemanip.GLConstantFixer;
import net.minecraftforge.gradle.sourcemanip.McpCleanup;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.logging.LogLevel;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaExecSpec;

import java.io.*;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DecompileTask extends CachedTask
{
    @InputFile
    private DelayedFile inJar;

    @InputFile
    private DelayedFile fernFlower;

    @InputFile
    private DelayedFile patch;

    @InputFile
    private DelayedFile astyleConfig;

    @OutputFile
    @Cached
    private DelayedFile outJar;

    private HashMap<String, String> sourceMap   = new HashMap<String, String>();
    private HashMap<String, byte[]> resourceMap = new HashMap<String, byte[]>();
    
    private static final Pattern BEFORE = Pattern.compile("(?m)((case|default).+(?:\\r\\n|\\r|\\n))(?:\\r\\n|\\r|\\n)");
    private static final Pattern AFTER  = Pattern.compile("(?m)(?:\\r\\n|\\r|\\n)((?:\\r\\n|\\r|\\n)[ \\t]+(case|default))");

    /**
     * This method outputs to the cleanSrc
     */
    @TaskAction
    protected void doMCPStuff() throws Throwable
    {
        // define files.
        File temp = new File(getTemporaryDir(), getInJar().getName());

        getLogger().info("Decompiling Jar");
        decompile(getInJar(), getTemporaryDir(), getFernFlower());

        getLogger().info("Loading decompiled jar");
        readJarAndFix(temp);

        getLogger().info("Applying MCP patches");
        applyMcpPatches(getPatch());

        getLogger().info("Cleaning source");
        applyMcpCleanup(getAstyleConfig());

        getLogger().info("Saving Jar");
        saveJar(getOutJar());
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
                        "-din=0",
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

                exec.setStandardOutput(Constants.getNullStream());

                return exec;
            }

            public JavaExecSpec call(Object obj)
            {
                return call();
            }
        });
    }

    private void readJarAndFix(final File jar) throws IOException
    {
        // begin reading jar
        final ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipEntry entry = null;
        String fileStr;

        while ((entry = zin.getNextEntry()) != null)
        {
            // no META or dirs. wel take care of dirs later.
            if (entry.getName().contains("META-INF"))
            {
                continue;
            }

            // resources or directories.
            if (entry.isDirectory() || !entry.getName().endsWith(".java"))
            {
                resourceMap.put(entry.getName(), ByteStreams.toByteArray(zin));
            }
            else
            {
                // source!
                fileStr = new String(ByteStreams.toByteArray(zin), Charset.defaultCharset());

                // fix
                fileStr = FFPatcher.processFile(new File(entry.getName()).getName(), fileStr);

                sourceMap.put(entry.getName(), fileStr);
            }
        }

        zin.close();
    }

    private void applyMcpPatches(File patchFile) throws Throwable
    {
        ContextualPatch patch = ContextualPatch.create(Files.toString(patchFile, Charset.defaultCharset()), new ContextProvider(sourceMap));

        boolean fuzzed = false;

        List<ContextualPatch.PatchReport> errors = patch.patch(false);
        for (ContextualPatch.PatchReport report : errors)
        {
            // catch failed patches
            if (!report.getStatus().isSuccess())
            {
                getLogger().log(LogLevel.ERROR, "Patching failed: " + report.getTarget(), report.getFailure());

                // now spit the hunks
                for (ContextualPatch.HunkReport hunk : report.getHunks())
                {
                    // catch the failed hunks
                    if (!hunk.getStatus().isSuccess())
                    {
                        getLogger().error("Hunk "+hunk.getHunkID()+" failed!");
                    }
                }

                throw report.getFailure();
            }
            // catch fuzzed patches
            else if (report.getStatus() == ContextualPatch.PatchStatus.Fuzzed)
            {
                getLogger().log(LogLevel.INFO, "Patching fuzzed: " + report.getTarget(), report.getFailure());

                // set the boolean for later use
                fuzzed = true;

                // now spit the hunks
                for (ContextualPatch.HunkReport hunk : report.getHunks())
                {
                    // catch the failed hunks
                    if (!hunk.getStatus().isSuccess())
                    {
                        getLogger().info("Hunk "+hunk.getHunkID()+" fuzzed "+hunk.getFuzz()+"!");
                    }
                }
            }

            // sucesful patches
            else
            {
                getLogger().info("Patch succeeded: " + report.getTarget());
            }
        }

        if (fuzzed)
            getLogger().lifecycle("Patches Fuzzed!");
    }

    private void applyMcpCleanup(File conf) throws IOException, InvalidSyntaxException
    {
        ASFormatter formatter = new ASFormatter();
        OptParser parser = new OptParser(formatter);
        parser.parseOptionFile(conf);

        Reader reader;
        Writer writer;

        GLConstantFixer fixer = new GLConstantFixer();

        for (String file : sourceMap.keySet())
        {
            String text = sourceMap.get(file);

            getLogger().debug("Processing file: " + file);

            getLogger().debug("processing comments");
            text = McpCleanup.stripComments(text);

            getLogger().debug("fixing imports comments");
            text = McpCleanup.fixImports(text);

            getLogger().debug("various other cleanup");
            text = McpCleanup.cleanup(text);

            getLogger().debug("fixing OGL constants");
            text = fixer.fixOGL(text);

            getLogger().debug("formatting source");
            reader = new StringReader(text);
            writer = new StringWriter();
            formatter.format(reader, writer);
            reader.close();
            writer.flush();
            writer.close();
            text = writer.toString();
            
            getLogger().debug("applying FML transformations");
            text = BEFORE.matcher(text).replaceAll("$1");
            text = AFTER.matcher(text).replaceAll("$1");
            text = FmlCleanup.renameClass(text);

            sourceMap.put(file, text);
        }
    }

    private void saveJar(File output) throws IOException
    {
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output));

        // write in resources
        for (Map.Entry<String, byte[]> entry : resourceMap.entrySet())
        {
            zout.putNextEntry(new ZipEntry(entry.getKey()));
            zout.write(entry.getValue());
            zout.closeEntry();
        }

        // write in sources
        for (Map.Entry<String, String> entry : sourceMap.entrySet())
        {
            zout.putNextEntry(new ZipEntry(entry.getKey()));
            zout.write(entry.getValue().getBytes());
            zout.closeEntry();
        }

        zout.close();
    }

    public HashMap<String, String> getSourceMap()
    {
        return sourceMap;
    }

    public void setSourceMap(HashMap<String, String> sourceMap)
    {
        this.sourceMap = sourceMap;
    }

    public File getAstyleConfig()
    {
        return astyleConfig.call();
    }

    public void setAstyleConfig(DelayedFile astyleConfig)
    {
        this.astyleConfig = astyleConfig;
    }

    public File getFernFlower()
    {
        return fernFlower.call();
    }

    public void setFernFlower(DelayedFile fernFlower)
    {
        this.fernFlower = fernFlower;
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }

    public File getPatch()
    {
        return patch.call();
    }

    public void setPatch(DelayedFile patch)
    {
        this.patch = patch;
    }

    public HashMap<String, byte[]> getResourceMap()
    {
        return resourceMap;
    }

    public void setResourceMap(HashMap<String, byte[]> resourceMap)
    {
        this.resourceMap = resourceMap;
    }

    /**
     * A private inner class to be used with the MCPPatches only.
     */
    private class ContextProvider implements ContextualPatch.IContextProvider
    {
        private Map<String, String> fileMap;

        private final int STRIP = 1;

        public ContextProvider(Map<String, String> fileMap)
        {
            this.fileMap = fileMap;
        }

        private String strip(String target)
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
            fileMap.put(strip(target), Joiner.on(Constants.NEWLINE).join(data));
        }
    }
}
