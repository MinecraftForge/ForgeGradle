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
package net.minecraftforge.gradle.tasks;

import static org.objectweb.asm.Opcodes.ACC_FINAL;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_PROTECTED;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import de.oceanlabs.mcp.mcinjector.LVTNaming;
import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;
import groovy.lang.Closure;
import net.md_5.specialsource.AccessMap;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.RemapperProcessor;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import net.minecraftforge.gradle.util.json.JsonFactory;
import net.minecraftforge.gradle.util.json.MCInjectorStruct;
import net.minecraftforge.gradle.util.json.MCInjectorStruct.InnerClass;

public class DeobfuscateJar extends CachedTask
{
    @InputFile
    @Optional
    private Object            fieldCsv;
    @InputFile
    @Optional
    private Object            methodCsv;

    @InputFile
    private Object            inJar;

    @InputFile
    private Object            srg;

    @InputFile
    private Object            exceptorCfg;

    @InputFile
    private Object            exceptorJson;

    @Input
    private boolean           applyMarkers  = false;

    @Input
    private boolean           failOnAtError = true;

    private Object            outJar;

    @InputFiles
    private ArrayList<Object> ats           = Lists.newArrayList();

    private Object            log;

    @TaskAction
    public void doTask() throws IOException
    {
        // make stuff into files.
        File tempObfJar = new File(getTemporaryDir(), "deobfed.jar"); // courtesy of gradle temp dir.
        File out = getOutJar();

        // make the ATs list.. its a Set to avoid duplication.
        Set<File> ats = new HashSet<File>();
        for (Object obj : this.ats)
        {
            ats.add(getProject().file(obj).getCanonicalFile());
        }

        // deobf
        getLogger().lifecycle("Applying SpecialSource...");
        deobfJar(getInJar(), tempObfJar, getSrg(), ats);

        File log = getLog();
        if (log == null)
            log = new File(getTemporaryDir(), "exceptor.log");

        // apply exceptor
        getLogger().lifecycle("Applying Exceptor...");
        applyExceptor(tempObfJar, out, getExceptorCfg(), log, ats);
    }

    private void deobfJar(File inJar, File outJar, File srg, Collection<File> ats) throws IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(srg);

        // load in ATs
        ErroringRemappingAccessMap accessMap = new ErroringRemappingAccessMap(new File[] { getMethodCsv(), getFieldCsv() });

        getLogger().info("Using AccessTransformers...");
        //Make SS shutup about access maps
        for (File at : ats)
        {
            getLogger().info("" + at);
            accessMap.loadAccessTransformer(at);
        }
        //        System.setOut(tmp);

        // make a processor out of the ATS and mappings.
        RemapperProcessor srgProcessor = new RemapperProcessor(null, mapping, null);

        RemapperProcessor atProcessor = new RemapperProcessor(null, null, accessMap);
        // make remapper
        JarRemapper remapper = new JarRemapper(srgProcessor, mapping, atProcessor);

