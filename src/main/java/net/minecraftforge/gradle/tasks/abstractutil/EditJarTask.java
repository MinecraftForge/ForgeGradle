package net.minecraftforge.gradle.tasks.abstractutil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

public abstract class EditJarTask extends CachedTask
{
    @InputFile
    private Object inJar;

    @Cached
    @OutputFile
    private Object outJar;

    protected File   resolvedInJar;
    protected File   resolvedOutJar;

    @TaskAction
    public void doTask() throws Throwable
    {
        resolvedInJar = getInJar();
        resolvedOutJar = getOutJar();

        doStuffBefore();
        
        if (storeJarInRam())
        {
            getLogger().debug("Reading jar: " + resolvedInJar);
            
            Map<String, String> sourceMap   = Maps.newHashMap();
            Map<String, byte[]> resourceMap   = Maps.newHashMap();
            
            readAndStoreJarInRam(resolvedInJar, sourceMap, resourceMap);

            doStuffMiddle(sourceMap, resourceMap);
            
            saveJar(resolvedOutJar, sourceMap, resourceMap);
            
            getLogger().debug("Saving jar: " + resolvedOutJar);
        }
        else
        {
            copyJar(resolvedInJar, resolvedOutJar);
        }

        doStuffAfter();
    }

    /**
     * Do Stuff before the jar is read
     * @throws Exception for convenience
     */
    public abstract void doStuffBefore() throws Exception;

    /**
     * Called as the .java files of the jar are read from the jar
     * @param file current contents of the file
     * @return new new contents of the file
     */
    public abstract String asRead(String file);

    /**
     * Do Stuff after the jar is read, but before it is written.
     * @param sourceMap name->contents  for all java files in the jar
     * @param resourceMap name->contents  for everything else
     * @throws Exception for convenience
     */
    public abstract void doStuffMiddle(Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws Exception;

    /**
     * Do Stuff after the jar is Written
     * @throws Exception for convenience
     */
    public abstract void doStuffAfter() throws Exception;
    
    /**
     * Whether to store the contents of the jar in RAM.
     * If this returns false, then the doStuffMiddle method is not called. 
     * @return
     */
    protected abstract boolean storeJarInRam();

    private final void readAndStoreJarInRam(File jar, Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws IOException
    {
        ZipInputStream zin = new ZipInputStream(new FileInputStream(jar));
        ZipEntry entry = null;
        String fileStr;

        while ((entry = zin.getNextEntry()) != null)
        {
            // ignore META-INF, it shouldnt be here. If it is we remove it from the output jar.
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
                fileStr = new String(ByteStreams.toByteArray(zin), Constants.CHARSET);

                fileStr = asRead(fileStr);

                sourceMap.put(entry.getName(), fileStr);
            }
        }

        zin.close();
    }

    protected static void saveJar(File output, Map<String, String> sourceMap, Map<String, byte[]> resourceMap) throws IOException
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
    
    private void copyJar(File input, File output) throws IOException
    {
        // begin reading jar
        ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
        JarOutputStream zout = new JarOutputStream(new FileOutputStream(output));
        ZipEntry entry = null;

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
                zout.putNextEntry(new JarEntry(entry));
                ByteStreams.copy(zin, zout);
                zout.closeEntry();
            }
            else
            {
                // source
                zout.putNextEntry(new JarEntry(entry));
                zout.write(asRead(new String(ByteStreams.toByteArray(zin), Constants.CHARSET)).getBytes());
                zout.closeEntry();
            }
        }

        zout.close();
        zin.close();
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }
}
