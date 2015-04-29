package net.minecraftforge.gradle.old.user;

import net.minecraftforge.gradle.common.Constants;

public final class UserConstants
{
    private UserConstants()
    {
        // no touch
    }

    public static final String CONFIG_USERDEV        = "userDevPackageDepConfig";
    public static final String CONFIG_NATIVES        = "minecraftNatives";
    public static final String CONFIG_START          = "forgeGradleStartClass";
    public static final String CONFIG_DEPS           = "minecraftDeps";
    public static final String CONFIG_MC             = "minecraft";

    static final String        FORGE_JAVADOC_URL     = Constants.URL_FORGE_MAVEN + "/net/minecraftforge/forge/{API_VERSION}/forge-{API_VERSION}-javadoc.zip";

    static final String        NATIVES_DIR_OLD       = "{BUILD_DIR}/natives";
    static final String        SOURCES_DIR           = "{BUILD_DIR}/sources";

    static final String        CONF_DIR              = "{USER_DEV}/conf";
    static final String        MERGE_CFG             = CONF_DIR + "/mcp_merge.cfg";
    static final String        MCP_PATCH_DIR         = CONF_DIR + "/minecraft_ff";
    static final String        ASTYLE_CFG            = CONF_DIR + "/astyle.cfg";
    static final String        PACKAGED_SRG          = CONF_DIR + "/packaged.srg";
    static final String        PACKAGED_EXC          = CONF_DIR + "/packaged.exc";
    static final String        EXC_JSON              = CONF_DIR + "/exceptor.json";

    public static final String MAPPING_APPENDAGE     = "{MAPPING_CHANNEL}/{MAPPING_VERSION}/";

    static final String        DIRTY_DIR             = "{BUILD_DIR}/dirtyArtifacts";

    static final String        RECOMP_SRC_DIR        = "{BUILD_DIR}/tmp/recompSrc";
    static final String        RECOMP_CLS_DIR        = "{BUILD_DIR}/tmp/recompCls";

    static final String        GRADLE_START_CLIENT   = "GradleStart";
    static final String        GRADLE_START_SERVER   = "GradleStartServer";

    // classifiers
    public static final String CLASSIFIER_DEOBF_SRG  = "srg";
    public static final String CLASSIFIER_DECOMPILED = "decomp";
    public static final String CLASSIFIER_SOURCES    = "sources";
}
