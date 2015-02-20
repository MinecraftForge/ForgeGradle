package net.minecraftforge.gradle.user.patch;

public class UserPatchConstants
{
    static final String SRC_DIR            = "{USER_DEV}/src/main/java";
    static final String RES_DIR            = "{USER_DEV}/src/main/resources";
    static final String FML_AT             = RES_DIR + "/fml_at.cfg";
    static final String FORGE_AT           = RES_DIR + "/forge_at.cfg";

    static final String BINPATCHES         = "{USER_DEV}/devbinpatches.pack.lzma";
    static final String BINARIES_JAR       = "{USER_DEV}/binaries.jar";
    static final String JAVADOC_JAR        = "{USER_DEV}/javadoc.jar";

    static final String JSON               = "{USER_DEV}/dev.json";
    static final String ECLIPSE_LOCATION   = "eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects";

    static final String JAR_BINPATCHED     = "{API_CACHE_DIR}/{API_NAME}-binpatched-{API_VERSION}.jar";

    static final String CLASSIFIER_PATCHED = "patched";

    static final String FML_PATCHES_ZIP    = "{USER_DEV}/fmlpatches.zip";
    static final String FORGE_PATCHES_ZIP  = "{USER_DEV}/forgepatches.zip";

    static final String START_DIR          = "{API_CACHE_DIR}/start";
}
