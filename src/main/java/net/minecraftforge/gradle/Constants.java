/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

/// The package-private constants used throughout ForgeGradle.
///
/// Looking for attributes? They are in [MinecraftExtension.Attributes].
final class Constants {
    static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    static final String MC_LIBS_MAVEN = "https://libraries.minecraft.net/";

    /// Use these with [java.text.MessageFormat#format(String, Object...)].
    static final class Messages {
        static final String WELCOME = """
            Welcome to ForgeGradle 7.0!
            
            Here are some release highlights:
            - Complete rewrite of the plugin and underlying code.
            - Complete overhaul of DSL objects and registrations.
            - Slimmed down the plugin, which now delegates many actions to separate tools.
            - Support for declaring defaults in settings.gradle.
            - Support for all Forge versions from Minecraft 1.20.6 (older versions are a
              work-in-progress).
            
            A couple of important things to note:
            - ForgeGradle 6 and older will no longer be supported, except for critical bug
              fixes. We will (gently) encourage developers to move away from them and use
              ForgeGradle 7 instead.
            - Many plugins that worked with ForgeGradle 6, such as Parchment's Librarian,
              do not work with ForgeGradle 7. For most cases (such as parchment), we have
              implemented native support. If ForgeGradle 7 is lacking in some aspect,
              please let us know!
            - Many things that ForgeGradle 6 and older used to do are now decentralized
              away from the plugin. This means that your project will need to apply
              'net.minecraftforge.accesstransformers' if you wish to use AccessTransformers.
              If you are on an older version (1.20.4 and older), you will need the
              'net.minecraftforge.obfuscation' plugin. Many of these come with our provided
              MDK, so this should not be an issue for you.
            
            This message will not display again until ForgeGradle 7.1 or the below file is deleted:
            {}
            
            For more details on this release, see https://docs.minecraftforge.net/en/fg-7.0/""";
    }
}
