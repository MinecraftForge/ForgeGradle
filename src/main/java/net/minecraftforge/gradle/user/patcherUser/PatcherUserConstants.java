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
    public static final String JAR_UD_CLASSES        = DIR_USERDEV + "/resources.zip";
    public static final String BINPATCH_USERDEV      = DIR_USERDEV + "/devbinpatches.pack.lzma";

    public static final String TASK_EXTRACT_USERDEV  = "extractUserdev";
}
