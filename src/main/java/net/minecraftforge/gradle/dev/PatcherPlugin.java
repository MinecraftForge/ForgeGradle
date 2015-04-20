package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.dev.PatcherConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.gradle.GradleConfigurationException;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.json.version.Library;
import net.minecraftforge.gradle.json.version.Version;
import net.minecraftforge.gradle.tasks.ApplyFernFlowerTask;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.DeobfuscateJarTask;
import net.minecraftforge.gradle.tasks.GenEclipseRunTask;
import net.minecraftforge.gradle.tasks.PostDecompileTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.patcher.GenDevProjectsTask;
import net.minecraftforge.gradle.tasks.patcher.GenIdeaRunTask;
import net.minecraftforge.gradle.tasks.patcher.SubprojectCall;

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
            createProjects.putProject("Clean", null, null, null, null);
            createProjects.setJavaLevel("1.6");
        }
        
        SubprojectCall makeIdeProjects = makeTask(TASK_GEN_IDES, SubprojectCall.class);
        {
            makeIdeProjects.setProjectDir(getExtension().getDelayedWorkspaceDir());
            makeIdeProjects.setCallLine("cleanEclipse cleanIdea eclipse idea");
            makeIdeProjects.dependsOn(createProjects);
        }

        // Clean project ONLY stuff ---------------------------------------------------------------

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
        
        Copy extractSrc = makeTask("extractCleanSources", Copy.class);
        {
            extractSrc.from(delayedRemapped);
            extractSrc.into(subWorkspace("Clean" + DIR_EXTRACTED_SRC));
            extractSrc.include("*.java", "**/*.java");
            extractSrc.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }
        
        Copy extractRes = makeTask("extractCleanResources", Copy.class);
        {
            extractRes.from(delayedRemapped);
            extractRes.into(subWorkspace("Clean" + DIR_EXTRACTED_RES));
            extractRes.exclude("*.java", "**/*.java");
            extractRes.dependsOn(remapCleanTask, TASK_GEN_PROJECTS);
        }
        
        CreateStartTask makeStart = makeTask("makeCleanStart", CreateStartTask.class);
        {
            for (String resource : GRADLE_START_RESOURCES)
            {
                makeStart.addResource(resource);
            }
            
            makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
            makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(DIR_ASSETS));
            makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(Constants.DIR_NATIVES));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_DATA));
            makeStart.addReplacement("@@BOUNCERCLIENT@@", "net.minecraft.client.main.Main");
            makeStart.addReplacement("@@TWEAKERCLIENT@@", "");
            makeStart.addReplacement("@@BOUNCERSERVER@@", "net.minecraft.server.MinecraftServer");
            makeStart.addReplacement("@@TWEAKERSERVER@@", "");
            makeStart.setStartOut(subWorkspace("Clean" + DIR_EXTRACTED_START));
            makeStart.setDoesCache(false);
            makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS, TASK_EXTRACT_NATIVES);
        }
        
        GenEclipseRunTask eclipseRunConfigClient = makeTask("makeEclipseCleanRunClient", GenEclipseRunTask.class);
        {
            eclipseRunConfigClient.setMainClass("net.minecraft.client.main.Main");
            eclipseRunConfigClient.setProjectName("Clean");
            eclipseRunConfigClient.setOutputFile(subWorkspace("Clean/Clean Client.launch"));
            eclipseRunConfigClient.setRunDir("${workspace_loc}/run");
            eclipseRunConfigClient.dependsOn(makeStart, makeIdeProjects);
        }
        
        GenEclipseRunTask eclipseRunConfigServer = makeTask("makeEclipseCleanRunServer", GenEclipseRunTask.class);
        {
            eclipseRunConfigServer.setMainClass("net.minecraft.server.MinecraftServer");
            eclipseRunConfigServer.setProjectName("Clean");
            eclipseRunConfigServer.setOutputFile(subWorkspace("Clean/Clean Server.launch"));
            eclipseRunConfigServer.setRunDir("${workspace_loc}/run");
            eclipseRunConfigServer.dependsOn(makeStart, makeIdeProjects);
        }
        
        GenIdeaRunTask ideaRunConfigClient = makeTask("makeIdeaCleanRunClient", GenIdeaRunTask.class);
        {
            ideaRunConfigClient.setMainClass("net.minecraft.client.main.Main");
            ideaRunConfigClient.setProjectName("Clean");
            ideaRunConfigClient.setConfigName("Clean Client");
            ideaRunConfigClient.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Client.xml"));
            ideaRunConfigClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunConfigClient.dependsOn(makeStart, makeIdeProjects);
        }
        
        GenIdeaRunTask ideaRunConfigServer = makeTask("makeIdeaCleanRunServer", GenIdeaRunTask.class);
        {
            ideaRunConfigServer.setMainClass("net.minecraft.server.MinecraftServer");
            ideaRunConfigServer.setProjectName("Clean");
            ideaRunConfigServer.setConfigName("Clean Server");
            ideaRunConfigServer.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Server.xml"));
            ideaRunConfigServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunConfigServer.dependsOn(makeStart, makeIdeProjects);
        }
        
        // add depends
        makeIdeProjects.dependsOn(extractSrc, extractRes);
        
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        setupTask.dependsOn(eclipseRunConfigClient, eclipseRunConfigServer, ideaRunConfigClient, ideaRunConfigServer);
    }
    
    protected void createProject(PatcherProject patcher)
    {
        RemapSourcesTask remapTask = makeTask(getProjectTask(TASK_PROJECT_REMAP_JAR, patcher), RemapSourcesTask.class);
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
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).putProject(patcher.getCapName(),
                patcher.getDelayedSourcesDir(),
                patcher.getDelayedResourcesDir(),
                patcher.getDelayedTestSourcesDir(),
                patcher.getDelayedTestResourcesDir());
        
        
        Object delayedRemapped = delayedTree(String.format(JAR_REMAPPED_PROJECT, patcher.getName()));
        
        Copy extract = makeTask(getProjectTask(TASK_PROJECT_EXTRACT_SRC, patcher), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));
            extract.include("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
        
        extract = makeTask(getProjectTask(TASK_PROJECT_EXTRACT_RES, patcher), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_RES));
            extract.exclude("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
        
        CreateStartTask makeStart = makeTask(getProjectTask(TASK_PROJECT_MAKE_START, patcher), CreateStartTask.class);
        {
            for (String resource : GRADLE_START_RESOURCES)
            {
                makeStart.addResource(resource);
            }
            
            makeStart.addReplacement("@@ASSETINDEX@@", delayedString(REPLACE_ASSET_INDEX));
            makeStart.addReplacement("@@ASSETSDIR@@", delayedFile(DIR_ASSETS));
            makeStart.addReplacement("@@NATIVESDIR@@", delayedFile(Constants.DIR_NATIVES));
            makeStart.addReplacement("@@CSVDIR@@", delayedFile(DIR_MCP_DATA));
            makeStart.addReplacement("@@BOUNCERCLIENT@@", patcher.getDelayedMainClassClient());
            makeStart.addReplacement("@@TWEAKERCLIENT@@", patcher.getDelayedTweakClassClient());
            makeStart.addReplacement("@@BOUNCERSERVER@@", patcher.getDelayedMainClassServer());
            makeStart.addReplacement("@@TWEAKERSERVER@@",  patcher.getDelayedTweakClassServer());
            makeStart.setStartOut(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_START));
            makeStart.setDoesCache(false);
            makeStart.dependsOn(TASK_DL_ASSET_INDEX, TASK_DL_ASSETS);
        }
        
        GenEclipseRunTask eclipseRunConfigClient = makeTask(getProjectTask(TASK_PROJECT_RUNE_CLIENT, patcher), GenEclipseRunTask.class);
        {
            eclipseRunConfigClient.setMainClass(patcher.getDelayedMainClassClient());
            eclipseRunConfigClient.setArguments(patcher.getDelayedRunArgsClient());
            eclipseRunConfigClient.setProjectName(patcher.getCapName());
            eclipseRunConfigClient.setOutputFile(subWorkspace(patcher.getCapName() + "/"+patcher.getCapName()+" Client.launch"));
            eclipseRunConfigClient.setRunDir("${workspace_loc}/run");
            eclipseRunConfigClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenEclipseRunTask eclipseRunConfigServer = makeTask(getProjectTask(TASK_PROJECT_RUNE_SERVER, patcher), GenEclipseRunTask.class);
        {
            eclipseRunConfigServer.setMainClass(patcher.getDelayedMainClassServer());
            eclipseRunConfigServer.setArguments(patcher.getDelayedRunArgsServer());
            eclipseRunConfigServer.setProjectName(patcher.getCapName());
            eclipseRunConfigServer.setOutputFile(subWorkspace(patcher.getCapName() + "/"+patcher.getCapName()+" Server.launch"));
            eclipseRunConfigServer.setRunDir("${workspace_loc}/run");
            eclipseRunConfigServer.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaRunConfigClient = makeTask(getProjectTask(TASK_PROJECT_RUNJ_CLIENT, patcher), GenIdeaRunTask.class);
        {
            ideaRunConfigClient.setMainClass(patcher.getDelayedMainClassClient());
            ideaRunConfigClient.setArguments(patcher.getDelayedRunArgsClient());
            ideaRunConfigClient.setProjectName(patcher.getCapName());
            ideaRunConfigClient.setConfigName(patcher.getCapName() + " Client");
            ideaRunConfigClient.setOutputFile(subWorkspace("/.idea/runConfigurations/"+patcher.getCapName()+"Client.xml"));
            ideaRunConfigClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunConfigClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaRunConfigServer = makeTask(getProjectTask(TASK_PROJECT_RUNJ_SERVER, patcher), GenIdeaRunTask.class);
        {
            ideaRunConfigServer.setMainClass(patcher.getDelayedMainClassServer());
            ideaRunConfigServer.setArguments(patcher.getDelayedRunArgsServer());
            ideaRunConfigServer.setProjectName(patcher.getCapName());
            ideaRunConfigServer.setConfigName(patcher.getCapName() + " Server");
            ideaRunConfigServer.setOutputFile(subWorkspace("/.idea/runConfigurations/"+patcher.getCapName()+"Server.xml"));
            ideaRunConfigServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunConfigServer.dependsOn(makeStart, TASK_GEN_IDES);
        }
    }
    
    protected void removeProject(PatcherProject patcher)
    {
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_REMAP_JAR, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_EXTRACT_SRC, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_EXTRACT_RES, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_MAKE_START, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_RUNE_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_RUNE_SERVER, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_RUNJ_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectTask(TASK_PROJECT_RUNJ_SERVER, patcher)));
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).removeProject(patcher.getCapName());
    }

    @Override
    protected void addReplaceTokens(PatcherExtension ext)
    {
        // use this? or not use this?
    }
    
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        // validate files
        File versionJson = getExtension().getVersionJson();
        
        {
            File workspaceDir = getExtension().getWorkspaceDir();
            
            if (workspaceDir == null)
            {
                throw new GradleConfigurationException("A workspaceDir must be specified! eg: minecraft { workspaceDir = 'someDir' }");
            }
            
            if (versionJson == null || !versionJson.exists())
            {
                throw new GradleConfigurationException("The versionJson could not be found! Are you sure its correct?");
            }
        }

        Version version = parseAndStoreVersion(versionJson, versionJson.getParentFile(), delayedFile(Constants.DIR_JSONS).call());

        GenDevProjectsTask createProjects = (GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS);
        for (Library lib : version.getLibraries())
        {
            if (lib.applies() && lib.extract == null)
            {
                createProjects.addCompileDep(lib.getArtifactName());
            }
        }


        List<PatcherProject> patchersList = sortByPatching(getExtension().getProjects());
        
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        Task ideTask = project.getTasks().getByName(TASK_GEN_IDES);
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
            ideTask.dependsOn(String.format(TASK_PROJECT_EXTRACT_SRC, patcher.getCapName()));
            ideTask.dependsOn(String.format(TASK_PROJECT_EXTRACT_RES, patcher.getCapName()));
            setupTask.dependsOn(String.format(TASK_PROJECT_RUNE_CLIENT, patcher.getCapName()));
            setupTask.dependsOn(String.format(TASK_PROJECT_RUNE_SERVER, patcher.getCapName()));
            setupTask.dependsOn(String.format(TASK_PROJECT_RUNJ_CLIENT, patcher.getCapName()));
            setupTask.dependsOn(String.format(TASK_PROJECT_RUNJ_SERVER, patcher.getCapName()));
            
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
    
    private Closure<File> subWorkspace(String path)
    {
        return getExtension().getDelayedSubWorkspaceDir(path);
    }
    
    private String getProjectTask(String taskname, PatcherProject project)
    {
        return String.format(taskname, project.getCapName());
    }
    
    //@formatter:off
    @Override public boolean canOverlayPlugin() { return false; }
    @Override protected void applyOverlayPlugin() { }
    @Override protected PatcherExtension getOverlayExtension() { return null; }
}
