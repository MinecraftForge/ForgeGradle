package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.dev.PatcherConstants.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.gradle.GradleConfigurationException;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJarTask;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.patcher.GenDevProjectsTask;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

public class PatcherPlugin extends BasePlugin<PatcherExtension>
{
    @Override
    public void applyPlugin()
    {
        // create and add the namedDomainObjectContainer to the extension object
        
        NamedDomainObjectContainer<PatcherProject> container = project.container(PatcherProject.class, new PatcherProjectFactory(this));
        getExtension().setProjectContainer(container);
        container.whenObjectAdded(new Action<PatcherProject>() {
            @Override
            public void execute(PatcherProject arg0)
            {
                createProject(arg0);
            }
            
        });
        container.whenObjectRemoved(new Action<PatcherProject>() {
            @Override
            public void execute(PatcherProject arg0)
            {
                removeProject(arg0);
            }
            
        });
        
        makeTask(TASK_SETUP);
        
        makeTasks();
    }
    
    protected void makeTasks()
    {
        DeobfuscateJarTask deobfJar = makeTask(TASK_DEOBF_JAR, DeobfuscateJarTask.class);
        {
            deobfJar.setInJar(delayedFile(Constants.JAR_MERGED));
            deobfJar.setOutCleanJar(delayedFile(JAR_DEOBF));
            deobfJar.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfJar.setExceptorCfg(delayedFile(EXC_SRG));
            deobfJar.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfJar.setApplyMarkers(true);
            deobfJar.setDoesCache(false);
            // access transformers are added afterEvaluate
            deobfJar.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
        }
        
        ApplyFernFlowerTask decompileJar = makeTask("decompileJar", ApplyFernFlowerTask.class);
        {
            decompileJar.setInJar(delayedFile(JAR_DEOBF));
            decompileJar.setOutJar(delayedFile(JAR_DECOMP));
            decompileJar.setFernflower(delayedFile(Constants.JAR_FERNFLOWER));
            decompileJar.setDoesCache(false);
            decompileJar.dependsOn(TASK_DL_FERNFLOWER, deobfJar);
        }
        
        PostDecompileTask postDecompileJar = makeTask("sourceProcessJar", PostDecompileTask.class);
        {
            postDecompileJar.setInJar(delayedFile(JAR_DECOMP));
            postDecompileJar.setOutJar(delayedFile(JAR_DECOMP_POST));
            postDecompileJar.setPatches(delayedFile(MCP_PATCHES_CLIENT));
            postDecompileJar.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecompileJar.setDoesCache(false);
            postDecompileJar.dependsOn(decompileJar);
        }
        
        ProcessSrcJarTask patchJar = makeTask(TASK_PATCH_JAR, ProcessSrcJarTask.class);
        {
            patchJar.setInJar(delayedFile(JAR_DECOMP_POST));
            patchJar.setOutJar(new File(project.getBuildDir(), "tmp/unneededPatched.jar"));
            patchJar.setDoesCache(false);
            patchJar.setMaxFuzz(2);
            patchJar.dependsOn(postDecompileJar);
        }
        
        GenDevProjectsTask createProjects = makeTask(TASK_GEN_PROJECTS, GenDevProjectsTask.class);
        {
            createProjects.setWorkspaceDir(getExtension().getDelayedWorkspaceDir());
            createProjects.addRepo("minecraft", Constants.URL_LIBRARY);
            createProjects.putProject("clean", null, null, null, null);
            createProjects.setJavaLevel("1.6");
            
            //TODO: add MC libs
        }

        // Clean project stuff

        RemapSourcesTask remapCleanTask = makeTask("remapCleanJar", RemapSourcesTask.class);
        {
            remapCleanTask.setInJar(delayedFile(JAR_DECOMP));
            remapCleanTask.setOutJar(delayedFile(JAR_REMAPPED));
            remapCleanTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapCleanTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapCleanTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapCleanTask.setAddsJavadocs(false);
            remapCleanTask.setDoesCache(false);
            remapCleanTask.dependsOn(postDecompileJar);
        }
        
        Object delayedRemapped = delayedTree(JAR_REMAPPED);
        Copy extract = makeTask("extractCleanSources", Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(getExtension().getDelayedSubWorkspaceDir("clean/src/main/java"));
            extract.include("*.java", "**/*.java");
            extract.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }
        
        extract = makeTask("extractCleanResources", Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(getExtension().getDelayedSubWorkspaceDir("clean/src/main/resources"));
            extract.exclude("*.java", "**/*.java");
            extract.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }
        
        // add setup depends
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        setupTask.dependsOn("extractCleanSources");
        setupTask.dependsOn("extractCleanResources");
    }
    
