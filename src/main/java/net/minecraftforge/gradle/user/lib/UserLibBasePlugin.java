package net.minecraftforge.gradle.user.lib;

import net.minecraftforge.gradle.GradleConfigurationException;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.ProcessJarTask;
import net.minecraftforge.gradle.tasks.user.reobf.ReobfTask;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public abstract class UserLibBasePlugin extends UserBasePlugin<UserExtension>
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();

        // ensure that this lib goes everywhere MC goes. its a required lib after all.
        Configuration config = project.getConfigurations().create(actualApiName());
        project.getConfigurations().getByName(UserConstants.CONFIG_MC).extendsFrom(config);
        
        // to set the output not notch names
        ((ReobfTask) project.getTasks().getByName("reobf")).setSrg(delayedFile(UserConstants.REOBF_NOTCH_SRG));
    }

    abstract String actualApiName();

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

    @Override
    protected final void doVersionChecks(String version)
    {
        if (!"1.7.2".equals(version) && !"1.7.10".equals(version))
            throw new GradleConfigurationException("ForgeGradle 1.2 does not support " + version);
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
