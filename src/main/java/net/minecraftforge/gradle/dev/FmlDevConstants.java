package net.minecraftforge.gradle.dev;

public final class FmlDevConstants
{
    // no touch :P
    private FmlDevConstants()
    {
    }

    // mappings
    public static final String METHOD_CSV       = "{MAPPINGS_DIR}/methods.csv";
    public static final String FIELDS_CSV       = "{MAPPINGS_DIR}/fields.csv";
    public static final String PARAMS_CSV       = "{MAPPINGS_DIR}/params.csv";
    public static final String PACK_CSV         = "{MAPPINGS_DIR}/packages.csv";
    public static final String JOINED_SRG       = "{MAPPINGS_DIR}/joined.srg";
    public static final String JOINED_EXC       = "{MAPPINGS_DIR}/joined.exc";
    public static final String ASTYLE_CFG       = "{MAPPINGS_DIR}/astyle.cfg";
    public static final String FML_PATCH_DIR    = "{FML_DIR}/patches/minecraft";
    public static final String JSON_DEV         = "{FML_DIR}/jsons/{MC_VERSION}-dev.json";
    public static final String JSON_REL         = "{FML_DIR}/jsons/{MC_VERSION}-rel.json";
    public static final String JSON_BASE        = "{FML_DIR}/jsons/{MC_VERSION}.json";
    public static final String FML_SOURCES      = "{FML_DIR}/src/main/java";
    public static final String FML_RESOURCES    = "{FML_DIR}/src/main/resources";
    public static final String FML_TEST_SOURCES = "{FML_DIR}/src/test/resources";
    public static final String FML_VERSIONF     = "{BUILD_DIR}/tmp/fmlversion.properties";
    public static final String FML_LICENSE      = "{FML_DIR}/LICENSE-fml.txt";
    public static final String FML_LOGO         = "{FML_DIR}/jsons/big_logo.png";

    // various useful files
    public static final String MCP_PATCH        = "{MAPPINGS_DIR}/patches/minecraft_ff.patch";
    public static final String MERGE_CFG        = "mcp_merge.cfg";
    public static final String FML_ECLIPSE_WS   = "{FML_DIR}/eclipse-workspace-dev.zip";
    public static final String CHANGELOG        = "{BUILD_DIR}/libs/{PROJECT}-{MC_VERSION}-{VERSION}-changelog.txt";
}
