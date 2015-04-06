package net.minecraftforge.gradle.dev;

import net.minecraftforge.gradle.common.Constants;

public final class PatcherConstants
{
    // @formatter:off
    private PatcherConstants() {}
    // @formatter:on

    // new project defaults
    static final String DEFAULT_PATCHES_DIR        = "patches";
    static final String DEFAULT_SOURCES_DIR        = "src/main/java";
    static final String DEFAULT_RESOURCES_DIR      = "src/main/resources";
    static final String DEFAULT_TEST_SOURCES_DIR   = "src/test/java";
    static final String DEFAULT_TEST_RESOURCES_DIR = "src/test/resources";

    static final String DIR_LOCAL_CACHE            = Constants.REPLACE_BUILD_DIR + "/localCache";
    static final String JAR_DEOBF                  = DIR_LOCAL_CACHE + "/deobfuscated.jar";
    static final String JAR_DECOMP                 = DIR_LOCAL_CACHE + "/decompiled.jar";
    static final String JAR_DECOMP_POST            = DIR_LOCAL_CACHE + "/decompiled-processed.jar";
    static final String JAR_REMAPPED               = DIR_LOCAL_CACHE + "/remapped-clean.jar";

    static final String JAR_PATCHED_PROJECT        = DIR_LOCAL_CACHE + "/patched-%s.jar";
    static final String JAR_REMAPPED_PROJECT       = DIR_LOCAL_CACHE + "/remapped-%s.jar";
    static final String JAR_RRETROMAPPED_PROJECT   = DIR_LOCAL_CACHE + "/retromapped-%s.jar";

    static final String TASK_SETUP                 = "setup";
    static final String TASK_DEOBF_JAR             = "deobfuscateJar";
    static final String TASK_PATCH_JAR             = "patchJar";
    static final String TASK_PROJECT_REMAP_JAR     = "remap%sJar";
    static final String TASK_PROJECT_EXTRACT_JAR   = "extract%sJar";
}
