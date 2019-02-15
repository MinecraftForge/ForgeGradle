/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.user;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import groovy.lang.Closure;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.mcp.ReobfExceptor;

/**
 * Reobfuscates an arbitrary jar artifact.
 *
 * <p>
 * To reobfuscate other artifacts or to change settings, use this in your build
 * script.
 *
 * <pre>
 *reobf {
 *    // the jar artifact to reobfuscate
 *    jar {
 *
 *        // Using non-default srg names
 *        // reobf to notch
 *        useNotchSrg()
 *        // or for Searge names
 *        useSrgSrg()
 *        // or something else
 *        mappings = file('srgs/minecraft.srg')
 *
 *        // In case you need to modify the classpath
 *        classpath += configurations.provided
 *
 *        // Use this to add srg files or lines
 *        // You can combine strings and files.
 *        extra 'PK: org/ejml your/pkg/ejml', file('srgs/mappings.srg')
 *
 *        // You can also use with '+=' and array
 *        extra += ['CL: your/pkg/Original your/pkg/Renamed', file('srgs/mappings2.srg')]
 *
 *    }
 *
 *    // Some other artifact using default settings
 *    // the brackets are needed to create it
 *    otherJar {}
 *}
 * </pre>
 *
 */
public class TaskSingleReobf extends DefaultTask
{
    private Object                 jar;
    private FileCollection         classpath;

    // because decomp stuff
    private Object                 fieldCsv;
    private Object                 methodCsv;
    private Object                 exceptorCfg;
    private Object                 deobfFile;
    private Object                 recompFile;
    private boolean                isDecomp          = false;

    private Object                 primarySrg;
    private List<Object>           secondarySrgFiles = Lists.newArrayList();
    private List<String>           extraSrgLines     = Lists.newArrayList();

    private List<ReobfTransformer> preTransformers   = Lists.newArrayList();
    private List<ReobfTransformer> postTransformers  = Lists.newArrayList();

    public TaskSingleReobf()
    {
        super();
        this.getOutputs().upToDateWhen(Constants.CALL_FALSE); // allways execute period
    }

    // Main Functionality
    // --------------------------------------------

