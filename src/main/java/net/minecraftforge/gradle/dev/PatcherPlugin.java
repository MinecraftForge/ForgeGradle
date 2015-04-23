package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.dev.PatcherConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
import com.google.common.collect.Sets;

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
        
        makeGeneralTasks();
        makeCleanTasks();
    }
    
    protected void makeGeneralTasks()
    {
        DeobfuscateJarTask deobfJar = makeTask(TASK_DEOBF, DeobfuscateJarTask.class);
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
        
        ApplyFernFlowerTask decompileJar = makeTask(TASK_DECOMP, ApplyFernFlowerTask.class);
        {
            decompileJar.setInJar(delayedFile(JAR_DEOBF));
            decompileJar.setOutJar(delayedFile(JAR_DECOMP));
            decompileJar.setFernflower(delayedFile(Constants.JAR_FERNFLOWER));
            decompileJar.setDoesCache(false);
            decompileJar.dependsOn(TASK_DL_FERNFLOWER, deobfJar);
        }
        
        PostDecompileTask postDecompileJar = makeTask(TASK_POST_DECOMP, PostDecompileTask.class);
        {
            postDecompileJar.setInJar(delayedFile(JAR_DECOMP));
            postDecompileJar.setOutJar(delayedFile(JAR_DECOMP_POST));
            postDecompileJar.setPatches(delayedFile(MCP_PATCHES_MERGED));
            postDecompileJar.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            postDecompileJar.setDoesCache(false);
            postDecompileJar.dependsOn(decompileJar);
        }
        
        ProcessSrcJarTask patchJar = makeTask(TASK_PATCH, ProcessSrcJarTask.class);
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
    }
    
    protected void makeCleanTasks()
    {
        RemapSourcesTask remapCleanTask = makeTask("remapCleanJar", RemapSourcesTask.class);
        {
            remapCleanTask.setInJar(delayedFile(JAR_DECOMP));
            remapCleanTask.setOutJar(delayedFile(JAR_REMAPPED));
            remapCleanTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapCleanTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapCleanTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapCleanTask.setAddsJavadocs(false);
            remapCleanTask.setDoesCache(false);
            remapCleanTask.dependsOn(TASK_POST_DECOMP);
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
        
        GenEclipseRunTask eclipseClient = makeTask("makeEclipseCleanRunClient", GenEclipseRunTask.class);
        {
            eclipseClient.setMainClass("net.minecraft.client.main.Main");
            eclipseClient.setProjectName("Clean");
            eclipseClient.setOutputFile(subWorkspace("Clean/Clean Client.launch"));
            eclipseClient.setRunDir("${workspace_loc}/run");
            eclipseClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenEclipseRunTask eclipseServer = makeTask("makeEclipseCleanRunServer", GenEclipseRunTask.class);
        {
            eclipseServer.setMainClass("net.minecraft.server.MinecraftServer");
            eclipseServer.setProjectName("Clean");
            eclipseServer.setOutputFile(subWorkspace("Clean/Clean Server.launch"));
            eclipseServer.setRunDir("${workspace_loc}/run");
            eclipseServer.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaClient = makeTask("makeIdeaCleanRunClient", GenIdeaRunTask.class);
        {
            ideaClient.setMainClass("net.minecraft.client.main.Main");
            ideaClient.setProjectName("Clean");
            ideaClient.setConfigName("Clean Client");
            ideaClient.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Client.xml"));
            ideaClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaServer = makeTask("makeIdeaCleanRunServer", GenIdeaRunTask.class);
        {
            ideaServer.setMainClass("net.minecraft.server.MinecraftServer");
            ideaServer.setProjectName("Clean");
            ideaServer.setConfigName("Clean Server");
            ideaServer.setOutputFile(subWorkspace("/.idea/runConfigurations/Clean_Server.xml"));
            ideaServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaServer.dependsOn(makeStart,TASK_GEN_IDES);
        }
        
        // add depends
        project.getTasks().getByName(TASK_GEN_IDES).dependsOn(extractSrc, extractRes);
        project.getTasks().getByName(TASK_SETUP).dependsOn(eclipseClient, eclipseServer, ideaClient, ideaServer);
    }
    
    protected void createProject(PatcherProject patcher)
    {
        RemapSourcesTask remapTask = makeTask(getProjectString(TASK_PROJECT_REMAP_JAR, patcher), RemapSourcesTask.class);
        {
            remapTask.setInJar(delayedFile(getProjectString(JAR_PROJECT_PATCHED, patcher)));
            remapTask.setOutJar(delayedFile(getProjectString(JAR_PROJECT_REMAPPED, patcher)));
            remapTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
            remapTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
            remapTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
            remapTask.setAddsJavadocs(false);
            remapTask.setDoesCache(false);
            remapTask.dependsOn(TASK_PATCH);
        }
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).putProject(patcher.getCapName(),
                patcher.getDelayedSourcesDir(),
                patcher.getDelayedResourcesDir(),
                patcher.getDelayedTestSourcesDir(),
                patcher.getDelayedTestResourcesDir());
        
        
        Object delayedRemapped = delayedTree(getProjectString(JAR_PROJECT_REMAPPED, patcher));
        
        Copy extract = makeTask(getProjectString(TASK_PROJECT_EXTRACT_SRC, patcher), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_SRC));
            extract.include("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
        
        extract = makeTask(getProjectString(TASK_PROJECT_EXTRACT_RES, patcher), Copy.class);
        {
            extract.from(delayedRemapped);
            extract.into(subWorkspace(patcher.getCapName() + DIR_EXTRACTED_RES));
            extract.exclude("*.java", "**/*.java");
            extract.dependsOn(remapTask, TASK_GEN_PROJECTS);
        }
        
        CreateStartTask makeStart = makeTask(getProjectString(TASK_PROJECT_MAKE_START, patcher), CreateStartTask.class);
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
        
        GenEclipseRunTask eclipseRunClient = makeTask(getProjectString(TASK_PROJECT_RUNE_CLIENT, patcher), GenEclipseRunTask.class);
        {
            eclipseRunClient.setMainClass(patcher.getDelayedMainClassClient());
            eclipseRunClient.setArguments(patcher.getDelayedRunArgsClient());
            eclipseRunClient.setProjectName(patcher.getCapName());
            eclipseRunClient.setOutputFile(subWorkspace(patcher.getCapName() + "/"+patcher.getCapName()+" Client.launch"));
            eclipseRunClient.setRunDir("${workspace_loc}/run");
            eclipseRunClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenEclipseRunTask eclipseRunServer = makeTask(getProjectString(TASK_PROJECT_RUNE_SERVER, patcher), GenEclipseRunTask.class);
        {
            eclipseRunServer.setMainClass(patcher.getDelayedMainClassServer());
            eclipseRunServer.setArguments(patcher.getDelayedRunArgsServer());
            eclipseRunServer.setProjectName(patcher.getCapName());
            eclipseRunServer.setOutputFile(subWorkspace(patcher.getCapName() + "/"+patcher.getCapName()+" Server.launch"));
            eclipseRunServer.setRunDir("${workspace_loc}/run");
            eclipseRunServer.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaRunClient = makeTask(getProjectString(TASK_PROJECT_RUNJ_CLIENT, patcher), GenIdeaRunTask.class);
        {
            ideaRunClient.setMainClass(patcher.getDelayedMainClassClient());
            ideaRunClient.setArguments(patcher.getDelayedRunArgsClient());
            ideaRunClient.setProjectName(patcher.getCapName());
            ideaRunClient.setConfigName(patcher.getCapName() + " Client");
            ideaRunClient.setOutputFile(subWorkspace("/.idea/runConfigurations/"+patcher.getCapName()+"Client.xml"));
            ideaRunClient.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunClient.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        GenIdeaRunTask ideaRunServer = makeTask(getProjectString(TASK_PROJECT_RUNJ_SERVER, patcher), GenIdeaRunTask.class);
        {
            ideaRunServer.setMainClass(patcher.getDelayedMainClassServer());
            ideaRunServer.setArguments(patcher.getDelayedRunArgsServer());
            ideaRunServer.setProjectName(patcher.getCapName());
            ideaRunServer.setConfigName(patcher.getCapName() + " Server");
            ideaRunServer.setOutputFile(subWorkspace("/.idea/runConfigurations/"+patcher.getCapName()+"Server.xml"));
            ideaRunServer.setRunDir("file://$PROJECT_DIR$/run");
            ideaRunServer.dependsOn(makeStart, TASK_GEN_IDES);
        }
        
        //
//        extractRange = makeTask("extractRangeClean", ExtractS2SRangeTask.class);
//        {
//            extractRange.setLibsFromProject(delayedFile(ECLIPSE_CLEAN + "/build.gradle"), "compile", true);
//            extractRange.addIn(delayedFile(REMAPPED_CLEAN));
//            extractRange.setExcOutput(delayedFile(EXC_MODIFIERS_CLEAN));
//            extractRange.setRangeMap(rangeMapClean);
//        }
    }
    
    protected void removeProject(PatcherProject patcher)
    {
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_REMAP_JAR, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_EXTRACT_SRC, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_EXTRACT_RES, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_MAKE_START, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_RUNE_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_RUNE_SERVER, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_RUNJ_CLIENT, patcher)));
        project.getTasks().remove(project.getTasks().getByName(getProjectString(TASK_PROJECT_RUNJ_SERVER, patcher)));
        
        ((GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS)).removeProject(patcher.getCapName());
    }
    
    public void afterEvaluate()
    {
        super.afterEvaluate();
        
        // validate files
        {
            File versionJson = getExtension().getVersionJson();
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

        // use versionJson stuff
        {
            File versionJson = getExtension().getVersionJson();
            Version version = parseAndStoreVersion(versionJson, versionJson.getParentFile(), delayedFile(Constants.DIR_JSONS).call());

            GenDevProjectsTask createProjects = (GenDevProjectsTask) project.getTasks().getByName(TASK_GEN_PROJECTS);
            Set<String> repos = Sets.newHashSet();
            
            for (Library lib : version.getLibraries())
            {
                if (lib.applies() && lib.extract == null)
                {
                    createProjects.addCompileDep(lib.getArtifactName());
                    
                    // add repo for url if its not the MC repo, not maven central, and not already added
                    String url = lib.getUrl();
                    if (!url.contains("libraries.minecraft.net") && !url.contains("maven.apache.org") && !repos.contains(url))
                    {
                        createProjects.addRepo("jsonRepo"+repos.size(), url);
                        repos.add(url);
                    }
                }
            }
        }

        List<PatcherProject> patchersList = sortByPatching(getExtension().getProjects());
        
        Task setupTask = project.getTasks().getByName(TASK_SETUP);
        Task ideTask = project.getTasks().getByName(TASK_GEN_IDES);
        ProcessSrcJarTask patchJar = (ProcessSrcJarTask) project.getTasks().getByName(TASK_PATCH);
        DeobfuscateJarTask deobfJar = (DeobfuscateJarTask) project.getTasks().getByName(TASK_DEOBF);
        
        for (PatcherProject patcher : patchersList)
        {
            patchJar.addStage(
                    patcher.getName(),
                    patcher.getDelayedPatchDir(), 
                    delayedFile(getProjectString(JAR_PROJECT_PATCHED, patcher)),
                    patcher.getDelayedSourcesDir(),
                    patcher.getDelayedResourcesDir());
            
            // TODO: make it the project creation tasks
            ideTask.dependsOn(getProjectString(TASK_PROJECT_EXTRACT_SRC, patcher));
            ideTask.dependsOn(getProjectString(TASK_PROJECT_EXTRACT_RES, patcher));
            setupTask.dependsOn(getProjectString(TASK_PROJECT_RUNE_CLIENT, patcher));
            setupTask.dependsOn(getProjectString(TASK_PROJECT_RUNE_SERVER, patcher));
            setupTask.dependsOn(getProjectString(TASK_PROJECT_RUNJ_CLIENT, patcher));
            setupTask.dependsOn(getProjectString(TASK_PROJECT_RUNJ_SERVER, patcher));
            
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
    
    private String getProjectString(String str, PatcherProject project)
    {
        return str.replace("{CAPNAME}", project.getCapName()).replace("{NAME}", project.getName());
    }
    
    //@formatter:off
    @Override protected void addReplaceTokens(PatcherExtension ext) { }
    @Override public boolean canOverlayPlugin() { return false; }
    @Override protected void applyOverlayPlugin() { }
    @Override protected PatcherExtension getOverlayExtension() { return null; }
}
