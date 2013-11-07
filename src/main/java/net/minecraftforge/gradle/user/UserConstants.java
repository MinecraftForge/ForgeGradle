package net.minecraftforge.gradle.user;

public final class UserConstants
{
    private UserConstants()
    {
        // no touch
    }
    
    public static final String CONFIG_USERDEV = "userDevPackageDepConfig";
    public static final String CONFIG = "minecraft";
    
    public static final String PACK_DIR = "{BUILD_DIR}/unpacked";
    
    public static final String CONF_DIR = PACK_DIR + "/conf";
    public static final String MERGE_CFG = CONF_DIR + "/mcp_merge.cfg";
    public static final String ASTYLE_CFG = CONF_DIR + "/astyle.cfg";
    public static final String PACKAGED_SRG = CONF_DIR+"/packaged.srg";
    public static final String PACKAGED_EXC = CONF_DIR+"/packaged.exc";

    public static final String MAPPINGS_DIR = PACK_DIR + "/mappings";
    public static final String METHOD_CSV = MAPPINGS_DIR+"/methods.csv";
    public static final String FIELD_CSV = MAPPINGS_DIR+"/fields.csv";
    public static final String PARAM_CSV = MAPPINGS_DIR+"/params.csv";
    
    public static final String SRC_DIR = PACK_DIR + "/src";
    public static final String FML_AT = SRC_DIR + "/fml_at.cfg";
    public static final String FORGE_AT = SRC_DIR + "/forge_at.cfg";
    
    public static final String FORGE_BINPATCHED = "{CACHE_DIR}/net/minecraftforge/minecraftforge/{API_VERSION}/minecraftforge-{API_VERSION}.jar";
    public static final String FORGE_DEOBF_SRG = "{CACHE_DIR}/net/minecraftforge/minecraftforge/{API_VERSION}/minecraftforge-{API_VERSION}-srg.jar";
    
    public static final String FML_BINPATCHED = "{CACHE_DIR}/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}.jar";
    public static final String FML_DEOBF_SRG = "{CACHE_DIR}/cpw/mods/fml/{API_VERSION}/fml-{API_VERSION}-srg.jar";
    
    public static final String PATCHES_ZIP = PACK_DIR+"/patches.zip";
    public static final String BINPATCHES = PACK_DIR+"/devbinpatches.pack.lzma";
    public static final String JAVADOC_JAR = PACK_DIR+"/javadoc.jar";
    
    public static final String JSON = PACK_DIR+"/dev.json";
}