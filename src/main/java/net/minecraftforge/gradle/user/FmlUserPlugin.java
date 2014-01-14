package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;

public class FmlUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(FML_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(FML_DEOBF_MCP));
        }
        
        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(FML_DEOBF_SRG));
        }
        
        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("remapJar");
    }

    @Override
    public void afterEvaluate()
    {
        project.getDependencies().add(CONFIG_USERDEV, "cpw.mods:fml:" + getExtension().getApiVersion() + ":userdev");
        
        super.afterEvaluate();
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformerClean(delayedFile(FML_AT));
    }
    
    @Override
    protected DelayedFile getBinPatchOut()
    {
        return delayedFile(FML_BINPATCHED);
    }

    @Override
    protected DelayedFile getDecompOut()
    {
        return delayedFile(FML_DECOMP);
    }

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        DelayedFile fmled    = delayedFile(isClean ? FML_FMLED : Constants.DECOMP_FMLED);
        DelayedFile injected = delayedFile(isClean ? FML_INJECTED : Constants.DECOMP_FMLINJECTED);
        DelayedFile remapped = delayedFile(isClean ? FML_REMAPPED : Constants.DECOMP_REMAPPED);
        
        PatchJarTask fmlPatches = makeTask("doFmlPatches", PatchJarTask.class);
        {
            fmlPatches.dependsOn("decompile");
            fmlPatches.setInJar(decompOut);
            fmlPatches.setOutJar(fmled);
            fmlPatches.setInPatches(delayedFile(FML_PATCHES_ZIP));
        }
        
        final Zip inject = makeTask("addFmlSources", Zip.class);
        {
            inject.getOutputs().upToDateWhen(new Closure<Boolean>(null)
            {
                private static final long serialVersionUID = -8480140049890357630L;
                public Boolean call(Object o)
                {
                    return !inject.dependsOnTaskDidWork();
                }
            });
            inject.dependsOn("doFmlPatches");
            inject.from(fmled.toZipTree());
            inject.from(delayedFile(SRC_DIR));
            inject.from(delayedFile(RES_DIR));
            
            File injectFile = injected.call();
            inject.setDestinationDir(injectFile.getParentFile());
            inject.setArchiveName(injectFile.getName());
        }
        
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn("addFmlSources");
            remap.setInJar(injected);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV, FIELD_CSV_OLD));
            remap.setMethodsCsv(delayedFile(METHOD_CSV, METHOD_CSV_OLD));
            remap.setParamsCsv(delayedFile(PARAM_CSV, PARAM_CSV_OLD));
            remap.setDoesJavadocs(true);
        }
        
        project.getDependencies().add(CONFIG_API_SRC, project.files(remapped));
    }
}
