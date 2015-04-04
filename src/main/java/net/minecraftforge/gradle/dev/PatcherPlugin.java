package net.minecraftforge.gradle.dev;

import static net.minecraftforge.gradle.common.Constants.*;
import static net.minecraftforge.gradle.dev.PatcherConstants.JAR_DECOMP;
import static net.minecraftforge.gradle.dev.PatcherConstants.JAR_DEOBF;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.DecompileTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;

public class PatcherPlugin extends BasePlugin<PatcherExtension>
{
    @Override
    public void applyPlugin()
    {
        // create and add the namedDomainObjectContainer to the extension object
        getExtension().setProjectContainer(project.container(PatcherProject.class, new PatcherProjectFactory(this)));
        
        makeTasks();
    }
    
    protected void makeTasks()
    {
        ProcessJarTask deobfJar = makeTask("deobfuscateJar", ProcessJarTask.class);
        {
            deobfJar.setInJar(delayedFile(Constants.JAR_MERGED));
            deobfJar.setOutCleanJar(delayedFile(JAR_DEOBF));
            deobfJar.setSrg(delayedFile(SRG_NOTCH_TO_SRG));
            deobfJar.setExceptorCfg(delayedFile(EXC_SRG));
            deobfJar.setExceptorJson(delayedFile(MCP_DATA_EXC_JSON));
            deobfJar.setApplyMarkers(true);
            deobfJar.setDoesCache(false);
            deobfJar.dependsOn(TASK_MERGE_JARS, TASK_GENERATE_SRGS);
            // TODO: add ATs afterEvaluate()
        }
        
        DecompileTask decompileJar = makeTask("decompileJar", DecompileTask.class);
        {
            decompileJar.setInJar(delayedFile(JAR_DEOBF));
            decompileJar.setOutJar(delayedFile(JAR_DECOMP));
            decompileJar.setFernFlower(delayedFile(Constants.JAR_FERNFLOWER));
            decompileJar.setPatches(delayedFile(MCP_PATCHES_CLIENT));
            decompileJar.setAstyleConfig(delayedFile(MCP_DATA_STYLE));
            decompileJar.setDoesCache(false);
            decompileJar.dependsOn(TASK_DL_FERNFLOWER, deobfJar);
        }
        
        // Clean project stuff
        
//        RemapSourcesTask remapCleanTask = makeTask("remapCleanJar", RemapSourcesTask.class);
//        {
//            remapCleanTask.setInJar(delayedFile(JAR_DECOMP));
//            remapCleanTask.setOutJar(delayedFile(JAR_REMAPPED));
//            remapCleanTask.setMethodsCsv(delayedFile(Constants.CSV_METHOD));
//            remapCleanTask.setFieldsCsv(delayedFile(Constants.CSV_FIELD));
//            remapCleanTask.setParamsCsv(delayedFile(Constants.CSV_PARAM));
//            remapCleanTask.setAddsJavadocs(false);
//            remapCleanTask.setDoesCache(false);
//            remapCleanTask.dependsOn(decompileJar);
//        }
    }
    
    protected void createProject(PatcherProject project)
    {
        
    }

    @Override
    protected void addReplaceTokens(PatcherExtension ext)
    {
        // use this? or not use this?
    }
    
    // overlay plugin stuff I dont care about.
    
    @Override
    public boolean canOverlayPlugin()
    {
        return false;
    }
    
    @Override
    public void applyOverlayPlugin()
    {
        // nothing
    }

    @Override
    protected PatcherExtension getOverlayExtension()
    {
        // cant overlay remember?
        return null;
    }
}
