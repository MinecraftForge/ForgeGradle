package net.minecraftforge.gradle.tasks.abstractutil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.delayed.DelayedFile;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;

public abstract class EditJarTask extends CachedTask
{
    @InputFile
    protected DelayedFile inJar;

    @OutputFile
    @Cached
    protected DelayedFile outJar;

    protected HashMap<String, String> sourceMap   = new HashMap<String, String>();
    protected HashMap<String, byte[]> resourceMap = new HashMap<String, byte[]>();

    @TaskAction
    public void doTask() throws Throwable
    {
        doStuffBefore();
        getLogger().debug("Reading jar: " + inJar);
        readJarAndClean(getInJar());
        
        doStuffMiddle();
        
        getLogger().debug("Saving jar: "+outJar);
        saveJar(getOutJar());
        doStuffAfter();
    }

    public abstract String asRead(String file);
    
    /**
     * Do Stuff before the jar is read
     * @throws Exception for convenience
     */
    public abstract void doStuffBefore() throws Exception;

    /**
     * Do Stuff after the jar is read, but before it is written.
     * @throws Exception for convenience
     */
    public abstract void doStuffMiddle() throws Exception;
    
    /**
     * Do Stuff after the jar is Written
     * @throws Exception for convenience
     */
    public abstract void doStuffAfter() throws Exception;

    private void readJarAndClean(final File jar) throws IOException
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

                fileStr = asRead(fileStr);

                sourceMap.put(entry.getName(), fileStr);
            }
        }

        zin.close();
    }

    private void saveJar(File output) throws IOException
    {
        JarOutputStream zout = new JarOutputStream(new FileOutputStream(output));

        // write in resources
        for (Map.Entry<String, byte[]> entry : resourceMap.entrySet())
        {
            zout.putNextEntry(new JarEntry(entry.getKey()));
            zout.write(entry.getValue());
            zout.closeEntry();
        }

        // write in sources
        for (Map.Entry<String, String> entry : sourceMap.entrySet())
        {
            zout.putNextEntry(new JarEntry(entry.getKey()));
            zout.write(entry.getValue().getBytes());
            zout.closeEntry();
        }

        zout.close();
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

    public HashMap<String, byte[]> getResourceMap()
    {
        return resourceMap;
    }

    public void setResourceMap(HashMap<String, byte[]> resourceMap)
    {
        this.resourceMap = resourceMap;
    }

    public HashMap<String, String> getSourceMap()
    {
        return sourceMap;
    }

    public void setSourceMap(HashMap<String, String> sourceMap)
    {
        this.sourceMap = sourceMap;
    }
}
