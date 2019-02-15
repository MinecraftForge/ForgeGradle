/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.user;

import org.gradle.api.tasks.SourceSet;

import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;
import static net.minecraftforge.gradle.common.Constants.REPLACE_BUILD_DIR;

public class UserConstants
{
    // @formatter:off
    private UserConstants() {}
    // @formatter:on

    public static final String CONFIG_MC              = "forgeGradleMc";
    public static final String CONFIG_START           = "forgeGradleGradleStart";
    public static final String CONFIG_PROVIDED        = "provided";

    public static final String CONFIG_DEOBF_COMPILE   = "deobfCompile";
    public static final String CONFIG_DEOBF_PROVIDED  = "deobfProvided";
    public static final String CONFIG_DC_RESOLVED     = "forgeGradleResolvedDeobfCompile";
    public static final String CONFIG_DP_RESOLVED     = "forgeGradleResovledDeobfProvided";

    public static final String TASK_SETUP_CI          = "setupCiWorkspace";
    public static final String TASK_SETUP_DEV         = "setupDevWorkspace";
    public static final String TASK_SETUP_DECOMP      = "setupDecompWorkspace";

    public static final String TASK_DEOBF_BIN         = "deobfMcMCP";
    public static final String TASK_DEOBF             = "deobfMcSRG";
    public static final String TASK_DECOMPILE         = "decompileMc";
    public static final String TASK_POST_DECOMP       = "fixMcSources";
    public static final String TASK_REMAP             = "remapMcSources";
    public static final String TASK_RECOMPILE         = "recompileMc";
    public static final String TASK_MAKE_START        = "makeStart";

    public static final String EXT_REOBF              = "reobf";
    public static final String TASK_REOBF             = "reobfJar";
    public static final String TASK_SRC_JAR           = "sourceJar";

    public static final String TASK_EXTRACT_DEP_ATS   = "extractDependencyATs";
    public static final String TASK_DD_COMPILE        = "deobfCompileDummyTask";
    public static final String TASK_DD_PROVIDED       = "deobfProvidedDummyTask";

    static final String        REPLACE_SERVER_TWEAKER = "{RUN_SERVER_TWEAKER}";
    static final String        REPLACE_CLIENT_TWEAKER = "{RUN_CLIENT_TWEAKER}";
    static final String        REPLACE_SERVER_MAIN    = "{RUN_SERVER_MAIN}";
    static final String        REPLACE_CLIENT_MAIN    = "{RUN_CLIENT_MAIN}";
    static final String        REPLACE_RUN_DIR        = "{RUN_DIR}";

    public static final String DIR_DEOBF_DEPS         = REPLACE_CACHE_DIR + "/deobfedDeps/";
    public static final String DIR_DEP_ATS            = REPLACE_BUILD_DIR + "/depAts/";

    // retromapping sourceSets
    public static final String DIR_RETROMAP           = REPLACE_BUILD_DIR + "/retromapping";
    public static final String TMPL_TASK_RANGEMAP     = "extractRangemap%s";
    public static final String TMPL_RANGEMAP          = DIR_RETROMAP + "/%s.rangemap";
    public static final String TMPL_TASK_RETROMAP     = "retromap%s";
    public static final String TMPL_RETROMAPED        = DIR_RETROMAP + "/retromapped%s.jar";

    // retromapping toke-replaced sourcesets
    public static final String TMPL_TASK_RANGEMAP_RPL = "extractRangemapReplaced%s";
    public static final String TMPL_RANGEMAP_RPL      = DIR_RETROMAP + "/replaced%s.rangemap";
    public static final String TMPL_TASK_RETROMAP_RPL = "retromapReplaced%s";
    public static final String TMPL_RETROMAPED_RPL    = DIR_RETROMAP + "/retromappedReplaced%s.jar";

    public static final String getSourceSetFormatted(SourceSet sourceSet, String template)
    {
        String name = sourceSet.getName();
        name = name.substring(0, 1).toUpperCase() + name.substring(1);          // convert 1st char to upper case.
        return String.format(template, name);
    }
}
