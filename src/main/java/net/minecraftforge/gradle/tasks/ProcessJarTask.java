package net.minecraftforge.gradle.tasks;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import joptsimple.internal.Strings;
import net.md_5.specialsource.AccessMap;
import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.RemapperProcessor;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.common.version.json.JsonFactory;
import net.minecraftforge.gradle.common.version.json.MCInjectorStruct;
import net.minecraftforge.gradle.common.version.json.MCInjectorStruct.InnerClass;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.Nullable;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import com.google.common.io.ByteStreams;

import static org.objectweb.asm.Opcodes.*;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import de.oceanlabs.mcp.mcinjector.MCInjectorImpl;

public class ProcessJarTask extends CachedTask
{
    @InputFile
    private DelayedFile            inJar;

    @InputFile
    private DelayedFile            srg;

    @InputFile
    private DelayedFile            exceptorCfg;

    @Nullable
    @InputFile
    private DelayedFile exceptorJson;

    private boolean applyMarkers = false;

    @OutputFile
    @Cached
    private DelayedFile outCleanJar; // clean = pure forge, or pure FML

    @OutputFile
    @Cached
    private DelayedFile            outDirtyJar = new DelayedFile(getProject(), Constants.DEOBF_JAR); // dirty = has any other ATs

    private ArrayList<DelayedFile> ats         = new ArrayList<DelayedFile>();

    private DelayedFile log;

    private boolean isClean = true;

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

        File log = getLog();
        if (log == null)
            log = new File(getTemporaryDir(), "exceptor.log");

        // apply exceptor
        getLogger().lifecycle("Applying Exceptor...");
        applyExceptor(tempObfJar, tempExcJar, getExceptorCfg(), log, ats);

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
        AccessMap accessMap = new AccessMap() {
            @Override
            public void addAccessChange(String symbolString, String accessString)
            {
                Iterable<String> split = Splitter.on(' ').omitEmptyStrings().split(symbolString);
                String joinedString = Joiner.on('.').join(split);
                super.addAccessChange(joinedString, accessString);
            }
        };
        getLogger().info("Using AccessTransformers...");
        for (File at : ats)
        {
            getLogger().info("" + at);
            accessMap.loadAccessTransformer(at);
        }

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
    }

    private int fixAccess(int access, String target)
    {
        int ret = access & ~7;
        int t = 0;

        if      (target.startsWith("public"))    t = ACC_PUBLIC;
        else if (target.startsWith("private"))   t = ACC_PRIVATE;
        else if (target.startsWith("protected")) t = ACC_PROTECTED;

        switch (access & 7)
        {
            case ACC_PRIVATE:   ret |= t; break;
            case 0:             ret |= (t != ACC_PRIVATE ? t : 0); break;
            case ACC_PROTECTED: ret |= (t != ACC_PRIVATE && t != 0 ? t : ACC_PROTECTED); break;
            case ACC_PUBLIC:    ret |= ACC_PUBLIC; break;
        }

        if      (target.endsWith("-f")) ret &= ~ACC_FINAL;
        else if (target.endsWith("+f")) ret |= ACC_FINAL;
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
                Files.readLines(at, Charset.defaultCharset(), new LineProcessor<Object>()
                {
                    @Override
                    public boolean processLine(String line) throws IOException
                    {
                        if (line.indexOf('#') != -1) line = line.substring(0, line.indexOf('#'));
                        line = line.trim().replace('.', '/');
                        if (line.isEmpty()) return true;

                        String[] s = line.split(" ");
                        if (s.length == 2 && s[1].indexOf('$') > 0)
                        {
                             String parent = s[1].substring(0, s[1].indexOf('$'));
                             MCInjectorStruct cls = struct.get(parent);
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

                        return true;
                    }

                    @Override public Object getResult() { return null; }
                });
            }
            File jsonTmp = new File(this.getTemporaryDir(), "transformed.json");
            json = jsonTmp.getCanonicalPath();
            Files.write(JsonFactory.GSON.toJson(struct).getBytes(), jsonTmp);
        }

        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        getLogger().debug("CONFIG: " + config);
        getLogger().debug("JSON: " + json);
        getLogger().debug("LOG: " + log);

        MCInjectorImpl.process(inJar.getCanonicalPath(),
                outJar.getCanonicalPath(),
                config.getCanonicalPath(),
                log.getCanonicalPath(),
                null,
                0,
                json,
                getApplyMarkers());
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

    public File getExceptorJson()
    {
        if (exceptorJson == null)
            return null;
        else
            return exceptorJson.call();
    }

    public void setExceptorJson(DelayedFile exceptorJson)
    {
        this.exceptorJson = exceptorJson;
    }

    public boolean getApplyMarkers()
    {
        return applyMarkers;
    }

    public void setApplyMarkers(boolean applyMarkers)
    {
        this.applyMarkers = applyMarkers;
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getLog()
    {
        if (log == null)
            return null;
        else
            return log.call();
    }

    public void setLog(DelayedFile Log)
    {
        this.log = Log;
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
