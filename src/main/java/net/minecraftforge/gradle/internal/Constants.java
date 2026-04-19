/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.apache.maven.artifact.versioning.ComparableVersion;

/// The package-private constants used throughout ForgeGradle.
///
/// Looking for attributes? They are in [ForgeGradleExtension.Attributes].
final class Constants {
    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    static final String MC_LIBS_MAVEN = "https://libraries.minecraft.net/";

    /// Use these with [java.text.MessageFormat#format(String, Object...)].
    static final class Messages {
        static final String WELCOME = """
            Welcome to ForgeGradle 7.0!

            Here are some release highlights:
            - Now requires Gradle 9.3.0.
            - A new message board that will display any new messages (only on the next
              successful build) from ForgeGradle, including release highlights and
              project-wide warnings.
            - Complete rewrite of the plugin and underlying code.
            - Complete overhaul of DSL objects and registrations.
            - Slimmed down the plugin, which now delegates many actions to separate tools.
            - Support for declaring defaults in settings.gradle (experimental).
            - Support for all Forge versions that are supported by ForgeGradle 6.
              - Minecraft 1.20.6+ are natively supported with no additional setup.
              - Minecraft 1.13.2 - 1.20.4 (along with certain versions of 1.12.2) are
                supported alongside the usage of the Renamer plugin (experimental).

            A couple of important things to note:
            - Many plugins that worked with ForgeGradle 6, such as Parchment's Librarian,
              do not work with ForgeGradle 7. For most cases (such as parchment), we have
              implemented native support.
            - Many things that ForgeGradle 6 and older used to do are now decentralized
              away from the plugin. This means that your project will need to apply
              'net.minecraftforge.jarjar' if you wish to use Forge's Jar-in-Jar system.
              If you are on an older version (1.20.4 and older), you will need the
              'net.minecraftforge.renamer' plugin. Many of these come with our provided
              MDK, so this should not be an issue for you.
            - If you come across any lingering bugs, please report them to our GitHub
              issue tracker. Please do not use Discord as a means to report issues, as
              they will get lost in the discussion very easily.
            - If you would like to see additional examples of ForgeGradle 7 usages,
              please see our MDKExamples repo (https://github.com/MinecraftForge/MDKExamples).
              If would like to request a specific usage for ForgeGradle 7, please file an
              issue on the GitHub issue tracker for it.

            For our GitHub issue tracker, see https://github.com/MinecraftForge/ForgeGradle/issues

            Proper documentation for ForgeGradle 7 is coming soon. For now, feel free to
            ask any questions you may have on the following platforms:
            - Forums (https://forums.minecraftforge.net/)
            - Discord (https://discord.minecraftforge.net/)""";

        static final String WELCOME_CONDITION = """
            This message will not display again until ForgeGradle 7.1 or the below file is
            deleted:
            {}""";

        // TODO [ForgeGradle][Magic] Rewrite this to be more informative as to what is actually happening.
        //      As of right now, this remains unused.
        static final String MAGIC = """
            This build is using ForgeGradle Magic. ForgeGradle Magic employs automatic
            behavior that is hidden from buildscript authors in order to implement and
            account for convenience features.

            Magic is enabled by default. It can be disabled by using the following
            Gradle property:
            net.minecraftforge.gradle.magic=false

            For more information, see https://docs.minecraftforge.net/en/fg-7.0/magic/""";
    }

    static class Mavenizer {
        /*
         * Add support for --facade configs, a system that allows consumers to attach interfaces to the dependency.
         * https://github.com/MinecraftForge/MinecraftMavenizer/commit/610bb2b11d6999e98d44cc4a87ec926e68d26a37
         */
        static final ComparableVersion SUPPORTS_FACADES = new ComparableVersion("0.4.61");

        /*
         * Also added --local-cache which is a project specific caching folder, used for post processors such as ATs and Facades
         */
        static final ComparableVersion SUPPORTS_LOCAL_CACHE = new ComparableVersion("0.4.56");
    }
}
