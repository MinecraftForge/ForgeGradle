/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.Tool;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class Tools {
    private Tools() { }

    private static final List<Tool> ALL = new ArrayList<>();

    private static Tool tool(String name, String version, String dlUrl, int javaVersion, @UnknownNullability String mainClass) {
        var ret = Tool.of(name, version, dlUrl, javaVersion, mainClass);
        ALL.add(ret);
        return ret;
    }

    static void forEach(Consumer<? super Tool> consumer) {
        ALL.forEach(consumer);
    }

    private static final String SLIMELAUNCHER_NAME = "slimelauncher";
    private static final String SLIMELAUNCHER_VERSION = "0.1.6";
    private static final String SLIMELAUNCHER_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/slime-launcher/" + SLIMELAUNCHER_VERSION + "/slime-launcher-" + SLIMELAUNCHER_VERSION + ".jar";
    private static final int SLIMELAUNCHER_JAVA_VERSION = 8;
    private static final String SLIMELAUNCHER_MAIN = "net.minecraftforge.launcher.Main";
    static final Tool SLIMELAUNCHER = tool(SLIMELAUNCHER_NAME, SLIMELAUNCHER_VERSION, SLIMELAUNCHER_DL_URL, SLIMELAUNCHER_JAVA_VERSION, SLIMELAUNCHER_MAIN);

    private static final String MAVENIZER_NAME = "mavenizer";
    private static final String MAVENIZER_VERSION = "0.3.13";
    private static final String MAVENIZER_DL_URL = "https://maven.minecraftforge.net/net/minecraftforge/minecraft-mavenizer/" + MAVENIZER_VERSION + "/minecraft-mavenizer-" + MAVENIZER_VERSION + ".jar";
    private static final int MAVENIZER_JAVA_VERSION = 21;
    private static final String MAVENIZER_MAIN = "net.minecraftforge.mcmaven.cli.Main";
    static final Tool MAVENIZER = tool(MAVENIZER_NAME, MAVENIZER_VERSION, MAVENIZER_DL_URL, MAVENIZER_JAVA_VERSION, MAVENIZER_MAIN);
}
