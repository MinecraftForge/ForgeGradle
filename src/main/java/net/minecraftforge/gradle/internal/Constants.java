/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.ForgeGradleExtension;

/// The package-private constants used throughout ForgeGradle.
///
/// Looking for attributes? They are in [ForgeGradleExtension.Attributes].
final class Constants {
    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    static final String MC_LIBS_MAVEN = "https://libraries.minecraft.net/";

    static final String SLIMELAUNCHER_NAME = "slimelauncher";
    static final String SLIMELAUNCHER_VERSION = "0.1.6";
    static final String SLIMELAUNCHER_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/slime-launcher/" + SLIMELAUNCHER_VERSION + "/slime-launcher-" + SLIMELAUNCHER_VERSION + ".jar";
    static final int SLIMELAUNCHER_JAVA_VERSION = 8;
    static final String SLIMELAUNCHER_MAIN = "net.minecraftforge.launcher.Main";

    static final String MAVENIZER_NAME = "mavenizer";
    static final String MAVENIZER_VERSION = "0.4.18";
    static final String MAVENIZER_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/minecraft-mavenizer/" + MAVENIZER_VERSION + "/minecraft-mavenizer-" + MAVENIZER_VERSION + ".jar";
    static final int MAVENIZER_JAVA_VERSION = 25;
    static final String MAVENIZER_MAIN = "net.minecraftforge.mcmaven.cli.Main";

    /// Use these with [java.text.MessageFormat#format(String, Object...)].
    static final class Messages {
        static final String WELCOME = """
            Welcome to ForgeGradle 7.0 Release Candidate!
            
            Here are some release highlights:
            - Complete rewrite of the plugin and underlying code.
            - Complete overhaul of DSL objects and registrations.
            - Slimmed down the plugin, which now delegates many actions to separate tools.
            - Support for declaring defaults in settings.gradle.
            - Support for all Forge versions from Minecraft 1.20.6 (older versions are a
              work-in-progress).
            
            A couple of important things to note:
            - Many plugins that worked with ForgeGradle 6, such as Parchment's Librarian,
              do not work with ForgeGradle 7. For most cases (such as parchment), we have
              implemented native support. If ForgeGradle 7 is lacking in some aspect,
              please let us know!
            - Many things that ForgeGradle 6 and older used to do are now decentralized
              away from the plugin. This means that your project will need to apply
              'net.minecraftforge.jarjar' if you wish to use Forge's Jar-in-Jar system.
              If you are on an older version (1.20.4 and older), you will need the
              'net.minecraftforge.renamer' plugin. Many of these come with our provided
              MDK, so this should not be an issue for you.
            - If you come across any lingering bugs, please report them to our GitHub
              issue tracker (https://github.com/MinecraftForge/ForgeGradle/issues).
              Please do not use Discord as a means to report issues, as they will get
              lost in the discussion very easily.
            
            For more details on this release, see https://docs.minecraftforge.net/en/fg-7.0/""";

        static final String WELCOME_CONDITION = """
            This message will not display again until ForgeGradle 7.0 or the below file is
            deleted:
            {}
            
            Documentation will be coming at a later date. Thank you for testing
            ForgeGradle 7!""";

        static final String MAGIC = """
            This build is using ForgeGradle Magic. ForgeGradle Magic employs automatic
            behavior that is hidden from buildscript authors in order to implement and
            account for convenience features.
            
            Magic is enabled by default. It can be disabled by using the following
            Gradle property:
            net.minecraftforge.gradle.magic=false
            
            For more information, see https://docs.minecraftforge.net/en/fg-7.0/magic/""";
    }
}
