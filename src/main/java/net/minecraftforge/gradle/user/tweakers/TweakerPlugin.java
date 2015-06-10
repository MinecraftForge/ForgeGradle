package net.minecraftforge.gradle.user.tweakers;

import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.REPLACE_PROJECT_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.TASK_MERGE_JARS;
import static net.minecraftforge.gradle.user.UserConstants.CONFIG_MC;

import java.util.List;

import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public abstract class TweakerPlugin extends UserBasePlugin<TweakerExtension>
{
    abstract boolean isClient();
    
    private static final String CLEAN_ROOT = REPLACE_CACHE_DIR + "/net/minecraft/";
    private static final String DIRTY_ROOT = REPLACE_PROJECT_CACHE_DIR + "/minecraft/";

    @Override
    protected void applyUserPlugin()
    {
        // patterns
        String cleanSuffix = "%s-" + REPLACE_MC_VERSION + ".jar";
        String dirtySuffix = "%s-" + REPLACE_MC_VERSION + "-PROJECT(" + project.getName() + ").jar";

        if (isClient())
        {
            this.tasksClient(CLEAN_ROOT + "minecraft/"+REPLACE_MC_VERSION+"/minecraft" + cleanSuffix, DIRTY_ROOT + "minecraft" + dirtySuffix);
        }
        else
        {
            this.tasksClient(CLEAN_ROOT + "mincraft_server/"+REPLACE_MC_VERSION+"/minecraft_server" + cleanSuffix, DIRTY_ROOT + "minecraft_server" + dirtySuffix);
        }

        // remove the unused merge jars task
        project.getTasks().remove(project.getTasks().getByName(TASK_MERGE_JARS));

        // TODO: configure reobfuscation to use SRG names
    }
    
    @Override
    protected void afterDecomp(final boolean isDecomp, final boolean useLocalCache, final String mcConfig)
    {
        // add MC repo to all projects
        project.allprojects(new Action<Project>() {
            @Override
            public void execute(Project proj)
            {
                String cleanRoot = CLEAN_ROOT + (isClient() ? "/minecraft" : "/minecraft_server") + "/" + REPLACE_MC_VERSION;
                String repo = delayedFile( useLocalCache ? DIRTY_ROOT : cleanRoot).call().getAbsolutePath();
                addFlatRepo(proj, "TweakerMcRepo", delayedFile( useLocalCache ? DIRTY_ROOT : cleanRoot).call());
                System.out.println("REPO!! ->> "+repo);
            }
        });
        
        // add the Mc dep
        String group = "net.minecraft";
        String artifact =  (isClient() ? "minecraft" : "minecraft_server") + (isDecomp ? "Src" : "Bin");
        String version = delayedString(REPLACE_MC_VERSION).call() + ( useLocalCache ? "-PROJECT(" + project.getName() + ")" : "");
        
        project.getDependencies().add(CONFIG_MC, ImmutableMap.of("group", group, "name", artifact, "version", version));
    }

    @Override
    protected void afterEvaluate()
    {
        super.afterEvaluate();

        TweakerExtension ext = getExtension();

        if (Strings.isNullOrEmpty(ext.getTweakClass()))
        {
            throw new GradleConfigurationException("You must set the tweak class of your tweaker!");
        }

        // add fml tweaker to manifest
        Jar jar = (Jar) project.getTasks().getByName("jar");
        jar.getManifest().getAttributes().put("TweakClass", ext.getTweakClass());
    }

    @Override
    protected Object getStartDir()
    {
        return delayedFile(REPLACE_CACHE_DIR + "/net/minecraft/minecraft/" + REPLACE_MC_VERSION + "/start");
    }

    @Override
    protected String getClientTweaker(TweakerExtension ext)
    {
        return ""; // nothing hardcoded to GradleStart
    }

    @Override
    protected String getServerTweaker(TweakerExtension ext)
    {
        return ""; // nothing hardcoded to GradleStart
    }

    @Override
    protected String getClientRunClass(TweakerExtension ext)
    {
        return ""; // default
    }

    @Override
    protected List<String> getClientRunArgs(TweakerExtension ext)
    {
        return ImmutableList.of("--tweakClass", ext.getTweakClass(), "--noCoreSearch"); // disable FMl coremod searching
    }

    @Override
    protected String getServerRunClass(TweakerExtension ext)
    {
        return ""; // default
    }

    @Override
    protected List<String> getServerRunArgs(TweakerExtension ext)
    {
        return ImmutableList.of("--tweakClass", ext.getTweakClass(), "--noCoreSearch");
    }

    @Override
    protected final boolean hasServerRun()
    {
        return !isClient();
    }

    @Override
    protected final boolean hasClientRun()
    {
        return isClient();
    }

    //@formatter:off
    @Override protected void applyOverlayPlugin() { }
    @Override public boolean canOverlayPlugin() { return false; }
    @Override protected TweakerExtension getOverlayExtension() { return null; }
    //@formatter:on
}
