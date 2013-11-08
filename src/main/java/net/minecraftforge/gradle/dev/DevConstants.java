package net.minecraftforge.gradle.dev;

public final class DevConstants
{
    private DevConstants()
    {

    }

    public static final String INSTALLER_URL   = "http://files.minecraftforge.net/installer/forge-installer-{INSTALLER_VERSION}-shrunk.jar";

    public static final String PACKAGED_SRG    = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.srg";
    public static final String PACKAGED_EXC    = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.exc";
    public static final String PACKAGED_PATCH  = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/packaged-{MC_VERSION}.patch";
    public static final String DEOBF_DATA      = "{CACHE_DIR}/minecraft/net/minecraft/minecraft_srg/{MC_VERSION}/deobfuscation_data-{MC_VERSION}.lzma";

    public static final String INSTALLER_BASE  = "{BUILD_DIR}/tmp/installer_base.{INSTALLER_VERSION}.jar";
    public static final String INSTALL_PROFILE = "{BUILD_DIR}/tmp/install_profile.json";
    public static final String REOBF_TMP       = "{BUILD_DIR}/tmp/recomp_obfed.jar";
    public static final String JAVADOC_TMP     = "{BUILD_DIR}/tmp/javadoc.jar";
    public static final String BINPATCH_TMP    = "{BUILD_DIR}/tmp/bin_patches.jar";

    // eclipse folders      More stuff only for the Dev plugins
    public static final String WORKSPACE       = "eclipse";
    public static final String ECLIPSE_CLEAN   = WORKSPACE + "/Clean";
    public static final String ECLIPSE_FML     = WORKSPACE + "/FML";
    public static final String ECLIPSE_RUN     = WORKSPACE + "/run";
    public static final String ECLIPSE_NATIVES = ECLIPSE_RUN + "/bin/natives";

    // src dirs   for only the DEV plugins
    public static final String SRC_DIR         = "src/main/java";
    public static final String RES_DIR         = "src/main/resources";
    public static final String TEST_DIR        = "src/test/java";
}
