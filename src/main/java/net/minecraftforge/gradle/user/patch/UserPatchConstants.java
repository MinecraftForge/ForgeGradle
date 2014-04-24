package net.minecraftforge.gradle.user.patch;

public class UserPatchConstants
{
    static final String PACK_DIR           = "{API_CACHE_DIR}/unpacked";

    static final String SRC_DIR            = PACK_DIR + "/src/main/java";
    static final String RES_DIR            = PACK_DIR + "/src/main/resources";
    static final String FML_AT             = RES_DIR + "/fml_at.cfg";
    static final String FORGE_AT           = RES_DIR + "/forge_at.cfg";

    static final String BINPATCHES         = PACK_DIR + "/devbinpatches.pack.lzma";
    static final String BINARIES_JAR       = PACK_DIR + "/binaries.jar";
    static final String JAVADOC_JAR        = PACK_DIR + "/javadoc.jar";

    static final String JSON               = PACK_DIR + "/dev.json";
    static final String ECLIPSE_LOCATION   = "eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects/Minecraft/.location";

    static final String FORGE_CACHE        = "{CACHE_DIR}/minecraft/net/minecraftforge/forge/{API_VERSION}";

    static final String JAR_BINPATCHED     = "{API_CACHE_DIR}/{API_NAME}-binpatched-{API_VERSION}.jar";

    static final String CLASSIFIER_PATCHED = "patched";

    static final String FML_PATCHES_ZIP    = PACK_DIR + "/fmlpatches.zip";
    static final String FORGE_PATCHES_ZIP  = PACK_DIR + "/forgepatches.zip";
}
