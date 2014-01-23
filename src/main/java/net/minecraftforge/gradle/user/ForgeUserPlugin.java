package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.user.RecompileTask;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.tasks.bundling.Zip;

import com.google.common.collect.ImmutableMap;

public class ForgeUserPlugin extends UserBasePlugin
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        ProcessJarTask procTask = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        {
            procTask.setInJar(delayedFile(FORGE_CACHE + FORGE_BINPATCHED));
            procTask.setOutCleanJar(delayedFile(FORGE_CACHE + FORGE_DEOBF_MCP));
            procTask.setOutDirtyJar(delayedFile(DIRTY_DIR + FORGE_DEOBF_MCP));
        }

        procTask = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");
        {
            procTask.setOutCleanJar(delayedFile(FORGE_CACHE + FORGE_DEOBF_SRG));
            procTask.setOutDirtyJar(delayedFile(DIRTY_DIR + FORGE_DEOBF_SRG));
        }

        Task task = project.getTasks().getByName("setupDecompWorkspace");
        task.dependsOn("genSrgs", "copyAssets", "extractNatives", "recompForge");
    }

    @Override
    public void afterEvaluate()
    {
        String depBase = "net.minecraftforge:forge:" + getExtension().getApiVersion();
        project.getDependencies().add(CONFIG_USERDEV, depBase + ":userdev@jar");

        super.afterEvaluate();
        
        {
            ProcessJarTask deobf = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
            boolean clean = deobf.isClean();
            
            DownloadTask dl = makeTask("getJavadocs", DownloadTask.class);
            dl.setUrl(delayedString(FORGE_JAVADOC_URL));
            dl.setOutput(delayedFile((clean ? getCacheDir() : DIRTY_DIR) + FORGE_JAVADOC));
            
            deobf.dependsOn(dl);
        }
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
        return delayedFile(FORGE_CACHE + FORGE_BINPATCHED);
    }

    @Override
    protected String getDecompOut()
    {
        return FORGE_DECOMP;
    }

    @Override
    protected String getCacheDir()
    {
        return FORGE_CACHE;
    }

    protected void createMcModuleDep(final boolean isClean, DependencyHandler depHandler, String depConfig)
    {
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addFlatRepo(project, "forgeFlatRepo", delayedFile(isClean ? FORGE_CACHE : DIRTY_DIR).call().getAbsolutePath());
            }
        });

        if (getExtension().isDecomp)
            depHandler.add("compile", ImmutableMap.of("name", "forgeSrc", "version", getExtension().getApiVersion()));
        else
            depHandler.add("compile", ImmutableMap.of("name", "forgeBin", "version", getExtension().getApiVersion()));
    }

    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        final String prefix = isClean ? FORGE_CACHE : DIRTY_DIR;
        DelayedFile fmled = delayedFile(prefix + FORGE_FMLED);
        DelayedFile fmlInjected = delayedFile(prefix + FORGE_FMLINJECTED);
        DelayedFile remapped = delayedFile(prefix + FORGE_REMAPPED);
        DelayedFile forged = delayedFile(prefix + FORGE_FORGED);
        DelayedFile forgeJavaDocced = delayedFile(prefix + FORGE_JAVADOCED);
        DelayedFile forgeRecomp = delayedFile(prefix + FORGE_RECOMP);

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
            inject.dependsOn(fmlPatches);
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
            remap.dependsOn(inject);
            remap.setInJar(fmlInjected);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV, FIELD_CSV_OLD));
            remap.setMethodsCsv(delayedFile(METHOD_CSV, METHOD_CSV_OLD));
            remap.setParamsCsv(delayedFile(PARAM_CSV, PARAM_CSV_OLD));
            remap.setDoesJavadocs(false);
        }

        PatchJarTask forgePatches = makeTask("doForgePatches", PatchJarTask.class);
        {
            forgePatches.dependsOn(remap);
            forgePatches.setInJar(remapped);
            forgePatches.setOutJar(forged);
            forgePatches.setInPatches(delayedFile(FORGE_PATCHES_ZIP));
        }

        // Post-inject javadocs
        RemapSourcesTask javadocRemap = makeTask("addForgeJavadoc", RemapSourcesTask.class);
        {
            javadocRemap.dependsOn(forgePatches);
            javadocRemap.setInJar(forged);
            javadocRemap.setOutJar(forgeJavaDocced);
            javadocRemap.setFieldsCsv(delayedFile(FIELD_CSV, FIELD_CSV_OLD));
            javadocRemap.setMethodsCsv(delayedFile(METHOD_CSV, METHOD_CSV_OLD));
            javadocRemap.setParamsCsv(delayedFile(PARAM_CSV, PARAM_CSV_OLD));
            javadocRemap.setDoesJavadocs(true);
        }

        RecompileTask recomp = makeTask("recompForge", RecompileTask.class);
        {
            recomp.setConfig(CONFIG);
            recomp.setInSrcJar(forgeJavaDocced);
            recomp.setOutJar(forgeRecomp);
            recomp.dependsOn(javadocRemap);
        }
    }
}
