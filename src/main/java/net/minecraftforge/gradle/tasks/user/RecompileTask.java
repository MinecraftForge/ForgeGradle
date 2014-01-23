package net.minecraftforge.gradle.tasks.user;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class RecompileTask extends CachedTask
{
    @InputFile
    DelayedFile inSrcJar;

    @Cached
    @OutputFile
    DelayedFile outJar;

    @Input
    String      config;

    @TaskAction
    public void doTask() throws IOException
    {
        final File srcDir = new File(getTemporaryDir(), "sources");
        final File classDir = new File(getTemporaryDir(), "classes");
        Configuration config = getProject().getConfigurations().getByName(this.config);

        getLogger().lifecycle("extracting sources...");
        srcDir.mkdirs();
        readInJar(getInSrcJar(), srcDir, classDir);

        getLogger().lifecycle("compiling sources...");
        classDir.mkdirs();
        compile(srcDir, classDir, config);

        getLogger().lifecycle("rebuilding jar...");
        buildJar(getOutJar(), classDir);
    }

    private void readInJar(File inJar, File srcDir, File clsDir) throws IOException
    {
        // begin reading jar
        final ZipInputStream zin = new ZipInputStream(new FileInputStream(inJar));
        ZipEntry entry = null;
        File out;
        
        while ((entry = zin.getNextEntry()) != null)
        {
            if (!entry.isDirectory())
            {
                if (entry.getName().endsWith(".java"))
                    out = new File(srcDir, entry.getName());
                else
                    out = new File(clsDir, entry.getName());
                
                out.getParentFile().mkdirs();
                if (!out.exists())
                    out.createNewFile();
                
                Files.write(ByteStreams.toByteArray(zin), out);
            }
        }

        zin.close();
    }

    @SuppressWarnings("serial")
    private void compile(final File src, final File outDir, final Configuration config) throws IOException
    {
        final OutputStream stream = Constants.getNullStream();
        
        outDir.getParentFile().mkdirs();
        if (!outDir.exists())
            outDir.createNewFile();
        
        // generate file name
        final File listFile = new File(getTemporaryDir(), "list.txt");
        listFile.getParentFile().mkdirs();
        if (!listFile.exists())
            listFile.createNewFile();
        Files.write(Joiner.on('\n').join(getProject().fileTree(src).getFiles()), listFile, Charset.defaultCharset());
        

        getProject().exec(new Closure<Object>(this, this) {
            public Object call(Object... obj)
            {
                ExecSpec exec = (ExecSpec) getDelegate();

                exec.executable("javac");

                exec.args("-d", outDir.getAbsolutePath()); // output dir
                exec.args("-target", "1.6", "-source", "1.6"); // src and target compat
                exec.args("-cp", config.getAsPath()); // classpath
                exec.args("-nowarn"); // dont wanna see the warnings
                exec.args("@"+listFile.getAbsolutePath()); // all java files
                
                exec.workingDir(src);

                // comment for debugging
                exec.setIgnoreExitValue(true);
                exec.setStandardOutput(stream);
                exec.setErrorOutput(stream);

                return exec;
            }
        });

        stream.close();
    }

    private void buildJar(File outJar, File classDir) throws IOException
    {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        
        JarOutputStream jout = new JarOutputStream(new FileOutputStream(outJar), manifest);
        
        // write in sources
        int cut = classDir.getCanonicalPath().length() + 1;
        writeDir(classDir, cut, jout);

        jout.flush();
        jout.close();
    }
    
    private void writeDir(File dir, int cut, JarOutputStream jout) throws IOException
    {
        for (File file : dir.listFiles())
        {
            String name = file.getCanonicalPath().substring(cut).replace('\\', '/');
            if (file.isDirectory())
            {
                jout.putNextEntry(new JarEntry(name + "/"));
                jout.closeEntry();
                writeDir(file, cut, jout);
            }
            else
            {
                jout.putNextEntry(new JarEntry(name));
                Files.copy(file, jout);
                jout.closeEntry();
            }
        }
    }

    public File getInSrcJar()
    {
        return inSrcJar.call();
    }

    public void setInSrcJar(DelayedFile inSrcJar)
    {
        this.inSrcJar = inSrcJar;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }

    public String getConfig()
    {
        return config;
    }

    public void setConfig(String config)
    {
        this.config = config;
    }
}
