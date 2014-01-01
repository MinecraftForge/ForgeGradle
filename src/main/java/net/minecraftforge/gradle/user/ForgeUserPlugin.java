package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;
import static net.minecraftforge.gradle.common.Constants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;

import org.gradle.api.Task;
import org.gradle.api.tasks.bundling.Zip;

public class ForgeUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(FORGE_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(FORGE_DEOBF_MCP));
        }

        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(FORGE_DEOBF_SRG));
        }

        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("addForgeJavadoc");
    }

    @Override
    public void afterEvaluate()
    {
        String depBase = "net.minecraftforge:forge:" + getExtension().getApiVersion();
        project.getDependencies().add(CONFIG_USERDEV,      depBase + ":userdev");
        project.getDependencies().add(CONFIG_API_JAVADOCS, depBase + ":javadoc@zip");

        super.afterEvaluate();
    }

    @Override
    protected void addATs(ProcessJarTask task)
    {
        task.addTransformerClean(delayedFile(FML_AT));
        task.addTransformerClean(delayedFile(FORGE_AT));
    }

    @Override
    protected DelayedFile getBinPatchOut()
    {
        return delayedFile(FORGE_BINPATCHED);
    }

    @Override
    protected DelayedFile getDecompOut()
    {
        return delayedFile(FORGE_DECOMP);
    }

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        DelayedFile fmled           = delayedFile(isClean ? FORGE_FMLED : DECOMP_FMLED);
        DelayedFile fmlInjected     = delayedFile(isClean ? FORGE_FMLINJECTED : DECOMP_FMLINJECTED);
        DelayedFile remapped        = delayedFile(isClean ? FORGE_REMAPPED : DECOMP_REMAPPED);
        DelayedFile forged          = delayedFile(isClean ? FORGE_FORGED : DECOMP_FORGED);
        DelayedFile forgeJavaDocced = delayedFile(isClean ? FORGE_FORGEJAVADOCCED : DECOMP_FORGEJAVADOCCED);

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

            File injectFile = fmlInjected.call();
            inject.setDestinationDir(injectFile.getParentFile());
            inject.setArchiveName(injectFile.getName());
        }

        // Remap to MCP for forge patching -- no javadoc here
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn("addFmlSources");
            remap.setInJar(fmlInjected);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV));
            remap.setMethodsCsv(delayedFile(METHOD_CSV));
            remap.setParamsCsv(delayedFile(PARAM_CSV));
            remap.setDoesJavadocs(false);
        }

        PatchJarTask forgePatches = makeTask("doForgePatches", PatchJarTask.class);
        {
            forgePatches.dependsOn("remapJar");
            forgePatches.setInJar(remapped);
            forgePatches.setOutJar(forged);
            forgePatches.setInPatches(delayedFile(FORGE_PATCHES_ZIP));
        }

        // Post-inject javadocs
        RemapSourcesTask javadocRemap = makeTask("addForgeJavadoc", RemapSourcesTask.class);
        {
            javadocRemap.dependsOn("doForgePatches");
            javadocRemap.setInJar(forged);
            javadocRemap.setOutJar(forgeJavaDocced);
            javadocRemap.setFieldsCsv(delayedFile(FIELD_CSV));
            javadocRemap.setMethodsCsv(delayedFile(METHOD_CSV));
            javadocRemap.setParamsCsv(delayedFile(PARAM_CSV));
            javadocRemap.setDoesJavadocs(true);
        }

        project.getDependencies().add(CONFIG_API_SRC, project.files(forgeJavaDocced));
    }
}
