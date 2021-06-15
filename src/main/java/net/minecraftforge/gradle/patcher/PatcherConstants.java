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
package net.minecraftforge.gradle.patcher;

import net.minecraftforge.gradle.common.Constants;

final class PatcherConstants
{
    // @formatter:off
    private PatcherConstants() {}
    // @formatter:on

    // installer stuff
    static final String REPLACE_INSTALLER        = "{INSTALLER}";
    static final String INSTALLER_URL            = "https://maven.minecraftforge.net/net/minecraftforge/installer/" + REPLACE_INSTALLER + "/installer-" + REPLACE_INSTALLER + "-shrunk.jar";

    // new project defaults
    static final String DEFAULT_PATCHES_DIR      = "patches";
    static final String DEFAULT_SRC_DIR          = "src/main/java";
    static final String DEFAULT_RES_DIR          = "src/main/resources";
    static final String DEFAULT_TEST_SRC_DIR     = "src/test/java";
    static final String DEFAULT_TEST_RES_DIR     = "src/test/resources";

    // constants for paths in the workspace dir
    static final String DIR_EXTRACTED_SRC        = "/src/main/java";
    static final String DIR_EXTRACTED_RES        = "/src/main/resources";
    static final String DIR_EXTRACTED_START      = "/src/main/start";

    static final String REPLACE_PROJECT_NAME     = "{NAME}";
    static final String REPLACE_PROJECT_CAP_NAME = "{CAPNAME}";

    // the only actually cached thing
    static final String DEOBF_DATA               = Constants.DIR_MCP_DATA + "/deobfuscation_data-" + Constants.REPLACE_MC_VERSION + ".lzma";

    // cached stuff
    static final String DIR_LOCAL_CACHE          = Constants.REPLACE_BUILD_DIR + "/localCache";
    static final String JAR_DEOBF                = DIR_LOCAL_CACHE + "/deobfuscated.jar";
    static final String JAR_DECOMP               = DIR_LOCAL_CACHE + "/decompiled.zip";
    static final String JAR_DECOMP_POST          = DIR_LOCAL_CACHE + "/decompiled-processed.zip";
    static final String JAR_REMAPPED             = DIR_LOCAL_CACHE + "/remapped-clean.zip";

    // cached project stuff
    static final String DIR_PROJECT_CACHE        = DIR_LOCAL_CACHE + "/" + REPLACE_PROJECT_CAP_NAME;
    static final String JAR_PROJECT_PATCHED      = DIR_PROJECT_CACHE + "/patched.zip";
    static final String JAR_PROJECT_RECOMPILED   = DIR_PROJECT_CACHE + "/recompiled.jar";
    static final String JAR_PROJECT_REMAPPED     = DIR_PROJECT_CACHE + "/mcp-named.zip";
    static final String JAR_PROJECT_RETROMAPPED  = DIR_PROJECT_CACHE + "/retromapped-mc.zip";
    static final String JAR_PROJECT_RETRO_NONMC  = DIR_PROJECT_CACHE + "/retromapped-nonMc.zip";
    static final String RANGEMAP_PROJECT         = DIR_PROJECT_CACHE + "/rangemap.txt";
    static final String EXC_PROJECT              = DIR_PROJECT_CACHE + "/extracted.exc";

    // stuff for packaging only
    static final String DIR_OUTPUT               = "build/distributions";
    static final String DIR_PACKAGING            = DIR_LOCAL_CACHE + "/packaging";
    static final String JAR_INSTALLER            = DIR_PACKAGING + "/installer-fresh.jar";
    static final String JAR_OBFUSCATED           = DIR_PACKAGING + "/reobfuscated.jar";
    static final String BINPATCH_RUN             = DIR_PACKAGING + "/binpatches.pack.lzma";
    static final String JSON_INSTALLER           = DIR_PACKAGING + "/install_profile.json";
    static final String JSON_UNIVERSAL           = DIR_PACKAGING + "/version.json";
    static final String DIR_USERDEV_PATCHES      = DIR_PACKAGING + "/userdevPatches";

    static final String DIR_USERDEV              = DIR_PACKAGING + "/userdev";
    static final String ZIP_USERDEV_PATCHES      = DIR_USERDEV + "/patches.zip";
    static final String ZIP_USERDEV_SOURCES      = DIR_USERDEV + "/sources.zip";
    static final String ZIP_USERDEV_RES          = DIR_USERDEV + "/resources.zip";
    static final String BINPATCH_DEV             = DIR_USERDEV + "/devbinpatches.pack.lzma";
    static final String JAR_OBF_CLASSES          = DIR_USERDEV + "/classes.jar";
    static final String SRG_MERGED_USERDEV       = DIR_USERDEV + "/merged.srg";
    static final String EXC_MERGED_USERDEV       = DIR_USERDEV + "/merged.exc";
    static final String AT_MERGED_USERDEV        = DIR_USERDEV + "/merged_at.cfg";

