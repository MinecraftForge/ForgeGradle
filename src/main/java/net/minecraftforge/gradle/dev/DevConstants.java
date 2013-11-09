package net.minecraftforge.gradle.dev;

final class DevConstants
{
    private DevConstants()
    {

    }

    static final String INSTALLER_URL    = "http://files.minecraftforge.net/installer/forge-installer-{INSTALLER_VERSION}-shrunk.jar";

    // generated mapping related stuff
    static final String PACKAGED_SRG     = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.srg";
    static final String PACKAGED_EXC     = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.exc";
    static final String PACKAGED_PATCH   = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.patch";
    static final String DEOBF_DATA       = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/deobfuscation_data-{MC_VERSION}.lzma";

    // other generated stuff
    static final String INSTALLER_BASE   = "{BUILD_DIR}/tmp/installer_base.{INSTALLER_VERSION}.jar";
    static final String INSTALL_PROFILE  = "{BUILD_DIR}/tmp/install_profile.json";
    static final String REOBF_TMP        = "{BUILD_DIR}/tmp/recomp_obfed.jar";
    static final String JAVADOC_TMP      = "{BUILD_DIR}/tmp/javadoc.jar";
    static final String BINPATCH_TMP     = "{BUILD_DIR}/tmp/bin_patches.jar";

    // mappings
    static final String METHOD_CSV       = "{MAPPINGS_DIR}/methods.csv";
    static final String FIELDS_CSV       = "{MAPPINGS_DIR}/fields.csv";
    static final String PARAMS_CSV       = "{MAPPINGS_DIR}/params.csv";
    static final String PACK_CSV         = "{MAPPINGS_DIR}/packages.csv";
    static final String JOINED_SRG       = "{MAPPINGS_DIR}/joined.srg";
    static final String JOINED_EXC       = "{MAPPINGS_DIR}/joined.exc";
    static final String ASTYLE_CFG       = "{MAPPINGS_DIR}/astyle.cfg";
    static final String MCP_PATCH        = "{MAPPINGS_DIR}/patches/minecraft_ff.patch";
    static final String MERGE_CFG        = "mcp_merge.cfg";

    // opther stuff
    static final String CHANGELOG        = "{BUILD_DIR}/libs/{PROJECT}-{MC_VERSION}-{VERSION}-changelog.txt";

    // jsons
    static final String JSON_DEV         = "{FML_DIR}/jsons/{MC_VERSION}-dev.json";
    static final String JSON_REL         = "{FML_DIR}/jsons/{MC_VERSION}-rel.json";
    static final String JSON_BASE        = "{FML_DIR}/jsons/{MC_VERSION}.json";

    // eclipse folders      More stuff only for the Dev plugins
    static final String WORKSPACE_ZIP    = "{FML_DIR}/eclipse-workspace-dev.zip";
    static final String WORKSPACE        = "eclipse";
    static final String ECLIPSE_CLEAN    = WORKSPACE + "/Clean";
    static final String ECLIPSE_FML      = WORKSPACE + "/FML";
    static final String ECLIPSE_RUN      = WORKSPACE + "/run";
    static final String ECLIPSE_NATIVES  = ECLIPSE_RUN + "/bin/natives";

    // src dirs   for only the DEV plugins
    static final String SRC_DIR          = "src/main/java";
    static final String RES_DIR          = "src/main/resources";
    static final String TEST_DIR         = "src/test/java";

    // FML stuff only...
    static final String FML_PATCH_DIR    = "{FML_DIR}/patches/minecraft";
    static final String FML_SOURCES      = "{FML_DIR}/src/main/java";
    static final String FML_RESOURCES    = "{FML_DIR}/src/main/resources";
    static final String FML_TEST_SOURCES = "{FML_DIR}/src/test/resources";
    static final String FML_VERSIONF     = "{BUILD_DIR}/tmp/fmlversion.properties";
    static final String FML_LICENSE      = "{FML_DIR}/LICENSE-fml.txt";
    static final String FML_CREDITS      = "{FML_DIR}/CREDITS-fml.txt";
    static final String FML_LOGO         = "{FML_DIR}/jsons/big_logo.png";
}
