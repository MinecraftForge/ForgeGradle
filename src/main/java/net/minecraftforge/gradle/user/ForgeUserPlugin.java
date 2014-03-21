package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.user.UserConstants.*;
import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.PatchJarTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.dsl.DependencyHandler;
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

    @SuppressWarnings({ "rawtypes", "serial"})
    @Override
    protected void doPostDecompTasks(boolean isClean, DelayedFile decompOut)
    {
        final String prefix = isClean ? FORGE_CACHE : DIRTY_DIR;
        DelayedFile fmled = delayedFile(prefix + FORGE_FMLED);
        DelayedFile injected = delayedFile(prefix + FORGE_FMLINJECTED);
        DelayedFile remapped = delayedFile(prefix + FORGE_REMAPPED);
        DelayedFile forged = delayedFile(prefix + FORGE_FORGED);
        DelayedFile forgeJavaDocced = delayedFile(prefix + FORGE_JAVADOCED);
        DelayedFile forgeRecomp = delayedFile(prefix + FORGE_RECOMP);
        
        DelayedFile recompSrc = delayedFile(RECOMP_SRC_DIR);
        DelayedFile recompCls = delayedFile(RECOMP_CLS_DIR);

        PatchJarTask fmlPatches = makeTask("doFmlPatches", PatchJarTask.class);
        {
            fmlPatches.dependsOn("decompile");
            fmlPatches.setInJar(decompOut);
            fmlPatches.setOutJar(fmled);
            fmlPatches.setInPatches(delayedFile(FML_PATCHES_ZIP));
        }

        final Zip inject = makeTask("addSources", Zip.class);
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

        // Remap to MCP for forge patching -- no javadoc here
        RemapSourcesTask remap = makeTask("remapJar", RemapSourcesTask.class);
        {
            remap.dependsOn(inject);
            remap.setInJar(forged);
            remap.setOutJar(remapped);
            remap.setFieldsCsv(delayedFile(FIELD_CSV, FIELD_CSV_OLD));
            remap.setMethodsCsv(delayedFile(METHOD_CSV, METHOD_CSV_OLD));
            remap.setParamsCsv(delayedFile(PARAM_CSV, PARAM_CSV_OLD));
            remap.setDoesJavadocs(true);
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

        // recomp stuff
        {
            ExtractTask extract = makeTask("extractForgeSrc", ExtractTask.class);
            {
                extract.from(forgeJavaDocced);
                extract.into(recompSrc);
                extract.setIncludeEmptyDirs(false);
                extract.dependsOn(javadocRemap);
                
                extract.onlyIf(new Closure(this, this) {
                    public Boolean call(Object obj)
                    {
                        return ((Task) obj).dependsOnTaskDidWork();
                    }
                });
            }
            
            JavaCompile recompTask = makeTask("recompForge", JavaCompile.class);
            {
                recompTask.setSource(recompSrc);
                recompTask.setDestinationDir(recompCls.call());
                recompTask.setSourceCompatibility("1.6");
                recompTask.setTargetCompatibility("1.6");
                recompTask.setClasspath(project.getConfigurations().getByName(CONFIG_DEPS));
                recompTask.dependsOn(extract);
                
                recompTask.onlyIf(new Closure(this, this) {
                    public Boolean call(Object obj)
                    {
                        return ((Task) obj).dependsOnTaskDidWork();
                    }
                });
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
                
                repackageTask.onlyIf(new Closure(this, this) {
                    public Boolean call(Object obj)
                    {
                        return ((Task) obj).dependsOnTaskDidWork();
                    }
                });
            }
        }
    }
}
