/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2018 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.user.patcherUser.forge;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import com.google.common.base.Strings;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.UserBaseExtension;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.json.forgeversion.ForgeBuild;
import net.minecraftforge.gradle.util.json.forgeversion.ForgeVersion;

public class ForgeExtension extends UserBaseExtension
{
    protected ForgeVersion forgeJson;
    private String         forgeVersion;
    private String         coreMod = null;

    public ForgeExtension(UserBasePlugin<ForgeExtension> plugin)
    {
        super(plugin);
    }

    /**
     * @return the MinecraftForge version
     */
    public String getForgeVersion()
    {
        return forgeVersion;
    }

    public void setForgeVersion(String forgeVersion)
    {
        checkAndSetVersion(forgeVersion);

        replacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

        mcpVersion = MCP_VERSION_MAP.get(version);
    }

    /**
     * Set the Minecraft and MinecraftForge version for the project. <br>
     * Valid formats are:
     * <ul>
     * <li>{@code MinecraftVersion-ForgeVersion}</li>
     * <li>{@code ForgeVersion}</li>
     * <li>{@code ForgeBuildNumber}</li>
     * <li>{@code recommended}</li>
     * <li>{@code latest}</li>
     * </ul>
     *
     * @param inVersion The version
     *
     * @see <a href="https://files.minecraftforge.net">https://files.minecraftforge.net</a>
     */
    @Override
    public void setVersion(String inVersion)
    {
        checkAndSetVersion(inVersion);

        replacer.putReplacement(Constants.REPLACE_MC_VERSION, version);

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
        if (this.forgeJson != null && this.forgeJson.promos != null && this.forgeJson.promos.containsKey(str))
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

            try
            {
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

            }
            catch (GradleConfigurationException e)
            {
                throw e;
            }
            catch (Exception e)// everythng but the gradle exception
            {
                System.out.println("Error occurred parsing version!");

                version = "1.8";// just gonna guess.. since we dont know..
                this.forgeVersion = forgeVersion;
                if (!Strings.isNullOrEmpty(branch) && !"null".equals(branch))
                    this.forgeVersion += branch;
            }

            return;
        }

        // matches standard form.
        matcher = STANDARD.matcher(str);
        if (matcher.matches())
        {
            String branch = matcher.group(4);
            String mcversion = matcher.group(1);

            String forgeVersion = matcher.group(2);
            String buildNumber = matcher.group(3);

            try
            {
                if ("0".equals(buildNumber))
                {
                    LOGGER.lifecycle("Assuming custom forge version!");
                    version = mcversion;
                    this.forgeVersion = forgeVersion + branch;
                    return;
                }

                ForgeBuild build = this.forgeJson.number.get(Integer.parseInt(buildNumber));

                if (build == null)
                {
                    throw new GradleConfigurationException("No such version exists!");
                }

                boolean branchMatches = false;
                if (Strings.isNullOrEmpty(branch))
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
            }
            catch (GradleConfigurationException e)
            {
                throw e;
            }
            catch (Exception e)// everythng but the gradle exception
            {
                System.out.println("Error occurred parsing version!");

                version = mcversion;
                this.forgeVersion = forgeVersion;
                if (!Strings.isNullOrEmpty(branch) && !"null".equals(branch))
                    this.forgeVersion += branch;
            }

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

    /**
     * Get the coremod class for the mod
     *
     * @return The coremod class, or {@code null} if none is configured
     */
    public String getCoreMod()
    {
        return coreMod;
    }

    /**
     * Set the coremod class for the mod
     *
     * @param coreMod The FQN for the coremod
     */
    public void setCoreMod(String coreMod)
    {
        this.coreMod = coreMod;
    }
}
