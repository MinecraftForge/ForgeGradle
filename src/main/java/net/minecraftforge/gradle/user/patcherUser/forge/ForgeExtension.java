/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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

public class ForgeExtension extends UserBaseExtension
{
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
     * @see <a href="http://files.minecraftforge.net">http://files.minecraftforge.net</a>
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
        int idx = str.indexOf('-');
        if (idx == -1)
            throw new IllegalArgumentException("You must specify the full forge version, including MC version in your build.gradle. Example: 1.12.2-14.23.5.2811");
        this.version = str.substring(0, idx); //MC Version
        this.forgeVersion = str.substring(idx + 1);

        /*
         * Old FG used to use a horribly outdated MASSIVE json file for trying to be 'smart' when processing the version information.
         * It tried to allow for many 'shortcuts' when specifying the Forge version.
         * All of this are horribly and stupid, and should of never existed in the first place.
         * So I'm gutting them.
         *
         * But will document them here to the best of my understanding, so that if people need them we can re-implement them in less horribly hacky ways.
         *
         * JUST the build number:
         *   Prior to 1.13, Forge used a unique build number to identify all versions. So in theory you could pick an exact build with just the build number.
         *   Example:
         *     Input: 2815
         *     Output: 1.12.2-14.23.5.2815
         *   Solution:
         *     Download maven-metadata.xml, loop through all versions doing:
         *       key = ver.split('-')[0].rsplit('.', 1)[1]
         *       if (!map.containsKey(key))  //This is important because metadata is ordered oldest to newest, and new versions could duplicate the build number
         *         map.put(key, ver)
         *
         *
         * Promotion Name:
         *   We publish 'promoted' builds of Forge. Typically 'latest' and 'recommended'. Simple enough way to make a auto updating version.
         *   Example:
         *     Input: 1.8-recommended
         *     Output: 1.8-11.14.4.1563
         *   Solution:
         *     Again, Abrar downloaded a 2MB MASSIVE json file, when a slim json would do.
         *     https://maven.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json
         *
         *
         * API-Wildcards:
         *   Abrar tried to emulate dynamic versions which would be introduced into gradle far afterwords.
         *   Example:
         *     Input: 14.23.5.1
         *     Output: 1.12.2-14.23.5.2811
         *   Solution:
         *     Again, can be solved using maven-metadata.xml, use Apache's ArtifactVersion library to parse out a easy comparable version for everything in the metadata, and the version the user input.
         *     Set MinVersion = ArtifactVersion(input)
         *     prefix = input.substring(0, input.lastIndexOf('.'))
         *     MaxVersion = ArtifactVersion(prefix.rsplit('.', 1)[0] + '.' + (int(prefix.rsplit('.', 1)[1]) + 1))
         *
         *     Then find the max version that fits: MinVersion <= Version < MaxVersion
         *
         * Full Version:
         *   Example:
         *     Input: 1.12.2-14.23.5.2811
         *     Output: 1.12.2-14.23.5.2811
         *
         *     This was just used to verify the version existed. This can be done via maven-metadata.xml
         */
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