    @TaskAction
    public void doTask() throws IOException
    {
        // prepare Srgs
        File srg = File.createTempFile("reobf-default", ".srg", getTemporaryDir());
        File srgLines = File.createTempFile("reobf-extraLines", ".srg", getTemporaryDir());

        srg.deleteOnExit();
        srgLines.deleteOnExit();

        if (isDecomp())
        {
            ReobfExceptor exc = new ReobfExceptor();
            exc.deobfJar = getDeobfFile();
            exc.toReobfJar = getRecompFile();
            exc.excConfig = getExceptorCfg();
            exc.fieldCSV = getFieldCsv();
            exc.methodCSV = getMethodCsv();
            exc.doFirstThings();
            exc.buildSrg(getPrimarySrg(), srg);
        }
        else
        {
            Files.copy(getPrimarySrg(), srg);
        }

        // generate extraSrg
        {
            if (!srgLines.exists())
            {
                srgLines.getParentFile().mkdirs();
                srgLines.createNewFile();
            }

            try (BufferedWriter writer = Files.newWriter(srgLines, Charsets.UTF_8))
            {
                for (String line : getExtraSrgLines())
                {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        // prepare jar for reobf
        File out = getJar(); // we will repalce the file on output
        File tempIn = File.createTempFile("input", ".jar", getTemporaryDir());
        tempIn.deleteOnExit();
        Constants.copyFile(out, tempIn); // copy the to-be-output jar to the temporary input location. because output == input

        // pre-transform
        List<ReobfTransformer> transformers = getPreTransformers();
        if (!transformers.isEmpty())
        {
            File transformed = File.createTempFile("preTransformed", ".jar", getTemporaryDir());
            transformed.deleteOnExit();
            applyExtraTransformers(tempIn, transformed, transformers);
            tempIn.delete();

            tempIn = transformed; // for later copying
        }

        // obfuscate
        File obfuscated = File.createTempFile("obfuscated", ".jar", getTemporaryDir());
        obfuscated.deleteOnExit();
        applySpecialSource(tempIn, obfuscated, srg, srgLines, getSecondarySrgFiles());
        tempIn.delete();

        // post transform
        transformers = getPostTransformers();
        if (!transformers.isEmpty())
        {
            File transformed = File.createTempFile("postTransformed", ".jar", getTemporaryDir());
            transformed.deleteOnExit();
            applyExtraTransformers(obfuscated, transformed, transformers);

            obfuscated = transformed; // for later copying
        }

        // copy to output
        Constants.copyFile(obfuscated, out);
        obfuscated.delete();
    }

    private void applySpecialSource(File input, File output, File srg, File extraSrg, FileCollection extraSrgFiles) throws IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(srg);
        mapping.loadMappings(extraSrg);

        for (File f : extraSrgFiles)
        {
            mapping.loadMappings(f);
        }

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping);

        // load jar
        URLClassLoader classLoader = null;
        try (Jar inputJar = Jar.init(input))
        {
            // ensure that inheritance provider is used
            JointProvider inheritanceProviders = new JointProvider();
            inheritanceProviders.add(new JarProvider(inputJar));

            if (classpath != null && !classpath.isEmpty())
                inheritanceProviders.add(new ClassLoaderProvider(classLoader = new URLClassLoader(Constants.toUrls(classpath))));

            mapping.setFallbackInheritanceProvider(inheritanceProviders);

            // remap jar
            remapper.remapJar(inputJar, output);
        }
        finally
        {
            if (classLoader != null)
                classLoader.close();
        }
    }

    private void applyExtraTransformers(File inJar, File outJar, List<ReobfTransformer> transformers) throws IOException
    {
        try (ZipFile in = new ZipFile(inJar);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outJar))))
        {
            for (ZipEntry e : Collections.list(in.entries()))
            {
                if (e.isDirectory())
                {
                    out.putNextEntry(e);
                    continue;
                }
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);

                byte[] data = ByteStreams.toByteArray(in.getInputStream(e));

                // correct source name
                if (e.getName().endsWith(".class"))
                {
                    for (ReobfTransformer trans : transformers)
                    {
                        data = trans.transform(data);
                    }
                }

                out.write(data);
            }
        }
    }

    // Main Jar and classpath
    // --------------------------------------------

    public File getJar()
    {
        return getProject().file(jar);
    }

    public void setJar(Object jar)
    {
        this.jar = jar;
    }

    public FileCollection getClasspath()
    {
        return classpath;
    }

    public void setClasspath(FileCollection classpath)
    {
        this.classpath = classpath;
    }

    // SRG STUFF
    // --------------------------------------------

    public File getPrimarySrg()
    {
        if (primarySrg == null)
            throw new GradleConfigurationException("Primary reobfuscation for Task '" + getName() + "' isnt set!");
        return getProject().file(primarySrg);
    }

    public void setPrimarySrg(Object srg)
    {
        this.primarySrg = srg;
    }

    public void addSecondarySrgFile(Object thing)
    {
        secondarySrgFiles.add(thing);
    }

    public FileCollection getSecondarySrgFiles()
    {
        List<File> files = new ArrayList<File>(secondarySrgFiles.size());

        for (Object thing : getProject().files(secondarySrgFiles))
        {
            File f = getProject().file(thing);
            if (f.isDirectory())
            {
                for (File nested : getProject().fileTree(f))
                {
                    if ("srg".equals(Files.getFileExtension(nested.getName()).toLowerCase()))
                    {
                        files.add(nested.getAbsoluteFile());
                    }
                }
            }
            else if ("srg".equals(Files.getFileExtension(f.getName()).toLowerCase()))
            {
                files.add(f.getAbsoluteFile());
            }
        }

        return getProject().files(files);
    }

    public List<String> getExtraSrgLines()
    {
        return extraSrgLines;
    }

    public void addExtraSrgLine(String srgLine)
    {
        this.extraSrgLines.add(srgLine);
    }

    public void addExtraSrgLines(String... srgLines)
    {
        this.extraSrgLines.addAll(Arrays.asList(srgLines));
    }

    public void addExtraSrgLines(Collection<String> srgLines)
    {
        this.extraSrgLines.addAll(srgLines);
    }

    // GETTERS AND STUF FOR DECOMP SPECIFIC STUFF
    // --------------------------------------------

    public File getFieldCsv()
    {
        return fieldCsv == null ? null : getProject().file(fieldCsv);
    }

    public void setFieldCsv(Object fieldCsv)
    {
        this.fieldCsv = fieldCsv;
    }

    public File getMethodCsv()
    {
        return methodCsv == null ? null : getProject().file(methodCsv);
    }

    public void setMethodCsv(Object methodCsv)
    {
        this.methodCsv = methodCsv;
    }

    public File getExceptorCfg()
    {
        return exceptorCfg == null ? null : getProject().file(exceptorCfg);
    }

    public void setExceptorCfg(Object file)
    {
        this.exceptorCfg = file;
    }

    public File getDeobfFile()
    {
        return deobfFile == null ? null : getProject().file(deobfFile);
    }

    public void setDeobfFile(Object deobfFile)
    {
        this.deobfFile = deobfFile;
    }

    public File getRecompFile()
    {
        return recompFile == null ? null : getProject().file(recompFile);
    }

    public void setRecompFile(Object recompFile)
    {
        this.recompFile = recompFile;
    }

    public boolean isDecomp()
    {
        return isDecomp;
    }

    public void setDecomp(boolean isDecomp)
    {
        this.isDecomp = isDecomp;
    }

    // EXTRA FANCY TRANSFORMERS
    // --------------------------------------------

    public List<ReobfTransformer> getPostTransformers()
    {
        return postTransformers; // Autobots! ROLL OUT!
    }

    public void addPostTransformer(ReobfTransformer autobot)
    {
        postTransformers.add(autobot);
    }

    public void addPostTransformer(Closure<byte[]> decepticon)
    {
        postTransformers.add(new ClosureTransformer(decepticon));
    }

    public List<ReobfTransformer> getPreTransformers()
    {
        return preTransformers; // Autobots! ROLL OUT!
    }

    public void addPreTransformer(ReobfTransformer autobot)
    {
        preTransformers.add(autobot);
    }

    public void addPreTransformer(Closure<byte[]> decepticon)
    {
        preTransformers.add(new ClosureTransformer(decepticon));
    }

    public static class ClosureTransformer implements ReobfTransformer
    {
        private static final long serialVersionUID = 1L;
        private Closure<byte[]>   closure;

        public ClosureTransformer(Closure<byte[]> closure)
        {
            super();
            this.closure = closure;
        }

        @Override
        public byte[] transform(byte[] data)
        {
            return closure.call(data);
        }
    }
}
