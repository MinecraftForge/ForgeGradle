package net.minecraftforge.gradle.user.lib;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.reobf.ArtifactSpec;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.bundling.Jar;

public abstract class UserLibBasePlugin extends UserBasePlugin<UserExtension>
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);
        
        // for special packaging.
        // make jar end with .litemod for litemod, and who knows what else for other things.
        ((Jar) project.getTasks().getByName("jar")).setExtension(getJarExtension());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public void applyOverlayPlugin()
    {
        // add in extension
        project.getExtensions().create(actualApiName(), getExtensionClass(), this);
        
        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);

        // override run configs if needed
        if (shouldOverrideRunConfigs())
        {
            overrideRunConfigs();
        }

        configurePackaging();

        // ensure we get basic things from the other extension
        project.afterEvaluate(new Action() {

            @Override
            public void execute(Object arg0)
            {
                getOverlayExtension().copyFrom(otherPlugin.getExtension());
            }

        });
    }

    @SuppressWarnings("rawtypes")
    protected void configurePackaging()
    {
        String cappedApiName = Character.toUpperCase(actualApiName().charAt(0)) + actualApiName().substring(1);
        JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");

        // create apiJar task
        Jar jarTask = makeTask("jar" + cappedApiName, Jar.class);
        jarTask.from(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput());
        jarTask.setClassifier(actualApiName());
        jarTask.setExtension(getJarExtension());

        // configure otherPlugin task to have a classifier
        ((Jar) project.getTasks().getByName("jar")).setClassifier(((UserBasePlugin) otherPlugin).getApiName());

        //  configure reobf for litemod
        ((ReobfTask) project.getTasks().getByName("reobf")).reobf(jarTask, new Action<ArtifactSpec>()
        {
            @Override
            public void execute(ArtifactSpec spec)
            {
                spec.setSrgMcp();

                JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
                spec.setClasspath(javaConv.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getCompileClasspath());
            }

        });

        project.getArtifacts().add("archives", jarTask);
    }

    @Override
    public final boolean canOverlayPlugin()
    {
        return true;
    }

    public abstract boolean shouldOverrideRunConfigs();

    abstract String actualApiName();

    private void overrideRunConfigs()
    {
        CreateStartTask starter = (CreateStartTask) project.getTasks().getByName("makeStart");
        starter.setClientBounce(delayedString(getClientRunClass()));
        starter.setServerBounce(delayedString(getServerRunClass()));
    }

    @Override
    protected String getStartDir()
    {
        return "{CACHE_DIR}/minecraft/net/minecraft_merged/"+actualApiName() + "Start";
    }

    @Override
    public String getApiName()
    {
        return "minecraft_merged";
    }

    @Override
    protected String getSrcDepName()
    {
        return "minecraft_merged_src";
    }

    @Override
    protected String getBinDepName()
    {
        return "minecraft_merged_bin";
    }

    @Override
    protected boolean hasApiVersion()
    {
        return false;
    }

    @Override
    protected String getApiVersion(UserExtension exten)
    {
        // unnecessary.
        return null;
    }

    @Override
    protected String getMcVersion(UserExtension exten)
    {
        return exten.getVersion();
    }

    @Override
    protected String getApiCacheDir(UserExtension exten)
    {
        return "{CACHE_DIR}/minecraft/net/minecraft/minecraft_merged/{MC_VERSION}";
    }

    @Override
    protected DelayedFile getDevJson()
    {
        return delayedFile(getFmlCacheDir() + "/unpacked/dev.json");
    }

    @Override
    protected String getSrgCacheDir(UserExtension exten)
    {
        return getFmlCacheDir() + "/srgs";
    }

    @Override
    protected String getUserDevCacheDir(UserExtension exten)
    {
        return getFmlCacheDir() + "/unpacked";
    }

    private final String getFmlCacheDir()
    {
        return "{CACHE_DIR}/minecraft/cpw/mods/fml/{FML_VERSION}";
    }

    private final String getFmlVersion(String mcVer)
    {
        // hardcoded because MCP snapshots should be soon, and this will be removed
        if ("1.7.2".equals(mcVer))
            return "1.7.2-7.2.158.889";

        if ("1.7.10".equals(mcVer))
            return "1.7.10-7.10.18.952";

        return null;
    }

    @Override
    protected String getUserDev()
    {
        // hardcoded version of FML... for now..
        return "cpw.mods:fml:{FML_VERSION}";
    }

    @Override
    protected final void configureDeobfuscation(ProcessJarTask task)
    {
        // no access transformers...
    }
    
    protected String getJarExtension()
    {
        return "jar";
    }

    @Override
    protected final void doVersionChecks(String version)
    {
        if (!"1.7.2".equals(version) && !"1.7.10".equals(version))
            throw new RuntimeException("ForgeGradle 1.2 does not support " + version);
    }

    public UserExtension getOverlayExtension()
    {
        return (UserExtension) project.getExtensions().getByName(actualApiName());
    }

    @Override
    public String resolve(String pattern, Project project, UserExtension exten)
    {
        pattern = super.resolve(pattern, project, exten);
        pattern = pattern.replace("{FML_VERSION}", getFmlVersion(getMcVersion(exten)));
        return pattern;
    }
}
