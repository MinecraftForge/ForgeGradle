package net.minecraftforge.gradle.user.patcherUser.forge;

import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.common.Constants.SRG_MCP_TO_SRG;
import static net.minecraftforge.gradle.user.UserConstants.TASK_REOBF;

import java.io.File;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.patcherUser.PatcherUserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.json.JsonFactory;
import net.minecraftforge.gradle.util.json.forgeversion.ForgeVersion;

import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;

public class ForgePlugin extends PatcherUserBasePlugin<ForgeExtension>
{
    @Override
    protected void applyUserPlugin()
    {
        // set the version info into the extension object
        setForgeVersionJson();

        super.applyUserPlugin();

        // setup reobf
        {
            TaskSingleReobf reobf = (TaskSingleReobf) project.getTasks().getByName(TASK_REOBF);
            reobf.setPrimarySrg(delayedFile(SRG_MCP_TO_SRG));
            reobf.addPreTransformer(new McVersionTransformer(delayedString(REPLACE_MC_VERSION)));
        }
    }

    @Override
    protected void afterEvaluate()
    {
        ForgeExtension ext = getExtension();
        if (Strings.isNullOrEmpty(ext.getForgeVersion()))
        {
            throw new GradleConfigurationException("You must set the Forge version!");
        }

        super.afterEvaluate();

        // configure reobf
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            Jar jarTask = (Jar) project.getTasks().getByName("jar");

            TaskSingleReobf reobfTask = ((TaskSingleReobf) project.getTasks().getByName(UserConstants.TASK_REOBF));
            reobfTask.setClasspath(javaConv.getSourceSets().getByName("main").getCompileClasspath());
            reobfTask.setPrimarySrg(delayedFile(Constants.SRG_MCP_TO_SRG));
            reobfTask.setJar(jarTask.getArchivePath());
            reobfTask.dependsOn(jarTask);
        }
    }

    private void setForgeVersionJson()
    {
        File jsonCache = cacheFile("ForgeVersion.json");
        File etagFile = new File(jsonCache.getAbsolutePath() + ".etag");
        String url = Constants.URL_FORGE_MAVEN + "/net/minecraftforge/forge/json";

        getExtension().forgeJson = JsonFactory.GSON.fromJson(getWithEtag(url, jsonCache, etagFile), ForgeVersion.class);
    }

    @Override
    public String getApiGroup(ForgeExtension ext)
    {
        return "net.minecraftforge";
    }

    @Override
    public String getApiName(ForgeExtension ext)
    {
        return "forge";
    }

    @Override
    public String getApiVersion(ForgeExtension ext)
    {
        return ext.getVersion() + "-" + ext.getForgeVersion();
    }

    @Override
    public String getUserdevClassifier(ForgeExtension ext)
    {
        return "userdev";
    }

    @Override
    public String getUserdevExtension(ForgeExtension ext)
    {
        return "jar";
    }

    @Override
    protected String getClientTweaker(ForgeExtension ext)
    {
        return getApiGroup(ext) + ".fml.common.launcher.FMLTweaker";
    }

    @Override
    protected String getServerTweaker(ForgeExtension ext)
    {
        return getApiGroup(ext) + ".fml.common.launcher.FMLServerTweaker";
    }

    @Override
    protected String getClientRunClass(ForgeExtension ext)
    {
        return "net.minecraft.launchwrapper.Launch";
    }

    @Override
    protected List<String> getClientRunArgs(ForgeExtension ext)
    {
        return Lists.newArrayListWithCapacity(0);
    }

    @Override
    protected String getServerRunClass(ForgeExtension ext)
    {
        return getClientRunClass(ext);
    }

    @Override
    protected List<String> getServerRunArgs(ForgeExtension ext)
    {
        return Lists.newArrayListWithCapacity(0);
    }
}
