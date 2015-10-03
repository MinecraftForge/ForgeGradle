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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.mcp.ReobfExceptor;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.collect.Lists;
import com.google.common.io.Files;

class TaskReobfuscate extends DefaultTask
{
    //@formatter:off
    @InputFile  private Object inJar;
    @InputFile  private Object preFFJar;
    @InputFile  private Object srg;
    @InputFile  private Object exc;
    @InputFile  private Object methodsCsv;
    @InputFile  private Object fieldsCsv;
    @OutputFile private Object outJar;
    //@formatter: on
    
    @Input
    private LinkedList<String> extraSrg = new LinkedList<String>();
    
    @InputFiles
    private List<Object> libs = Lists.newArrayList();
    
    //@formatter:off
    public TaskReobfuscate() { super(); }
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException
    {
        File inJar = getInJar();
        File srg = getSrg();

        {
            ReobfExceptor exceptor = new ReobfExceptor();
            exceptor.toReobfJar = inJar;
            exceptor.deobfJar = getPreFFJar();
            exceptor.excConfig = getExc();
            exceptor.fieldCSV = getFieldsCsv();
            exceptor.methodCSV = getMethodsCsv();
            
            File outSrg =  new File(this.getTemporaryDir(), "reobf_cls.srg");
            
            exceptor.doFirstThings();
            exceptor.buildSrg(srg, outSrg);
            
            srg = outSrg;
        }
        
        // append SRG
        BufferedWriter writer = new BufferedWriter(new FileWriter(srg, true));
        for (String line : extraSrg)
        {
            writer.write(line);
            writer.newLine();
        }
        writer.flush();
        writer.close();

        obfuscate(inJar, getLibs(), srg);
    }

    private void obfuscate(File inJar, FileCollection classpath, File srg) throws FileNotFoundException, IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(Files.newReader(srg, Charset.defaultCharset()), null, null, false);

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping);

        // load jar
        Jar input = Jar.init(inJar);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));

        if (classpath != null)
            inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(Constants.toUrls(classpath))));

        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        File out = getOutJar();
        if (!out.getParentFile().exists()) //Needed because SS doesn't create it.
        {
            out.getParentFile().mkdirs();
        }

        // remap jar
        remapper.remapJar(input, getOutJar());
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
    
    public File getPreFFJar()
    {
        return getProject().file(preFFJar);
    }

    public void setPreFFJar(Object preFFJar)
    {
        this.preFFJar = preFFJar;
    }

    public File getSrg()
    {
        return getProject().file(srg);
    }

    public void setSrg(Object srg)
    {
        this.srg = srg;
    }

    public File getExc()
    {
        return getProject().file(exc);
    }

    public void setExc(Object exc)
    {
        this.exc = exc;
    }


    public File getMethodsCsv()
    {
        return getProject().file(methodsCsv);
    }

    public void setMethodsCsv(Object methodsCsv)
    {
        this.methodsCsv = methodsCsv;
    }

    public File getFieldsCsv()
    {
        return getProject().file(fieldsCsv);
    }

    public void setFieldsCsv(Object fieldsCsv)
    {
        this.fieldsCsv = fieldsCsv;
    }

    public LinkedList<String> getExtraSrg()
    {
        return extraSrg;
    }

    public void setExtraSrg(LinkedList<String> extraSrg)
    {
        this.extraSrg = extraSrg;
    }
    
    public FileCollection getLibs()
    {
        FileCollection collection = null;
        
        for (Object o : libs)
        {
            FileCollection col;
            if (o instanceof FileCollection)
            {
                col = (FileCollection) o;
            }
            else
            {
                col = getProject().files(o);
            }
            
            if (collection == null)
                collection = col;
            else
                collection = collection.plus(col);
        }
        
        return collection;
    }

    public void addLibs(Object libs)
    {
        this.libs.add(libs);
    }
}
