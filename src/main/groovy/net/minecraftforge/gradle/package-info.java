/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
/// ## ForgeGradle
///
/// This package houses the entirety of ForgeGradle, a simple plugin designed to bootstrap the process of using
/// Minecraft as a dependency for your Gradle project.
///
/// The plugin itself is a Java-Groovy mixture with the public-facing interface APIs being written in Java. Thus, their
/// JavaDocs are included in these JavaDoc pages.
///
/// The Groovy classes in this plugin consist of implementations of the APIs, all of which are either
/// [internal][org.jetbrains.annotations.ApiStatus.Internal] or package-private.
///
/// ### Consumption in Gradle DSL Scripts
///
/// ForgeGradle is designed to be used as a plugin for Gradle scripts. It is written with the Groovy DSL in mind, so we
/// recommend using that for buildscripts.
///
/// Unlike many traditional Gradle plugins, ForgeGradle is *not* provided on the Gradle Plugin Portal due to its
/// reliance on external tools that Forge hosts.
@NotNullByDefault
package net.minecraftforge.gradle;

import org.jetbrains.annotations.NotNullByDefault;
