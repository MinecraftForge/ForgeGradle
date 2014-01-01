package net.minecraftforge.gradle.user;

public final class UserConstants
{
    private UserConstants()
    {
        // no touch
    }

    static final String CONFIG_USERDEV      = "userDevPackageDepConfig";
    static final String CONFIG_NATIVES      = "minecraftNatives";
    static final String CONFIG_API_JAVADOCS = "apiJavaDocsConfig";
    static final String CONFIG_API_SRC      = "apiSrcConfig";
    static final String CONFIG              = "minecraft";

    static final String PACK_DIR    = "{BUILD_DIR}/unpacked";
    static final String NATIVES_DIR = "{BUILD_DIR}/natives";
    static final String SOURCES_DIR = "{BUILD_DIR}/sources";

    static final String CONF_DIR      = PACK_DIR + "/conf";
    static final String MERGE_CFG     = CONF_DIR + "/mcp_merge.cfg";
    static final String MCP_PATCH_DIR = CONF_DIR + "/minecraft_ff";
    static final String ASTYLE_CFG    = CONF_DIR + "/astyle.cfg";
    static final String PACKAGED_SRG  = CONF_DIR + "/packaged.srg";
    static final String PACKAGED_EXC  = CONF_DIR + "/packaged.exc";
    static final String EXC_JSON      = CONF_DIR + "/exceptor.json";

           static final String DEOBF_MCP_SRG   = CONF_DIR + "/notch-mcp.srg";
    public static final String REOBF_SRG       = CONF_DIR + "/mcp-srg.srg";
           static final String REOBF_NOTCH_SRG = CONF_DIR + "/mcp-notch.srg";

           static final String MAPPINGS_DIR = PACK_DIR + "/mappings";
    public static final String METHOD_CSV   = MAPPINGS_DIR + "/methods.csv";
    public static final String FIELD_CSV    = MAPPINGS_DIR + "/fields.csv";
           static final String PARAM_CSV    = MAPPINGS_DIR + "/params.csv";

    static final String SRC_DIR  = PACK_DIR + "/src/main/java";
    static final String RES_DIR  = PACK_DIR + "/src/main/resources";
    static final String FML_AT   = RES_DIR + "/fml_at.cfg";
    static final String FORGE_AT = RES_DIR + "/forge_at.cfg";

    static final String FORGE_CACHE           = "{CACHE_DIR}/minecraft/net/minecraftforge/forge/{API_VERSION}";
    static final String FORGE_BINPATCHED      = FORGE_CACHE + "/forge-{API_VERSION}.jar";
    static final String FORGE_DEOBF_MCP       = FORGE_CACHE + "/forge-{API_VERSION}-mcp.jar";
    static final String FORGE_DEOBF_SRG       = FORGE_CACHE + "/forge-{API_VERSION}-srg.jar";
    static final String FORGE_DECOMP          = FORGE_CACHE + "/forge-{API_VERSION}-decomp.jar";
    static final String FORGE_FMLED           = FORGE_CACHE + "/forge-{API_VERSION}-fmled.jar";
    static final String FORGE_FMLINJECTED     = FORGE_CACHE + "/forge-{API_VERSION}-fmlinjected.jar";
    static final String FORGE_REMAPPED        = FORGE_CACHE + "/forge-{API_VERSION}-mcped.jar";
    static final String FORGE_FORGED          = FORGE_CACHE + "/forge-{API_VERSION}-src-nojd.jar";
    static final String FORGE_FORGEJAVADOCCED = FORGE_CACHE + "/forge-{API_VERSION}-src.jar";

    static final String FML_BINPATCHED = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}.jar";
    static final String FML_DEOBF_MCP  = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-mcp.jar";
    static final String FML_DEOBF_SRG  = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-srg.jar";
    static final String FML_DECOMP     = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-decomp.jar";
    static final String FML_FMLED      = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-fmled.jar";
    static final String FML_INJECTED   = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-src-injected.jar";
    static final String FML_REMAPPED   = "{CACHE_DIR}/minecraft/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-src.jar";

    static final String FML_PATCHES_ZIP   = PACK_DIR+"/fmlpatches.zip";
    static final String FORGE_PATCHES_ZIP = PACK_DIR+"/forgepatches.zip";

    static final String BINPATCHES   = PACK_DIR+"/devbinpatches.pack.lzma";
    static final String BINARIES_JAR = PACK_DIR+"/binaries.jar";
    static final String JAVADOC_JAR  = PACK_DIR+"/javadoc.jar";

    static final String JSON = PACK_DIR+"/dev.json";
    static final String ECLIPSE_LOCATION = "eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects/Minecraft/.location";
}
