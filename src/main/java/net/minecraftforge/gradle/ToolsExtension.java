/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.plugins.ExtensionAware;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public sealed interface ToolsExtension extends ExtensionAware permits ToolsExtensionInternal {
    String NAME = "fgtools";
}
