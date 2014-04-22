package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.Constants;

public final class UserConstants
{
    private UserConstants()
    {
        // no touch
    }

    static final String CONFIG_USERDEV    = "userDevPackageDepConfig";
    static final String CONFIG_NATIVES    = "minecraftNatives";
    static final String CONFIG_DEPS       = "minecraftDeps";
    static final String CONFIG            = "minecraft";

    static final String FORGE_JAVADOC_URL = Constants.FORGE_MAVEN + "/net/minecraftforge/forge/{API_VERSION}/forge-{API_VERSION}-javadoc.zip";

    static final String PACK_DIR          = "{API_CACHE_DIR}/unpacked";
    static final String NATIVES_DIR       = "{BUILD_DIR}/natives";
    static final String SOURCES_DIR       = "{BUILD_DIR}/sources";

    static final String CONF_DIR          = PACK_DIR + "/conf";
    static final String MERGE_CFG         = CONF_DIR + "/mcp_merge.cfg";
    static final String MCP_PATCH_DIR     = CONF_DIR + "/minecraft_ff";
    static final String ASTYLE_CFG        = CONF_DIR + "/astyle.cfg";
    static final String PACKAGED_SRG      = CONF_DIR + "/packaged.srg";
    static final String PACKAGED_EXC      = CONF_DIR + "/packaged.exc";
    static final String EXC_JSON          = CONF_DIR + "/exceptor.json";

    public static final String DEOBF_SRG_SRG     = "{API_CACHE_DIR}/srgs/notch-srg.srg";
    public static final String DEOBF_MCP_SRG     = "{API_CACHE_DIR}/srgs/notch-mcp.srg";
    public static final String REOBF_SRG         = "{API_CACHE_DIR}/srgs/mcp-srg.srg";
    public static final String REOBF_NOTCH_SRG   = "{API_CACHE_DIR}/srgs/mcp-notch.srg";
    public static final String EXC_SRG           = "{API_CACHE_DIR}/srgs/srg.exc";
    public static final String EXC_MCP           = "{API_CACHE_DIR}/srgs/mcp.srg";

    static final String METHOD_CSV        = CONF_DIR + "/methods.csv";
    static final String FIELD_CSV         = CONF_DIR + "/fields.csv";
    static final String PARAM_CSV         = CONF_DIR + "/params.csv";

    static final String SRC_DIR           = PACK_DIR + "/src/main/java";
    static final String RES_DIR           = PACK_DIR + "/src/main/resources";
    static final String FML_AT            = RES_DIR + "/fml_at.cfg";
    static final String FORGE_AT          = RES_DIR + "/forge_at.cfg";

    static final String DIRTY_DIR         = "{BUILD_DIR}/dirtyArtifacts";
    
    static final String RECOMP_SRC_DIR    = "{BUILD_DIR}/tmp/recompSrc";
    static final String RECOMP_CLS_DIR    = "{BUILD_DIR}/tmp/recompCls";

    static final String FORGE_CACHE       = "{CACHE_DIR}/minecraft/net/minecraftforge/forge/{API_VERSION}";
    // forge bin jars
    static final String FORGE_DEOBF_MCP   = "/forgeBin-{API_VERSION}.jar";
    static final String FORGE_JAVADOC     = "/forgeBin-{API_VERSION}-javadoc.jar";
    // frge src jars
    static final String FORGE_RECOMP      = "/forgeSrc-{API_VERSION}.jar";
    static final String FORGE_REMAPPED    = "/forgeSrc-{API_VERSION}-sources.jar";
    // intermediate jars
    static final String FORGE_BINPATCHED  = "/forge-{API_VERSION}-binPatched.jar";
    static final String FORGE_DEOBF_SRG   = "/forge-{API_VERSION}-srg.jar";
    static final String FORGE_DECOMP      = "/forge-{API_VERSION}-decomp.jar";
    static final String FORGE_FORGED      = "/forge-{API_VERSION}-forged.jar";
    

    static final String FML_CACHE         = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}";
    // fml Bin jar
    static final String FML_DEOBF_MCP     = "/fmlBin-{API_VERSION}.jar";
    // fm Src jars
    static final String FML_RECOMP        = "/fmlSrc-{API_VERSION}.jar";
    static final String FML_REMAPPED      = "/fmlSrc-{API_VERSION}-sources.jar";
    // fml intermediate jars
    static final String FML_BINPATCHED    = "/fml-{API_VERSION}-binPatched.jar";
    static final String FML_DEOBF_SRG     = "/fml-{API_VERSION}-srg.jar";
    static final String FML_DECOMP        = "/fml-{API_VERSION}-decomp.jar";
    static final String FML_FMLED         = "/fml-{API_VERSION}-fmled.jar";

    static final String FML_PATCHES_ZIP   = PACK_DIR + "/fmlpatches.zip";
    static final String FORGE_PATCHES_ZIP = PACK_DIR + "/forgepatches.zip";

    static final String BINPATCHES        = PACK_DIR + "/devbinpatches.pack.lzma";
    static final String BINARIES_JAR      = PACK_DIR + "/binaries.jar";
    static final String JAVADOC_JAR       = PACK_DIR + "/javadoc.jar";

    static final String JSON              = PACK_DIR + "/dev.json";
    static final String ECLIPSE_LOCATION  = "eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects/Minecraft/.location";
}
