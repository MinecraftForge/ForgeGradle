/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.Tool;

final class Tools {
    private Tools() { }

    static final Tool SLIMELAUNCHER = Tool.of(Constants.SLIMELAUNCHER_NAME, Constants.SLIMELAUNCHER_VERSION, Constants.SLIMELAUNCHER_DL_URL, Constants.SLIMELAUNCHER_JAVA_VERSION, Constants.SLIMELAUNCHER_MAIN);

    static final Tool MAVENIZER = Tool.of(Constants.MAVENIZER_NAME, Constants.MAVENIZER_VERSION, Constants.MAVENIZER_DL_URL, Constants.MAVENIZER_JAVA_VERSION, Constants.MAVENIZER_MAIN);
}
