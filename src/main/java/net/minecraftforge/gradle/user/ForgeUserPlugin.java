package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.api.tasks.compile.JavaCompile;

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
        task.dependsOn("genSrgs", "copyAssets", "extractNatives", "repackForge");
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
        final String repoDir = delayedFile(isClean ? FORGE_CACHE : DIRTY_DIR).call().getAbsolutePath();
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addFlatRepo(proj, "forgeFlatRepo", repoDir);
                proj.getLogger().info("Adding repo to " + proj.getPath() + " >> " +repoDir);
            }
        });

        if (getExtension().isDecomp)
            depHandler.add(depConfig, ImmutableMap.of("name", "forgeSrc", "version", getExtension().getApiVersion()));
        else
            depHandler.add(depConfig, ImmutableMap.of("name", "forgeBin", "version", getExtension().getApiVersion()));
    }
    
    @Override
    public void finalCall()
    {
        super.finalCall();
        
        if (getExtension().isDecomp)
        {
            boolean isClean = ((ProcessJarTask) project.getTasks().getByName("deobfuscateJar")).isClean();
            ((ReobfTask) project.getTasks().getByName("reobf")).setRecompFile(delayedFile((isClean ? FORGE_CACHE : DIRTY_DIR) + FORGE_RECOMP));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        final String prefix = isClean ? FORGE_CACHE : DIRTY_DIR;
        final DelayedFile fmled = delayedFile(prefix + FORGE_FMLED);
        final DelayedFile injected = delayedFile(prefix + FORGE_FMLINJECTED);
        final DelayedFile forged = delayedFile(prefix + FORGE_FORGED);
        final DelayedFile remapped = delayedFile(prefix + FORGE_REMAPPED);
        final DelayedFile forgeRecomp = delayedFile(prefix + FORGE_RECOMP);
        
        DelayedFile recompSrc = delayedFile(RECOMP_SRC_DIR);
        DelayedFile recompCls = delayedFile(RECOMP_CLS_DIR);
        
        Spec onlyIfCheck = new Spec() {
            @Override
            public boolean isSatisfiedBy(Object obj)
            {
                boolean didWork = ((Task) obj).dependsOnTaskDidWork();
                boolean exists = forgeRecomp.call().exists();
                if (!exists)
                    return true;
                else
                    return didWork;
            }
        };

        final PatchJarTask fmlPatches = makeTask("doFmlPatches", PatchJarTask.class);
        {
            fmlPatches.dependsOn("decompile");
            fmlPatches.setInJar(decompOut);
            fmlPatches.setOutJar(fmled);
            fmlPatches.setInPatches(delayedFile(FML_PATCHES_ZIP));
        }

        final Zip inject = makeTask("addSources", Zip.class);
        {
            inject.dependsOn(fmlPatches);
            inject.from(fmled.toZipTree());
            inject.from(delayedFile(SRC_DIR));
            inject.from(delayedFile(RES_DIR));
            
            inject.onlyIf(new Spec()
            {
                public boolean isSatisfiedBy(Object o)
                {
                    boolean didWork = fmlPatches.getDidWork();
                    boolean exists = injected.call().exists();
                    if (!exists)
                        return true;
                    else
                        return didWork;
                }
            });

            File injectFile = injected.call();
            inject.setDestinationDir(injectFile.getParentFile());
            inject.setArchiveName(injectFile.getName());
        }
        
        PatchJarTask forgePatches = makeTask("doForgePatches", PatchJarTask.class);
        {
            forgePatches.dependsOn(inject);
            forgePatches.setInJar(injected);
            forgePatches.setOutJar(forged);
            forgePatches.setInPatches(delayedFile(FORGE_PATCHES_ZIP));
        }

        // Remap to MCP names
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn(forgePatches);
            remap.setInJar(forged);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV));
            remap.setMethodsCsv(delayedFile(METHOD_CSV));
            remap.setParamsCsv(delayedFile(PARAM_CSV));
            remap.setDoesJavadocs(true);
        }

        // recomp stuff
        {
            ExtractTask extract = makeTask("extractForgeSrc", ExtractTask.class);
            {
                extract.from(remapped);
                extract.into(recompSrc);
                extract.setIncludeEmptyDirs(false);
                extract.dependsOn(remap);
                
                extract.onlyIf(onlyIfCheck);
            }
            
            JavaCompile recompTask = makeTask("recompForge", JavaCompile.class);
            {
                recompTask.setSource(recompSrc);
                recompTask.setDestinationDir(recompCls.call());
                recompTask.setSourceCompatibility("1.6");
                recompTask.setTargetCompatibility("1.6");
                recompTask.setClasspath(project.getConfigurations().getByName(CONFIG_DEPS));
                recompTask.dependsOn(extract);
                
                recompTask.onlyIf(onlyIfCheck);
            }
            
            Jar repackageTask = makeTask("repackForge", Jar.class);
            {
                repackageTask.from(recompSrc);
                repackageTask.from(recompCls);
                repackageTask.exclude("*.java", "**/*.java", "**.java");
                repackageTask.dependsOn(recompTask);
                
                File out = forgeRecomp.call();
                repackageTask.setArchiveName(out.getName());
                repackageTask.setDestinationDir(out.getParentFile());
                
                repackageTask.onlyIf(onlyIfCheck);
            }
        }
    }
}