    protected void createProject(PatcherProject patcher)
    {
        RemapSourcesTask remapTask = makeTask(String.format(TASK_PROJECT_REMAP_JAR, patcher.getName()), RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(String.format(JAR_PATCHED_PROJECT, patcher.getName())));
            remapTask.setOutJar(delayedFile(String.format(JAR_REMAPPED_PROJECT, patcher.getName())));
            remapTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapTask.setAddsJavadocs(false);
            remapTask.setDoesCache(false);
            remapTask.dependsOn(TASK_PATCH_JAR);
        }
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).putProject(patcher.getName(),
                patcher.getDelayedSourcesDir(),
                patcher.getDelayedResourcesDir(),
                patcher.getDelayedTestSourcesDir(),
                patcher.getDelayedTestResourcesDir());
        
        
        Object delayedRemapped = delayedTree(String.format(JAR_REMAPPED_PROJECT, patcher.getName()));
        
        Copy extract = makeTask(String.format(TASK_PROJECT_EXTRACT_SRC, patcher.getName()), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(getExtension().getDelayedSubWorkspaceDir(patcher.getName() + "/src/main/java"));
            extract.include("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
        
        extract = makeTask(String.format(TASK_PROJECT_EXTRACT_RES, patcher.getName()), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(getExtension().getDelayedSubWorkspaceDir(patcher.getName() + "/src/main/resources"));
            extract.exclude("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
    }
    
    protected void removeProject(PatcherProject patcher)
    {
        project.getTasks().remove(project.getTasks().getByName(String.format(TASK_PROJECT_REMAP_JAR, patcher.getName())));
        
        project.getTasks().remove(project.getTasks().getByName(String.format(TASK_PROJECT_EXTRACT_SRC, patcher.getName())));
        project.getTasks().remove(project.getTasks().getByName(String.format(TASK_PROJECT_EXTRACT_RES, patcher.getName())));
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).removeProject(patcher.getName());
    }

    @Override
    protected void addReplaceTokens(PatcherExtension ext)
    {
        // use this? or not use this?
    }
    
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        List<PatcherProject> patchersList = sortByPatching(getExtension().getProjects());
        
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        ProcessSrcJarTask patchJar = (ProcessSrcJarTask) project.getTasks().getByName(TASK_PATCH_JAR);
        DeobfuscateJarTask deobfJar = (DeobfuscateJarTask) project.getTasks().getByName(TASK_DEOBF_JAR);
        
        for (PatcherProject patcher : patchersList)
        {
            patchJar.addStage(
                    patcher.getName(),
                    patcher.getDelayedPatchDir(), 
                    delayedFile(String.format(JAR_PATCHED_PROJECT, patcher.getName())),
                    patcher.getDelayedSourcesDir(),
                    patcher.getDelayedResourcesDir());
            
            // TODO: make it the project creation tasks
            setupTask.dependsOn(String.format(TASK_PROJECT_EXTRACT_SRC, patcher.getName()));
            setupTask.dependsOn(String.format(TASK_PROJECT_EXTRACT_RES, patcher.getName()));
            
            // get Ats
            for (File at : project.fileTree(patcher.getResourcesDir()))
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    deobfJar.addTransformerClean(at);
                }
            }
        }
    }
    
    /**
     * Sorts the project into the list of patches on each other.
     * Throws GradleConfigurationException if the projects cannot be fitted into the list.
     * Doesnt support potential patching loops, but the clean project cant patch anything, so its unlikely to happen.
     * @return list of sorted projects
     */
    private List<PatcherProject> sortByPatching(NamedDomainObjectContainer<PatcherProject> projects)
    {
        // patcher->patched
        BiMap<PatcherProject, PatcherProject> tempMap = HashBiMap.create();
        
        for (PatcherProject project : projects)
        {
            String patchAfter = project.getPatchAfter();
            PatcherProject toPut;
            
            if (patchAfter.equals("clean"))
            {
                toPut = null;
            }
            else
            {
                toPut = projects.findByName(patchAfter);
            
                if (toPut == null)
                    throw new GradleConfigurationException("Project " + patchAfter + " does not exist! You cannot patch after it!");
            }
            
            try {
                tempMap.put(project, toPut);
            }
            catch(IllegalArgumentException e)
            {
                // must exist already.. thus a duplicate value..
                throw new GradleConfigurationException("2 projects cannot patch after the same project '"+ toPut == null ? "clean" : toPut.getName() + "'!");
            }
        }
        
        // now  patched->patcher
        tempMap = tempMap.inverse();
        
        ArrayList<PatcherProject> list = new ArrayList<PatcherProject>(projects.size());
        PatcherProject key = tempMap.remove(null); // null is clean
        while (key != null)
        {
            list.add(key);
            key = tempMap.remove(key);
        }
        
        return list;
    }
    
    //@formatter:off
    @Override public boolean canOverlayPlugin() { return false; }
    @Override protected void applyOverlayPlugin() { }
    @Override protected PatcherExtension getOverlayExtension() { return null; }
}
