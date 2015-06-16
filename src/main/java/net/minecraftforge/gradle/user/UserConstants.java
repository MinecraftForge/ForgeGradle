package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.Constants;

public class UserConstants
{
    // @formatter:off
    private UserConstants() {}
    // @formatter:on

    public static final String CONFIG_MC              = "forgeGradleMc";
    public static final String CONFIG_START           = "forgeGradleGradleStart";
    public static final String CONFIG_PROVIDED        = "provided";

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
    
    // dummy stuff
    public static final String TASK_DUMMY_MC          = "dummyMcDepTask";
    public static final String JAR_DUMMY_MC           = Constants.REPLACE_PROJECT_CACHE_DIR+"/dummyMcDep.jar";

    static final String        REPLACE_SERVER_TWEAKER = "{RUN_SERVER_TWEAKER}";
    static final String        REPLACE_CLIENT_TWEAKER = "{RUN_CLIENT_TWEAKER}";
    static final String        REPLACE_SERVER_MAIN    = "{RUN_SERVER_MAIN}";
    static final String        REPLACE_CLIENT_MAIN    = "{RUN_CLIENT_MAIN}";
    static final String        REPLACE_RUN_DIR        = "{RUN_DIR}";
}
