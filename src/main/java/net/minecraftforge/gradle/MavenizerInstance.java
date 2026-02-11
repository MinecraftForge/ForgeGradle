/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

import java.io.File;

public interface MavenizerInstance extends ProviderConvertible<ExternalModuleDependency> {
    @Override Provider<ExternalModuleDependency> asProvider();
    Provider<String> getMappingVersion();
    Provider<String> getToSrg();
    Provider<File> getToSrgFile();
    Provider<String> getToObf();
    Provider<File> getToObfFile();
}
