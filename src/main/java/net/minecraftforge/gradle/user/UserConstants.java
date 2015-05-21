package net.minecraftforge.gradle.user;

public class UserConstants
{
    // @formatter:off
    private UserConstants() {}
    // @formatter:on

    public static final String CONFIG_MC         = "forgeGradleMc";
    public static final String CONFIG_START      = "forgeGradleGradleStart";
    public static final String CONFIG_PROVIDED   = "provided";

    public static final String TASK_SETUP_CI     = "setupCiWorkspace";
    public static final String TASK_SETUP_DEV    = "setupDevWorkspace";
    public static final String TASK_SETUP_DECOMP = "setupDecompWorkspace";

    public static final String TASK_DEOBF_BIN    = "deobfMcMCP";
    public static final String TASK_DEOBF        = "deobfMcSRG";
    public static final String TASK_DECOMPILE    = "decompileMc";
    public static final String TASK_POST_DECOMP  = "fixMcSources";
    public static final String TASK_RECOMPILE    = "recompileMc";
}
