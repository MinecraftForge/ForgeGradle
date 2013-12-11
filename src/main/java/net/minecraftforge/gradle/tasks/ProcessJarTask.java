package net.minecraftforge.gradle.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import joptsimple.internal.Strings;
import net.md_5.specialsource.AccessMap;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.RemapperPreprocessor;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.google.common.io.ByteStreams;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class ProcessJarTask extends CachedTask
{
    @InputFile
    private DelayedFile            inJar;

    @InputFile
    private DelayedFile            srg;

    @InputFile
    private DelayedFile            exceptorCfg;

    @OutputFile
    @Cached
    private DelayedFile            outCleanJar;                                                     // clean = pure forge, or pure FML

    @OutputFile
    @Cached
    private DelayedFile            outDirtyJar = new DelayedFile(getProject(), Constants.DEOBF_JAR); // dirty = has any other ATs

    private ArrayList<DelayedFile> ats         = new ArrayList<DelayedFile>();

    private boolean                isClean     = true;

    public void addTransformer(DelayedFile... obj)
    {
        for (DelayedFile object : obj)
        {
            ats.add(object);
        }
    }

    /**
     * adds an access transformer to the deobfuscation of this
     * @param obj
     */
    public void addTransformer(Object... obj)
    {
        for (Object object : obj)
        {
            if (object instanceof File)
                ats.add(new DelayedFile(getProject(), ((File) object).getAbsolutePath()));
            else if (object instanceof String)
                ats.add(new DelayedFile(getProject(), (String) object));
            else
                ats.add(new DelayedFile(getProject(), object.toString()));

            isClean = false;
        }
    }

    @TaskAction
    public void doTask() throws IOException
    {
        // make stuff into files.
        File tempObfJar = new File(getTemporaryDir(), "deobfed.jar"); // courtesy of gradle temp dir.
        File tempExcJar = new File(getTemporaryDir(), "excepted.jar"); // courtesy of gradle temp dir.

        // make the ATs list.. its a Set to avoid duplication.
        Set<File> ats = new HashSet<File>();
        for (DelayedFile obj : this.ats)
        {
            ats.add(getProject().file(obj).getCanonicalFile());
        }

        // deobf
        getLogger().lifecycle("Applying SpecialSource...");
        deobfJar(getInJar(), tempObfJar, getSrg(), ats);

        // apply exceptor
        getLogger().lifecycle("Applying Exceptor...");
        applyExceptor(tempObfJar, tempExcJar, getExceptorCfg(), new File(getTemporaryDir(), "exceptor.log"));

        File out = isClean ? getOutCleanJar() : getOutDirtyJar();

        getLogger().lifecycle("Injecting source info...");
        injectSourceInfo(tempExcJar, out);
    }

    private void deobfJar(File inJar, File outJar, File srg, Collection<File> ats) throws IOException
    {
        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(srg);

        // load in ATs
        AccessMap accessMap = new AccessMap();
        getLogger().info("Using AccessTransformers...");
        for (File at : ats)
        {
            getLogger().info("" + at);
            accessMap.loadAccessTransformer(at);
        }

        // make a processor out of the ATS and mappings.
        RemapperPreprocessor processor = new RemapperPreprocessor(null, mapping, accessMap);

        // make remapper
        JarRemapper remapper = new JarRemapper(processor, mapping);

        // load jar
        Jar input = Jar.init(inJar);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));
        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        // remap jar
        remapper.remapJar(input, outJar);
    }

    public void applyExceptor(File inJar, File outJar, File config, File log) throws IOException
    {
        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        getLogger().debug("CONFIG: " + config);

        MCInjectorImpl.process(inJar.getCanonicalPath(),
                outJar.getCanonicalPath(),
                config.getCanonicalPath(),
                log.getCanonicalPath(),
                null,
                0,
                null,
                false);
    }

    private void injectSourceInfo(File inJar, File outJar) throws IOException
    {
        ZipFile in = new ZipFile(inJar);
        final ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outJar)));

        for (ZipEntry e : Collections.list(in.entries()))
        {
            if (e.getName().contains("META-INF"))
                continue;

            if (e.isDirectory())
            {
                out.putNextEntry(e);
            }
            else
            {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);

                byte[] data = ByteStreams.toByteArray(in.getInputStream(e));

                // correct source name
                if (e.getName().endsWith(".class"))
                    data = correctSourceName(e.getName(), data);

                out.write(data);
            }
        }

        out.flush();
        out.close();
        in.close();
    }

    private byte[] correctSourceName(String name, byte[] data)
    {
        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();

        reader.accept(node, 0);

        if (Strings.isNullOrEmpty(node.sourceFile) || !node.sourceFile.endsWith(".java"))
            node.sourceFile = name.substring(name.lastIndexOf('/') + 1).replace(".class", ".java");

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public File getExceptorCfg()
    {
        return exceptorCfg.call();
    }

    public void setExceptorCfg(DelayedFile exceptorCfg)
    {
        this.exceptorCfg = exceptorCfg;
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }

    public File getOutCleanJar()
    {
        return outCleanJar.call();
    }

    public void setOutCleanJar(DelayedFile outJar)
    {
        this.outCleanJar = outJar;
    }

    public File getOutDirtyJar()
    {
        return outDirtyJar.call();
    }

    public void setOutDirtyJar(DelayedFile outDirtyJar)
    {
        this.outDirtyJar = outDirtyJar;
    }

    public boolean isClean()
    {
        return isClean;
    }

    /**
     * returns the actual output DelayedFile depending on Clean status
     * Unlike getOutputJar() this method does not resolve the files.
     */
    public DelayedFile getDelayedOutput()
    {
        return isClean ? outCleanJar : outDirtyJar;
    }

    /**
     * returns the actual output file depending on Clean status
     */
    public File getOutJar()
    {
        return isClean ? outCleanJar.call() : outDirtyJar.call();
    }

    @InputFiles
    public FileCollection getAts()
    {
        return getProject().files(ats.toArray());
    }
}
