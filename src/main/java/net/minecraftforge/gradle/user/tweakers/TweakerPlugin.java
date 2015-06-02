package net.minecraftforge.gradle.user.tweakers;

import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.REPLACE_PROJECT_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.TASK_MERGE_JARS;

import java.util.List;

import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;

import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public abstract class TweakerPlugin extends UserBasePlugin<TweakerExtension>
{
    abstract boolean isClient();

    @Override
    protected void applyUserPlugin()
    {
        // patterns
        String cleanRoot = REPLACE_CACHE_DIR + "/net/minecraft/";
        String dirtyRoot = REPLACE_PROJECT_CACHE_DIR + "/minecraft/";
        String cleanSuffix = "%s-" + REPLACE_MC_VERSION + ".jar";
        String dirtySuffix = "%s-" + REPLACE_MC_VERSION + "-PROJECT(" + project.getName() + ").jar";

        if (isClient())
        {
            this.tasksClient(cleanRoot + "minecraft/"+REPLACE_MC_VERSION+"/minecraft" + cleanSuffix, dirtyRoot + "minecraft" + dirtySuffix);
        }
        else
        {
            this.tasksClient(cleanRoot + "mincraft_server/"+REPLACE_MC_VERSION+"/minecraft_server" + cleanSuffix, dirtyRoot + "minecraft_server" + dirtySuffix);
        }

        // remove the unused merge jars task
        project.getTasks().remove(project.getTasks().getByName(TASK_MERGE_JARS));

        // TODO: configure reobfuscation to use SRG names
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