        // load jar
        Jar input = Jar.init(inJar);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));
        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        // remap jar
        remapper.remapJar(input, outJar);

        // throw error for broken AT lines
        if (accessMap.brokenLines.size() > 0 && failOnAtError)
        {
            getLogger().error("{} Broken Access Transformer lines:", accessMap.brokenLines.size());
            for (String line : accessMap.brokenLines.values())
            {
                getLogger().error(" ---  {}", line);
            }

            // TODO: add info for disabling

            throw new RuntimeException("Your Access Transformers be broke!");
        }
    }

    private int fixAccess(int access, String target)
    {
        int ret = access & ~7;
        int t = 0;

        if (target.startsWith("public"))
            t = ACC_PUBLIC;
        else if (target.startsWith("private"))
            t = ACC_PRIVATE;
        else if (target.startsWith("protected"))
            t = ACC_PROTECTED;

        switch (access & 7)
            {
                case ACC_PRIVATE:
                    ret |= t;
                    break;
                case 0:
                    ret |= (t != ACC_PRIVATE ? t : 0);
                    break;
                case ACC_PROTECTED:
                    ret |= (t != ACC_PRIVATE && t != 0 ? t : ACC_PROTECTED);
                    break;
                case ACC_PUBLIC:
                    ret |= ACC_PUBLIC;
                    break;
            }

        if (target.endsWith("-f"))
            ret &= ~ACC_FINAL;
        else if (target.endsWith("+f"))
            ret |= ACC_FINAL;
        return ret;
    }

    public void applyExceptor(File inJar, File outJar, File config, File log, Set<File> ats) throws IOException
    {
        String json = null;
        File getJson = getExceptorJson();
        if (getJson != null)
        {
            final Map<String, MCInjectorStruct> struct = JsonFactory.loadMCIJson(getJson);
            for (File at : ats)
            {
                getLogger().info("loading AT: " + at.getCanonicalPath());

                Files.readLines(at, Charset.defaultCharset(), new LineProcessor<Object>()
                {
                    @Override
                    public boolean processLine(String line) throws IOException
                    {
                        if (line.indexOf('#') != -1)
                            line = line.substring(0, line.indexOf('#'));
                        line = line.trim().replace('.', '/');
                        if (line.isEmpty())
                            return true;

                        String[] s = line.split(" ");
                        if (s.length == 2 && s[1].indexOf('$') > 0)
                        {
                            String parent = s[1].substring(0, s[1].indexOf('$'));
                            for (MCInjectorStruct cls : new MCInjectorStruct[] { struct.get(parent), struct.get(s[1]) })
                            {
                                if (cls != null && cls.innerClasses != null)
                                {
                                    for (InnerClass inner : cls.innerClasses)
                                    {
                                        if (inner.inner_class.equals(s[1]))
                                        {
                                            int access = fixAccess(inner.getAccess(), s[0]);
                                            inner.access = (access == 0 ? null : Integer.toHexString(access));
                                        }
                                    }
                                }
                            }
                        }

                        return true;
                    }

                    @Override
                    public Object getResult()
                    {
                        return null;
                    }
                });
            }

            // Remove unknown classes from configuration
            removeUnknownClasses(inJar, struct);

            File jsonTmp = new File(this.getTemporaryDir(), "transformed.json");
            json = jsonTmp.getCanonicalPath();
            Files.write(JsonFactory.GSON.toJson(struct).getBytes(), jsonTmp);
        }

        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        getLogger().debug("CONFIG: " + config);
        getLogger().debug("JSON: " + json);
        getLogger().debug("LOG: " + log);
        getLogger().debug("PARAMS: true");

        MCInjectorImpl.process(inJar.getCanonicalPath(),
                outJar.getCanonicalPath(),
                config.getCanonicalPath(),
                log.getCanonicalPath(),
                null,
                0,
                json,
                isApplyMarkers(),
                true,
                LVTNaming.LVT
                );
    }

    private void removeUnknownClasses(File inJar, Map<String, MCInjectorStruct> config) throws IOException
    {
        ZipFile zip = new ZipFile(inJar);
        try
        {
            Iterator<Map.Entry<String, MCInjectorStruct>> entries = config.entrySet().iterator();
            while (entries.hasNext())
            {
                Map.Entry<String, MCInjectorStruct> entry = entries.next();
                String className = entry.getKey();

                // Verify the configuration contains only classes we actually have
                if (zip.getEntry(className + ".class") == null)
                {
                    getLogger().info("Removing unknown class {}", className);
                    entries.remove();
                    continue;
                }

                MCInjectorStruct struct = entry.getValue();

                // Verify the inner classes in the configuration actually exist in our deobfuscated JAR file
                if (struct.innerClasses != null)
                {
                    Iterator<InnerClass> innerClasses = struct.innerClasses.iterator();
                    while (innerClasses.hasNext())
                    {
                        InnerClass innerClass = innerClasses.next();
                        if (zip.getEntry(innerClass.inner_class + ".class") == null)
                        {
                            getLogger().info("Removing unknown inner class {} from {}", innerClass.inner_class, className);
                            innerClasses.remove();
                        }
                    }
                }
            }
        }
        finally
        {
            zip.close();
        }
    }

    public File getExceptorCfg()
    {
        return getProject().file(exceptorCfg);
    }

    public void setExceptorCfg(Object exceptorCfg)
    {
        this.exceptorCfg = exceptorCfg;
    }

    public File getExceptorJson()
    {
        if (exceptorJson == null)
            return null;
        else
            return getProject().file(exceptorJson);
    }

    public void setExceptorJson(Object exceptorJson)
    {
        this.exceptorJson = exceptorJson;
    }

    public boolean isApplyMarkers()
    {
        return applyMarkers;
    }

    public void setApplyMarkers(boolean applyMarkers)
    {
        this.applyMarkers = applyMarkers;
    }

    public boolean isFailOnAtError()
    {
        return failOnAtError;
    }

    public void setFailOnAtError(boolean failOnAtError)
    {
        this.failOnAtError = failOnAtError;
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getLog()
    {
        if (log == null)
            return null;
        else
            return getProject().file(log);
    }

    public void setLog(Object log)
    {
        this.log = log;
    }

    public File getSrg()
    {
        return getProject().file(srg);
    }

    public void setSrg(Object srg)
    {
        this.srg = srg;
    }

    /**
     * returns the actual output file depending on Clean status
     * @return File representing output jar
     */
    @Cached
    @OutputFile
    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }

    /**
     * returns the actual output Object depending on Clean status
     * Unlike getOutputJar() this method does not resolve the files.
     * @return Object that will resolve to
     */
    @SuppressWarnings("serial")
    public Closure<File> getDelayedOutput()
    {
        return new Closure<File>(DeobfuscateJar.class) {
            public File call()
            {
                return getOutJar();
            }
        };
    }

    /**
     * adds an access transformer to the deobfuscation of this
     * @param obj access transformers
     */
    public void addAt(Object obj)
    {
        ats.add(obj);
    }
    
    /**
     * adds access transformers to the deobfuscation of this
     * @param objs access transformers
     */
    public void addAts(Object... objs)
    {
        for (Object object : objs)
        {
            ats.add(object);
        }
    }
    
    /**
     * adds access transformers to the deobfuscation of this
     * @param objs access transformers
     */
    public void addAts(Iterable<Object> objs)
    {
        for (Object object : objs)
        {
            ats.add(object);
        }
    }
    
    public FileCollection getAts()
    {
        return getProject().files(ats.toArray());
    }

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

    private static final class ErroringRemappingAccessMap extends AccessMap
    {
        private final Map<String, String> renames     = Maps.newHashMap();
        public final Map<String, String>  brokenLines = Maps.newHashMap();

        public ErroringRemappingAccessMap(File[] renameCsvs) throws IOException
        {
            super();

            for (File f : renameCsvs)
            {
                if (f == null)
                    continue;
                Files.readLines(f, Charsets.UTF_8, new LineProcessor<String>()
                {
                    @Override
                    public boolean processLine(String line) throws IOException
                    {
                        String[] pts = line.split(",");
                        if (!"searge".equals(pts[0]))
                        {
                            renames.put(pts[0], pts[1]);
                        }

                        return true;
                    }

                    @Override
                    public String getResult()
                    {
                        return null;
                    }
                });
            }
        }

        @Override
        public void loadAccessTransformer(File file) throws IOException
        {
            // because SS doesnt close its freaking reader...
            BufferedReader reader = Files.newReader(file, Constants.CHARSET);
            loadAccessTransformer(reader);
            reader.close();
        }

        @Override
        public void addAccessChange(String symbolString, String accessString)
        {
            String[] pts = symbolString.split(" ");
            if (pts.length >= 2)
            {
                int idx = pts[1].indexOf('(');

                String start = pts[1];
                String end = "";

                if (idx != -1)
                {
                    start = pts[1].substring(0, idx);
                    end = pts[1].substring(idx);
                }

                String rename = renames.get(start);
                if (rename != null)
                {
                    pts[1] = rename + end;
                }
            }
            String joinedString = Joiner.on('.').join(pts);
            super.addAccessChange(joinedString, accessString);
            // convert  package.class  to  package/class
            brokenLines.put(joinedString.replace('.', '/'), symbolString);
        }

        @Override
        protected void accessApplied(String key, int oldAccess, int newAccess)
        {
            // if the access' are equal, then the line is broken, and we dont want to remove it.\
            // or not... it still applied.. just applied twice somehow.. not an issue.
//            if (oldAccess != newAccess)
            {
                // key added before is in format: package/class{method/field sig}
                // and the key here is in format: package/class {method/field sig}
                brokenLines.remove(key.replace(" ", ""));
            }
        }
    }
}