    // top level tasks
    static final String TASK_SETUP               = "setup";
    static final String TASK_CLEAN               = "clean";
    static final String TASK_GEN_PATCHES         = "genPatches";
    static final String TASK_BUILD               = "build";

    // internal tasks
    static final String TASK_SETUP_PROJECTS      = "setupProjects";
    static final String TASK_DEOBF               = "deobfuscateJar";
    static final String TASK_DECOMP              = "decompileJar";
    static final String TASK_POST_DECOMP         = "sourceProcessJar";
    static final String TASK_GEN_PROJECTS        = "genGradleProjects";
    static final String TASK_GEN_IDES            = "genIdeProjects";

    // packaging tasks
    static final String TASK_REOBFUSCATE         = "reobfuscate";
    static final String TASK_GEN_BIN_PATCHES     = "genBinaryPatches";
    static final String TASK_EXTRACT_OBF_CLASSES = "extractNonMcClasses";
    static final String TASK_PROCESS_JSON        = "processJson";
    static final String TASK_OUTPUT_JAR          = "outputJar";
    static final String TASK_GEN_PATCHES_USERDEV = "genUserdevPatches";
    static final String TASK_PATCHES_USERDEV     = "packagedUserdevPatches";
    static final String TASK_EXTRACT_OBF_SOURCES = "extractNonMcSources";
    static final String TASK_COMBINE_RESOURCES   = "combineResources";
    static final String TASK_MERGE_FILES         = "mergeFiles";
    static final String TASK_BUILD_USERDEV       = "buildUserdev";
    static final String TASK_BUILD_INSTALLER     = "installer";
    
    // clean project tasks
    static final String TASK_CLEAN_REMAP         = "remapCleanJar";
    static final String TASK_CLEAN_EXTRACT_SRC   = "extractCleanSources";
    static final String TASK_CLEAN_EXTRACT_RES   = "extractCleanResources";
    static final String TASK_CLEAN_MAKE_START    = "makeCleanStart";
    static final String TASK_CLEAN_RUNE_CLIENT   = "makeEclipseCleanRunClient";
    static final String TASK_CLEAN_RUNE_SERVER   = "makeEclipseCleanRunServer";
    static final String TASK_CLEAN_RUNJ_CLIENT   = "makeIdeaCleanRunClient";
    static final String TASK_CLEAN_RUNJ_SERVER   = "makeIdeaCleanRunServer";

    // project tasks
    static final String TASK_PROJECT_SETUP       = "setupProject" + REPLACE_PROJECT_CAP_NAME;
    static final String TASK_PROJECT_SETUP_DEV   = "setupDevProject" + REPLACE_PROJECT_CAP_NAME;
    static final String TASK_PROJECT_PATCH       = "patch" + REPLACE_PROJECT_CAP_NAME + "Jar";
    static final String TASK_PROJECT_REMAP_JAR   = "remap" + REPLACE_PROJECT_CAP_NAME + "Jar";
    static final String TASK_PROJECT_EXTRACT_SRC = "extract" + REPLACE_PROJECT_CAP_NAME + "Sources";
    static final String TASK_PROJECT_EXTRACT_RES = "extract" + REPLACE_PROJECT_CAP_NAME + "Resources";
    static final String TASK_PROJECT_MAKE_START  = "make" + REPLACE_PROJECT_CAP_NAME + "Start";
    static final String TASK_PROJECT_RUNE_CLIENT = "makeEclipse" + REPLACE_PROJECT_CAP_NAME + "RunClient";
    static final String TASK_PROJECT_RUNE_SERVER = "makeEclipse" + REPLACE_PROJECT_CAP_NAME + "RunServer";
    static final String TASK_PROJECT_RUNJ_CLIENT = "makeIdea" + REPLACE_PROJECT_CAP_NAME + "RunClient";
    static final String TASK_PROJECT_RUNJ_SERVER = "makeIdea" + REPLACE_PROJECT_CAP_NAME + "RunServer";
    static final String TASK_PROJECT_COMPILE     = "makeJar" + REPLACE_PROJECT_CAP_NAME + "";
    static final String TASK_PROJECT_GEN_EXC     = "extractExc" + REPLACE_PROJECT_CAP_NAME + "";
    static final String TASK_PROJECT_RANGEMAP    = "extract" + REPLACE_PROJECT_CAP_NAME + "Rangemap";
    static final String TASK_PROJECT_RETROMAP    = "retromapMc" + REPLACE_PROJECT_CAP_NAME;
    static final String TASK_PROJECT_RETRO_NONMC = "retromapNonMc" + REPLACE_PROJECT_CAP_NAME;
    static final String TASK_PROJECT_GEN_PATCHES = "gen" + REPLACE_PROJECT_CAP_NAME + "Patches";
}
