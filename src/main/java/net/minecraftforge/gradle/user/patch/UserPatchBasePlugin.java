package net.minecraftforge.gradle.user.patch;

import static net.minecraftforge.gradle.common.Constants.JAR_MERGED;
import static net.minecraftforge.gradle.user.UserConstants.CLASSIFIER_DECOMPILED;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.BINARIES_JAR;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.BINPATCHES;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.CLASSIFIER_PATCHED;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.ECLIPSE_LOCATION;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.JAR_BINPATCHED;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.JSON;
import static net.minecraftforge.gradle.user.patch.UserPatchConstants.RES_DIR;

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
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

import com.google.common.collect.ImmutableList;

public abstract class UserPatchBasePlugin extends UserBasePlugin<UserPatchExtension>
{
    @SuppressWarnings({ "unchecked", "rawtypes" })
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
    
    @Override
    public final void applyOverlayPlugin()
    {
        // nothing.
    }

    @Override
    public final boolean canOverlayPlugin()
    {
        return false;
    }
    
    @Override
    public final UserPatchExtension getOverlayExtension()
    {
        return null; // nope.
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
    }
    
    @Override
    protected void doVersionChecks(String version)
    {
        int buildNumber = Integer.parseInt(version.substring(version.lastIndexOf('.') + 1));
        doVersionChecks(buildNumber);
    }
    
    protected abstract void doVersionChecks(int buildNumber);

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
    protected String getApiCacheDir(UserPatchExtension exten)
    {
        return "{CACHE_DIR}/minecraft/"+getApiGroup().replace('.', '/') + "/{API_NAME}/{API_VERSION}";
    }

    @Override
    protected String getSrgCacheDir(UserPatchExtension exten)
    {
        return "{API_CACHE_DIR}/srgs";
    }

    @Override
    protected String getUserDevCacheDir(UserPatchExtension exten)
    {
        return "{API_CACHE_DIR}/unpacked";
    }

    @Override
    protected String getUserDev()
    {
        return getApiGroup() + ":{API_NAME}:{API_VERSION}";
    }

    @Override
    protected Class<UserPatchExtension> getExtensionClass()
    {
        return UserPatchExtension.class;
    }

    @Override
    protected String getApiVersion(UserPatchExtension exten)
    {
        return exten.getApiVersion();
    }

    @Override
    protected String getMcVersion(UserPatchExtension exten)
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
