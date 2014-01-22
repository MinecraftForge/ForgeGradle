package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.user.RecompileTask;

import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.bundling.Zip;

import com.google.common.collect.ImmutableMap;

public class FmlUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(FML_CACHE + FML_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(FML_CACHE + FML_DEOBF_MCP));
            procTask.setOutDirtyJar(delayedFile(DIRTY_DIR + FML_DEOBF_MCP));
        }

        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(FML_CACHE + FML_DEOBF_SRG));
            procTask.setOutDirtyJar(delayedFile(DIRTY_DIR + FML_DEOBF_SRG));
        }

        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("genSrgs", "copyAssets", "extractNatives", "recompFml");
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
        return delayedFile(FML_CACHE + FML_BINPATCHED);
    }

    @Override
    protected String getDecompOut()
    {
        return FML_DECOMP;
    }

    @Override
    protected String getCacheDir()
    {
        return FML_CACHE;
    }

    protected void createMcModuleDep(boolean isClean, DependencyHandler depHandler, String depConfig)
    {
        addFlatRepo(project, "fmlFlatRepo", delayedFile(isClean ? FML_CACHE : DIRTY_DIR).call());

        final String prefix = isClean ? FML_CACHE : DIRTY_DIR;

        if (getExtension().isDecomp)
            depHandler.add("compile", ImmutableMap.of("name", "fmlSrc", "version", getExtension().getApiVersion()));
        else
            depHandler.add("compile", project.files(delayedFile(prefix + FML_BINPATCHED)));
    }

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        final String prefix = isClean ? FML_CACHE : DIRTY_DIR;
        DelayedFile fmled = delayedFile(prefix + FML_FMLED);
        DelayedFile injected = delayedFile(prefix + FML_INJECTED);
        DelayedFile remapped = delayedFile(prefix + FML_REMAPPED);
        DelayedFile recompJar = delayedFile(prefix + FML_RECOMP);

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

        RemapSourcesTask remapTask = makeTask("remapJar", RemapSourcesTask.class);
        {
            remapTask.dependsOn("addFmlSources");
            remapTask.setInJar(injected);
            remapTask.setOutJar(remapped);
            remapTask.setFieldsCsv(delayedFile(FIELD_CSV, FIELD_CSV_OLD));
            remapTask.setMethodsCsv(delayedFile(METHOD_CSV, METHOD_CSV_OLD));
            remapTask.setParamsCsv(delayedFile(PARAM_CSV, PARAM_CSV_OLD));
            remapTask.setDoesJavadocs(true);
        }

        RecompileTask recomp = makeTask("recompFml", RecompileTask.class);
        {
            recomp.setConfig(CONFIG);
            recomp.setInSrcJar(remapped);
            recomp.setOutJar(recompJar);
            recomp.dependsOn(remapTask);
        }
    }
}
