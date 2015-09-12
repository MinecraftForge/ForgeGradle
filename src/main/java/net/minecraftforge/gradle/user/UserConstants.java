package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.REPLACE_CACHE_DIR;

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

    public static final String TASK_REOBF             = "reobfJar";
    public static final String TASK_EXTRACT_RANGE     = "extractRangemapSrc";
    public static final String TASK_RETROMAP_SRC      = "retromapSources";
    public static final String TASK_SRC_JAR           = "sourceJar";

    public static final String TASK_DD_COMPILE        = "deobfCompileDummyTask";
    public static final String TASK_DD_PROVIDED       = "deobfProvidedDummyTask";

    static final String        REPLACE_SERVER_TWEAKER = "{RUN_SERVER_TWEAKER}";
    static final String        REPLACE_CLIENT_TWEAKER = "{RUN_CLIENT_TWEAKER}";
    static final String        REPLACE_SERVER_MAIN    = "{RUN_SERVER_MAIN}";
    static final String        REPLACE_CLIENT_MAIN    = "{RUN_CLIENT_MAIN}";
    static final String        REPLACE_RUN_DIR        = "{RUN_DIR}";

    public static final String DIR_DEOBF_DEPS         = REPLACE_CACHE_DIR + "/deobfedDeps/";
}
