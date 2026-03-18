/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.Tool;

final class Tools {
    private Tools() { }

    static final Tool SLIMELAUNCHER = Tool.ofForge("slimelauncher", "net.minecraftforge:slime-launcher:0.1.11", 8, "net.minecraftforge.launcher.Main");

    static final Tool MAVENIZER = Tool.ofForge("mavenizer", "net.minecraftforge:minecraft-mavenizer:0.4.48", 25, "net.minecraftforge.mcmaven.cli.Main");
}
