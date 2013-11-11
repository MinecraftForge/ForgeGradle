package net.minecraftforge.gradle.dev;

final class DevConstants
{
    private DevConstants()
    {

    }

    static final String INSTALLER_URL       = "http://files.minecraftforge.net/installer/forge-installer-{INSTALLER_VERSION}-shrunk.jar";

    // generated mapping related stuff
    static final String PACKAGED_SRG        = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.srg";
    static final String PACKAGED_EXC        = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.exc";
    static final String PACKAGED_PATCH      = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.patch";
    static final String DEOBF_DATA          = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/deobfuscation_data-{MC_VERSION}.lzma";

    // other generated stuff
    static final String INSTALLER_BASE      = "{BUILD_DIR}/tmp/installer_base.{INSTALLER_VERSION}.jar";
    static final String INSTALL_PROFILE     = "{BUILD_DIR}/tmp/install_profile.json";
    static final String REOBF_TMP           = "{BUILD_DIR}/tmp/recomp_obfed.jar";
    static final String JAVADOC_TMP         = "{BUILD_DIR}/tmp/javadoc";
    static final String BINPATCH_TMP        = "{BUILD_DIR}/tmp/bin_patches.jar";

    // mappings
    static final String METHODS_CSV         = "{MAPPINGS_DIR}/methods.csv";
    static final String FIELDS_CSV          = "{MAPPINGS_DIR}/fields.csv";
    static final String PARAMS_CSV          = "{MAPPINGS_DIR}/params.csv";
    static final String PACK_CSV            = "{MAPPINGS_DIR}/packages.csv";
    static final String JOINED_SRG          = "{MAPPINGS_DIR}/joined.srg";
    static final String JOINED_EXC          = "{MAPPINGS_DIR}/joined.exc";
    static final String ASTYLE_CFG          = "{MAPPINGS_DIR}/astyle.cfg";
    static final String MCP_PATCH           = "{MAPPINGS_DIR}/patches/minecraft_ff.patch";
    static final String MERGE_CFG           = "{FML_DIR}/mcp_merge.cfg";

    // jars.
    static final String JAR_SRG_FORGE       = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/minecraft_srg_forge-{MC_VERSION}.jar";
    static final String JAR_SRG_FML         = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/minecraft_srg_fml-{MC_VERSION}.jar";
    static final String ZIP_DECOMP_FML      = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_decomp/{MC_VERSION}/minecraft_decomp_fml-{MC_VERSION}.zip";
    static final String ZIP_DECOMP_FORGE    = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_decomp/{MC_VERSION}/minecraft_decomp_forge-{MC_VERSION}.zip";

    // fml intermediate jars
    static final String ZIP_PATCHED_FML     = "{BUILD_DIR}/fmlTmp/minecraft_patched.zip";

    // forge intermediate jars
    static final String ZIP_FMLED_FORGE     = "{BUILD_DIR}/forgeTmp/minecraft_fmlpatched.zip";
    static final String ZIP_INJECT_FORGE    = "{BUILD_DIR}/forgeTmp/minecraft_fmlinjected.zip";
    static final String ZIP_RENAMED_FORGE   = "{BUILD_DIR}/forgeTmp/minecraft_renamed.zip";
    static final String ZIP_PATCHED_FORGE   = "{BUILD_DIR}/forgeTmp/minecraft_patches.zip";

    // other stuff
    static final String CHANGELOG           = "{BUILD_DIR}/distributions/{PROJECT}-{MC_VERSION}-{VERSION}-changelog.txt";

    // jsons
    static final String JSON_DEV            = "{FML_DIR}/jsons/{MC_VERSION}-dev.json";
    static final String JSON_REL            = "{FML_DIR}/jsons/{MC_VERSION}-rel.json";
    static final String JSON_BASE           = "{FML_DIR}/jsons/{MC_VERSION}.json";

    // eclipse folders      More stuff only for the Dev plugins
    static final String WORKSPACE_ZIP       = "eclipse-workspace-dev.zip";
    static final String WORKSPACE           = "eclipse";
    static final String ECLIPSE_CLEAN       = WORKSPACE + "/Clean";
    static final String ECLIPSE_FML         = WORKSPACE + "/FML";
    static final String ECLIPSE_FORGE       = WORKSPACE + "/Forge";
    static final String ECLIPSE_RUN         = WORKSPACE + "/run";
    static final String ECLIPSE_NATIVES     = ECLIPSE_RUN + "/bin/natives";
    static final String ECLIPSE_ASSETS      = ECLIPSE_RUN + "/bin/assets";

    // FML stuff only...
    static final String FML_PATCH_DIR       = "{FML_DIR}/patches/minecraft";
    static final String FML_SOURCES         = "{FML_DIR}/src/main/java";
    static final String FML_RESOURCES       = "{FML_DIR}/src/main/resources";
    static final String FML_TEST_SOURCES    = "{FML_DIR}/src/test/java";
    static final String FML_VERSIONF        = "{FML_DIR}/build/tmp/fmlversion.properties";
    static final String FML_LICENSE         = "{FML_DIR}/LICENSE-fml.txt";
    static final String FML_CREDITS         = "{FML_DIR}/CREDITS-fml.txt";
    static final String FML_LOGO            = "{FML_DIR}/jsons/big_logo.png";

    // Forge stuff only
    static final String FORGE_PATCH_DIR     = "patches/minecraft";
    static final String FORGE_SOURCES       = "src/main/java";
    static final String FORGE_RESOURCES     = "src/main/resources";
    static final String FORGE_TEST_SOURCES  = "src/test/java";
    static final String FORGE_LICENSE       = "MinecraftForge-License.txt";
    static final String FORGE_CREDITS       = "MinecraftForge-Credits.txt";
    static final String PAULSCODE_LISCENCE1 = "Paulscode IBXM Library License.txt";
    static final String PAULSCODE_LISCENCE2 = "Paulscode SoundSystem CodecIBXM License.txt";
    static final String FORGE_LOGO          = FORGE_RESOURCES + "/forge_logo.png";
}
