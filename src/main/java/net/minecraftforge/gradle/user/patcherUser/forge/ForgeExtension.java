package net.minecraftforge.gradle.user.patcherUser.forge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.delayed.TokenReplacer;
import net.minecraftforge.gradle.util.json.forgeversion.ForgeBuild;
import net.minecraftforge.gradle.util.json.forgeversion.ForgeVersion;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import com.google.common.base.Strings;

public class ForgeExtension extends UserBaseExtension
{
    protected ForgeVersion forgeJson;
    private String         forgeVersion;
    private String         coreMod = null;

    public ForgeExtension(UserBasePlugin<ForgeExtension> plugin)
    {
        super(plugin);
    }

    public String getForgeVersion()
    {
        return forgeVersion;
    }

    public void setForgeVersion(String forgeVersion)
    {
        checkAndSetVersion(forgeVersion);

        TokenReplacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

        mcpVersion = MCP_VERSION_MAP.get(version);
    }

    @Override
    public void setVersion(String inVersion)
    {
        checkAndSetVersion(inVersion);

        TokenReplacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

        mcpVersion = MCP_VERSION_MAP.get(version);

        // maybe they set the mappings first
        checkMappings();
    }

    // ----------------------------------------
    // Code to check the forge version and stuff
    // ---------------------------------------- 

    private static final String  JUST_MC  = "(\\d+\\.\\d+(?:\\.\\d+)?[_pre\\d]*)";
    private static final String  JUST_API = "((?:\\d+\\.){3}(\\d+))((?:-[\\w\\.]+)?)";
    private static final Pattern API      = Pattern.compile(JUST_API);
    private static final Pattern STANDARD = Pattern.compile(JUST_MC + "-" + JUST_API);
    private static final Logger  LOGGER   = Logging.getLogger(ForgeExtension.class);

    private void checkAndSetVersion(String str)
    {
        str = str.trim();

        // build number
        if (isAllNums(str))
        {
            boolean worked = getFromBuildNumber(str);
            if (worked)
                return;
        }

        // promotions
        if (this.forgeJson.promos.containsKey(str))
        {
            boolean worked = getFromBuildNumber(this.forgeJson.promos.get(str));
            LOGGER.lifecycle("Selected version " + this.forgeVersion);
            if (worked)
                return;
        }

        // matches just an API version
        Matcher matcher = API.matcher(str);
        if (matcher.matches())
        {
            String branch = Strings.emptyToNull(matcher.group(3));

            String forgeVersion = matcher.group(1);
            ForgeBuild build = this.forgeJson.number.get(Integer.valueOf(matcher.group(2)));

            if (build == null)
            {
                throw new GradleConfigurationException("No such version exists!");
            }

            boolean branchMatches = false;
            if (branch == null)
                branchMatches = Strings.isNullOrEmpty(build.branch);
            else
                branchMatches = branch.substring(1).equals(build.branch);

            String outBranch = build.branch;
            if (outBranch == null)
                outBranch = "";
            else
                outBranch = "-" + build.branch;

            if (!build.version.equals(forgeVersion) || !branchMatches)
            {
                throw new GradleConfigurationException(str + " is an invalid version! did you mean '" + build.version + outBranch + "' ?");
            }

            version = build.mcversion.replace("_", "-");
            this.forgeVersion = build.version;
            if (!Strings.isNullOrEmpty(build.branch) && !"null".equals(build.branch))
                this.forgeVersion += outBranch;

            return;
        }

        // matches standard form.
        matcher = STANDARD.matcher(str);
        if (matcher.matches())
        {
            String branch = Strings.emptyToNull(matcher.group(4));
            String mcversion = matcher.group(1);

            String forgeVersion = matcher.group(2);
            String buildNumber = matcher.group(3);

            if ("0".equals(buildNumber))
            {
                LOGGER.lifecycle("Assuming custom forge version!");
                version = mcversion;
                this.forgeVersion = forgeVersion;
                return;
            }

            ForgeBuild build = this.forgeJson.number.get(Integer.parseInt(buildNumber));

            if (build == null)
            {
                throw new GradleConfigurationException("No such version exists!");
            }

            boolean branchMatches = false;
            if (branch == null)
                branchMatches = Strings.isNullOrEmpty(build.branch);
            else
                branchMatches = branch.substring(1).equals(build.branch);

            boolean mcMatches = build.mcversion.equals(mcversion);

            String outBranch = build.branch;
            if (outBranch == null)
                outBranch = "";
            else
                outBranch = "-" + build.branch;

            if (!build.version.equals(forgeVersion) || !branchMatches || !mcMatches)
            {
                throw new GradleConfigurationException(str + " is an invalid version! did you mean '" + build.mcversion + "-" + build.version + outBranch + "' ?");
            }

            version = build.mcversion.replace("_", "-");
            this.forgeVersion = build.version;
            if (!Strings.isNullOrEmpty(build.branch) && !"null".equals(build.branch))
                this.forgeVersion += outBranch;

            return;
        }

        throw new GradleConfigurationException("Invalid version notation, or version doesnt exist! The following are valid notations. Buildnumber, version, version-branch, mcversion-version-branch, and pomotion");
    }

    private boolean isAllNums(String in)
    {
        for (char c : in.toCharArray())
        {
            if (!Character.isDigit(c))
                return false;
        }

        return true;
    }

    private boolean getFromBuildNumber(String str)
    {
        return getFromBuildNumber(Integer.valueOf(str));
    }

    private boolean getFromBuildNumber(Integer num)
    {
        ForgeBuild build = this.forgeJson.number.get(num);
        if (build != null)
        {
            version = build.mcversion.replace("_", "-");
            this.forgeVersion = build.version;
            if (!Strings.isNullOrEmpty(build.branch) && !"null".equals(build.branch))
                this.forgeVersion += "-" + build.branch;

            return true;
        }
        else
            return false;
    }

    public String getCoreMod()
    {
        return coreMod;
    }

    public void setCoreMod(String coreMod)
    {
        this.coreMod = coreMod;
    }

}
