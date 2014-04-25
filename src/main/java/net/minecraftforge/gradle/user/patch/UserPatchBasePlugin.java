package net.minecraftforge.gradle.user.patch;

import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.user.UserConstants.CLASSIFIER_DECOMPILED;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_MC;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.*;
import groovy.lang.Closure;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.ProcessSrcJarTask;
import net.minecraftforge.gradle.tasks.RemapSourcesTask;
import net.minecraftforge.gradle.tasks.user.ApplyBinPatchesTask;
import net.minecraftforge.gradle.user.UserBasePlugin;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class UserPatchBasePlugin extends UserBasePlugin<UserPackExtension>
{
    @SuppressWarnings({ "serial", "unchecked", "rawtypes" })
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // add the binPatching task
        {
            ApplyBinPatchesTask task = makeTask("applyBinPatches", ApplyBinPatchesTask.class);
            task.setInJar(delayedFile(JAR_MERGED));
            task.setOutJar(delayedFile(JAR_BINPATCHED));
            task.setPatches(delayedFile(BINPATCHES));
            task.setClassesJar(delayedFile(BINARIES_JAR));
            task.setResources(delayedFileTree(RES_DIR));
            task.dependsOn("mergeJars");

            project.getTasks().getByName("deobfBinJar").dependsOn(task);
            
            ProcessJarTask deobf = (ProcessJarTask) project.getTasks().getByName("deobfBinJar").dependsOn(task);;
            deobf.setInJar(delayedFile(JAR_BINPATCHED));
            deobf.dependsOn(task);
        }

        // add source patching task
        {
            DelayedFile decompOut = delayedDirtyFile(null, CLASSIFIER_DECOMPILED, "jar");
            DelayedFile processed = delayedDirtyFile(null, CLASSIFIER_PATCHED, "jar");

            ProcessSrcJarTask patch = makeTask("processSources", ProcessSrcJarTask.class);
            patch.dependsOn("decompile");
            patch.setInJar(decompOut);
            patch.setOutJar(processed);
            configurePatching(patch);

            RemapSourcesTask remap = (RemapSourcesTask) project.getTasks().getByName("remapJar");
            remap.setInJar(processed);
            remap.dependsOn(patch);
        }
        
        // add special handling here.
        // stop people screwing stuff up.
        project.getGradle().getTaskGraph().whenReady(new Closure<Object>(this, null) {
            @Override
            public Object call()
            {
                TaskExecutionGraph graph = project.getGradle().getTaskGraph();
                String path = project.getPath();
                
                if (graph.hasTask(path + "setupDecompWorkspace"))
                {
                    getExtension().setDecomp();
                    setMinecraftDeps(true, false);
                }
                return null;
            }
            
            @Override
            public Object call(Object obj)
            {
                return call();
            }
            
            @Override
            public Object call(Object... obj)
            {
                return call();
            }
        });
        
        // configure eclipse task to do extra stuff.
        project.getTasks().getByName("eclipse").doLast(new Action() {

            @Override
            public void execute(Object arg0)
            {
                File f = new File(ECLIPSE_LOCATION);
                if (f.exists())// && f.length() == 0)
                {
                    String projectDir = "URI//" + project.getProjectDir().toURI().toString();
                    try
                    {
                        byte[] LOCATION_BEFORE = new byte[] { 0x40, (byte) 0xB1, (byte) 0x8B, (byte) 0x81, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x25, (byte) 0x96, (byte) 0xE7, (byte) 0xA3, (byte) 0x93, (byte) 0xBE, 0x1E };
                        byte[] LOCATION_AFTER  = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x58, (byte) 0xFB, (byte) 0xF3, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x51, (byte) 0xF3, (byte) 0x8C, 0x7B, (byte) 0xBB, 0x77, (byte) 0xC6 };
                        
                        FileOutputStream fos = new FileOutputStream(f);
                        fos.write(LOCATION_BEFORE); //Unknown but w/e
                        fos.write((byte) ((projectDir.length() & 0xFF) >> 8));
                        fos.write((byte) ((projectDir.length() & 0xFF) >> 0));
                        fos.write(projectDir.getBytes());
                        fos.write(LOCATION_AFTER); //Unknown but w/e
                        fos.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            
        });
    }

    /**
     * Allows for the configuration of tasks in AfterEvaluate
     */
    protected void delayedTaskConfig()
    {
        // add src ATs
        ProcessJarTask binDeobf = (ProcessJarTask) project.getTasks().getByName("deobfBinJar");
        ProcessJarTask decompDeobf = (ProcessJarTask) project.getTasks().getByName("deobfuscateJar");

        // ATs from the ExtensionObject
        Object[] extAts = getExtension().getAccessTransformers().toArray();
        binDeobf.addTransformer(extAts);
        decompDeobf.addTransformer(extAts);

        // from the resources dirs
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

            SourceSet main = javaConv.getSourceSets().getByName("main");
            SourceSet api = javaConv.getSourceSets().getByName("api");

            for (File at : main.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in main resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }

            for (File at : api.getResources().getFiles())
            {
                if (at.getName().toLowerCase().endsWith("_at.cfg"))
                {
                    project.getLogger().lifecycle("Found AccessTransformer in api resources: " + at.getName());
                    binDeobf.addTransformer(at);
                    decompDeobf.addTransformer(at);
                }
            }
        }

        super.delayedTaskConfig();
        
        // add MC repo.
        final String repoDir = delayedDirtyFile("this", "doesnt", "matter").call().getParentFile().getAbsolutePath();
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addFlatRepo(proj, getApiName()+"FlatRepo", repoDir);
                proj.getLogger().info("Adding repo to " + proj.getPath() + " >> " + repoDir);
            }
        });
    }
    
    @Override
    protected void configurePostDecomp(boolean decomp)
    {
        super.configurePostDecomp(decomp);
        
        // set MC deps
        setMinecraftDeps(decomp, false);
    }
    
    private final void setMinecraftDeps(boolean decomp, boolean remove)
    {
        String version = getApiVersion(getExtension());
        
        if (decomp)
        {
            project.getDependencies().add(CONFIG_MC, ImmutableMap.of("name", getSrcDepName(), "version", version));
            if (remove)
            {
                project.getConfigurations().getByName(CONFIG_MC).exclude(ImmutableMap.of("module", getBinDepName()));
            }
        }
        else
        {
            project.getDependencies().add(CONFIG_MC, ImmutableMap.of("name", getBinDepName(), "version", version));
            if (remove)
            {
                project.getConfigurations().getByName(CONFIG_MC).exclude(ImmutableMap.of("module", getSrcDepName()));
            }
        }
    }
    
    @Override
    protected DelayedFile getDevJson()
    {
        return delayedFile(JSON);
    }

    @Override
    protected String getSrcDepName()
    {
        return getApiName() + "Src";
    }

    @Override
    protected String getBinDepName()
    {
        return getApiName() + "Bin";
    }

    @Override
    protected boolean hasApiVersion()
    {
        return true;
    }

    @Override
    protected String getApiCacheDir(UserPackExtension exten)
    {
        return "{CACHE_DIR}/minecraft/"+getApiGroup().replace('.', '/') + "/{API_NAME}/{API_VERSION}";
    }

    @Override
    protected String getUserDev()
    {
        return getApiGroup() + ":{API_NAME}:{API_VERSION}";
    }

    @Override
    protected Class<UserPackExtension> getExtensionClass()
    {
        return UserPackExtension.class;
    }

    @Override
    protected String getApiVersion(UserPackExtension exten)
    {
        return exten.getApiVersion();
    }

    @Override
    protected String getMcVersion(UserPackExtension exten)
    {
        return exten.getVersion();
    }

    @Override
    protected String getClientRunClass()
    {
        return "net.minecraft.launchwrapper.Launch";
    }

    @Override
    protected Iterable<String> getClientRunArgs()
    {
        return ImmutableList.of("--version", "1.7", "--tweakClass", "cpw.mods.fml.common.launcher.FMLTweaker", "--username=ForgeDevName", "--accessToken", "FML");
    }

    @Override
    protected String getServerRunClass()
    {
        return "cpw.mods.fml.relauncher.ServerLaunchWrapper";
    }

    @Override
    protected Iterable<String> getServerRunArgs()
    {
        return new ArrayList<String>(0);
    }

    /**
     * Add in the desired patching stages.
     * This happens during normal evaluation, and NOT AfterEvaluate.
     * @param patch
     */
    protected abstract void configurePatching(ProcessSrcJarTask patch);
    
    /**
     * Should be with seperate with periods.
     */
    protected abstract String getApiGroup();
}
