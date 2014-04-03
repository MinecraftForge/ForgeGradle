package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
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
        task.dependsOn("genSrgs", "copyAssets", "extractNatives", "repackFml");
    }

    @Override
    public void afterEvaluate()
    {
        String apiVersion = getExtension().getApiVersion();
        int buildNumber = Integer.parseInt(apiVersion.substring(apiVersion.lastIndexOf('.') + 1));
        if (buildNumber < 883)
            throw new IllegalArgumentException("ForgeGradle 1.2 only works for FML versions 7.2.132.882+");
        
        project.getDependencies().add(CONFIG_USERDEV, "cpw.mods:fml:" + apiVersion + ":userdev");

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

    protected void createMcModuleDep(final boolean isClean, DependencyHandler depHandler, String depConfig)
    {
        final String repoDir = delayedFile(isClean ? FML_CACHE : DIRTY_DIR).call().getAbsolutePath();
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addFlatRepo(proj, "fmlFlatRepo", repoDir);
                proj.getLogger().info("Adding repo to " + proj.getPath() + " >> " +repoDir);
            }
        });

        final String prefix = isClean ? FML_CACHE : DIRTY_DIR;

        if (getExtension().isDecomp)
            depHandler.add(depConfig, ImmutableMap.of("name", "fmlSrc", "version", getExtension().getApiVersion()));
        else
            depHandler.add(depConfig, project.files(delayedFile(prefix + FML_BINPATCHED)));
    }
    
    @Override
    public void finalCall()
    {
        super.finalCall();
        
        if (getExtension().isDecomp)
        {
            boolean isClean = ((ProcessJarTask) project.getTasks().getByName("deobfuscateJar")).isClean();
            ((ReobfTask) project.getTasks().getByName("reobf")).setRecompFile(delayedFile((isClean ? FML_CACHE : DIRTY_DIR) + FML_RECOMP));
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        final String prefix = isClean ? FML_CACHE : DIRTY_DIR;
        final DelayedFile fmled = delayedFile(prefix + FML_FMLED);
        final DelayedFile injected = delayedFile(prefix + FML_INJECTED);
        final DelayedFile remapped = delayedFile(prefix + FML_REMAPPED);
        final DelayedFile recompJar = delayedFile(prefix + FML_RECOMP);

        final DelayedFile recompSrc = delayedFile(RECOMP_SRC_DIR);
        final DelayedFile recompCls = delayedFile(RECOMP_CLS_DIR);
        
        Spec onlyIfCheck = new Spec() {
            @Override
            public boolean isSatisfiedBy(Object obj)
            {
                boolean didWork = ((Task) obj).dependsOnTaskDidWork();
                boolean exists = recompJar.call().exists();
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

        final Zip inject = makeTask("addFmlSources", Zip.class);
        {
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
            remapTask.setFieldsCsv(delayedFile(FIELD_CSV));
            remapTask.setMethodsCsv(delayedFile(METHOD_CSV));
            remapTask.setParamsCsv(delayedFile(PARAM_CSV));
            remapTask.setDoesJavadocs(true);
        }

        // recomp stuff
        ExtractTask extract = makeTask("extractFmlSrc", ExtractTask.class);
        {
            extract.from(remapped);
            extract.into(recompSrc);
            extract.setIncludeEmptyDirs(false);
            extract.dependsOn(remapTask);

            extract.onlyIf(onlyIfCheck);
        }

        JavaCompile recompTask = makeTask("recompFml", JavaCompile.class);
        {
            recompTask.setSource(recompSrc);
            recompTask.setDestinationDir(recompCls.call());
            recompTask.setSourceCompatibility("1.6");
            recompTask.setTargetCompatibility("1.6");
            recompTask.setClasspath(project.getConfigurations().getByName(CONFIG_DEPS));
            recompTask.dependsOn(extract);

            recompTask.onlyIf(onlyIfCheck);
        }

        Jar repackageTask = makeTask("repackFml", Jar.class);
        {
            repackageTask.from(recompSrc);
            repackageTask.from(recompCls);
            repackageTask.exclude("*.java", "**/*.java", "**.java");
            repackageTask.dependsOn(recompTask);

            File out = recompJar.call();
            repackageTask.setArchiveName(out.getName());
            repackageTask.setDestinationDir(out.getParentFile());

            repackageTask.onlyIf(onlyIfCheck);
        }
    }
}
