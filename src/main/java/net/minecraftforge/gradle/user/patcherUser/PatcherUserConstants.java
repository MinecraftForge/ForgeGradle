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
package net.minecraftforge.gradle.user.patcherUser;

import static net.minecraftforge.gradle.common.Constants.*;

public final class PatcherUserConstants
{
    public static final String CONFIG_USERDEV        = "forgeGradleUserDevPackage";

    public static final String REPLACE_API_GROUP     = "{API_GROUP}";
    public static final String REPLACE_API_GROUP_DIR = "{API_GROUP_DIR}";
    public static final String REPLACE_API_NAME      = "{API_NAME}";
    public static final String REPLACE_API_VERSION   = "{API_VERSION}";

    public static final String DIR_API_BASE          = REPLACE_CACHE_DIR + "/" + REPLACE_API_GROUP_DIR + "/" + REPLACE_API_NAME + "/" + REPLACE_API_VERSION;
    public static final String DIR_API_JAR_BASE      = DIR_API_BASE + "/" + REPLACE_MCP_CHANNEL + "/" + REPLACE_MCP_VERSION;

    // userdev locations
    public static final String DIR_USERDEV           = DIR_API_BASE + "/" + "userdev";
    public static final String SRG_USERDEV           = DIR_USERDEV + "/merged.srg";
    public static final String EXC_USERDEV           = DIR_USERDEV + "/merged.exc";
    public static final String AT_USERDEV            = DIR_USERDEV + "/merged_at.cfg";
    public static final String JSON_USERDEV          = DIR_USERDEV + "/dev.json";
    public static final String ZIP_UD_SRC            = DIR_USERDEV + "/sources.zip";
    public static final String ZIP_UD_RES            = DIR_USERDEV + "/resources.zip";
    public static final String ZIP_UD_PATCHES        = DIR_USERDEV + "/patches.zip";
    public static final String ZIP_UD_CLASSES        = DIR_USERDEV + "/classes.jar";
    public static final String BINPATCH_USERDEV      = DIR_USERDEV + "/devbinpatches.pack.lzma";

    public static final String TASK_EXTRACT_USERDEV  = "extractUserdev";
    public static final String TASK_BINPATCH         = "applyBinaryPatches";
    public static final String TASK_PATCH            = "applySourcePatches";
}
