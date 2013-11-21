package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;
import net.md_5.specialsource.*;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaExecSpec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ProcessJarTask extends CachedTask
{
    @InputFile
    private DelayedFile inJar;

    @InputFile
    private DelayedFile exceptorJar;

    @InputFile
    private DelayedFile srg;

    @InputFile
    private DelayedFile exceptorCfg;

    @OutputFile
    @Cached
    private DelayedFile outCleanJar; // clean = pure forge, or pure FML
    
    @OutputFile
    @Cached
    private DelayedFile outDirtyJar = new DelayedFile(getProject(), "{BUILD_DIR}/deobfuscated.jar"); // dirty = has any other ATs

    @InputFiles
    private ArrayList<DelayedFile> ats = new ArrayList<DelayedFile>();
    
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
     *
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
        File tempObfJar = new File(getTemporaryDir(), "obfed.jar"); // courtesy of gradle temp dir.

        // make the ATs list.. its a Set to avoid duplication.
        Set<File> ats = new HashSet<File>();
        for (DelayedFile obj : this.ats)
        {
            ats.add(getProject().file(obj).getCanonicalFile());
        }

        // deobf
        getLogger().lifecycle("Applying SpecialSource...");
        deobfJar(getInJar(), tempObfJar, getSrg(), ats);
        
        File out = isClean ? getOutCleanJar() : getOutDirtyJar();

        // apply exceptor
        getLogger().lifecycle("Applying Exceptor...");
        applyExceptor(getExceptorJar(), tempObfJar, out, getExceptorCfg(), new File(getTemporaryDir(), "exceptorLog"));
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

    public void applyExceptor(final File injectorJar, final File inJar, final File outJar, final File config, final File log)
    {
        getLogger().debug("INPUT: " + inJar);
        getLogger().debug("OUTPUT: " + outJar);
        getLogger().debug("CONFIG: " + config);
        // http://www.gradle.org/docs/current/dsl/org.gradle.api.tasks.JavaExec.html
        getProject().javaexec(new Closure<JavaExecSpec>(this)
        {
            private static final long serialVersionUID = -1201498060683667405L;

            public JavaExecSpec call()
            {
                JavaExecSpec exec = (JavaExecSpec) getDelegate();

                exec.args(
                        injectorJar.getAbsolutePath(),
                        inJar.getAbsolutePath(),
                        outJar.getAbsolutePath(),
                        config.getAbsolutePath(),
                        log.getAbsolutePath()
                );

                //exec.jvmArgs("-jar", injectorJar.getAbsolutePath());

                exec.setMain("-jar");
                //exec.setExecutable(injectorJar);
                exec.setWorkingDir(injectorJar.getParentFile());

                exec.classpath(Constants.getClassPath());

                exec.setStandardOutput(Constants.getNullStream());

                return exec;
            }
        });
    }

    public File getExceptorCfg()
    {
        return exceptorCfg.call();
    }

    public void setExceptorCfg(DelayedFile exceptorCfg)
    {
        this.exceptorCfg = exceptorCfg;
    }

    public File getExceptorJar()
    {
        return exceptorJar.call();
    }

    public void setExceptorJar(DelayedFile exceptorJar)
    {
        this.exceptorJar = exceptorJar;
    }

    public File getInJar()
    {
        return inJar.call();
    }

    public void setInJar(DelayedFile inJar)
    {
        this.inJar = inJar;
    }

    public File getOutCleanJar()
    {
        return outCleanJar.call();
    }

    public void setOutCleanJar(DelayedFile outJar)
    {
        this.outCleanJar = outJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }

    public File getOutDirtyJar()
    {
        return outDirtyJar.call();
    }

    public void setOutDirtyJar(DelayedFile outDirtyJar)
    {
        this.outDirtyJar = outDirtyJar;
    }
}
