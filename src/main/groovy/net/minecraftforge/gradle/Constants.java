/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

/// The package-private constants used throughout ForgeGradle.
///
/// Looking for attributes? They are in [MinecraftExtension.Attributes].
final class Constants {
    // Caches -- BE CAREFUL when changing this
    static final String CACHES_LOCATION = /* gradleUserHomeDir + */ "caches/minecraftforge/forgegradle";

    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";

    static final String MCMAVEN_VERSION = "0.3.2";
    static final String MCMAVEN_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/minecraft-mavenizer/" + MCMAVEN_VERSION + "/minecraft-mavenizer-" + MCMAVEN_VERSION + ".jar";
    static final String MCMAVEN_MAIN = "net.minecraftforge.mcmaven.cli.Main";
    static final int MCMAVEN_JAVA_VERSION = 21;

    static final String SL_VERSION = "0.1.0";
    static final String SL_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/slime-launcher/" + SL_VERSION + "/slime-launcher-" + SL_VERSION + ".jar";
    static final String SLIMELAUNCHER_MAIN = "net.minecraftforge.launcher.Main";
    static final int SLIMELAUNCHER_JAVA_VERSION = 8;

    /// Use these with [java.text.MessageFormat#format(String, Object...)].
    static final class Messages {
        static final String WELCOME = """
            Welcome to ForgeGradle 7.0!
            
            Here are some release highlights:
            - Complete rewrite of the plugin and underlying code.
            - Complete overhaul of DSL objects and registrations.
            - Slimmed down the plugin, which now delegates many actions to separate tools.
            - Support for declaring defaults in settings.gradle.
            - Support for all Forge versions from Minecraft 1.14.4 (older versions are a work-in-progress).
            
            This message will not display again until ForgeGradle 7.1 or the below file is deleted:
            {}
            
            For more details on this release, see https://docs.minecraftforge.net/en/fg-7.0/""";
    }
}
