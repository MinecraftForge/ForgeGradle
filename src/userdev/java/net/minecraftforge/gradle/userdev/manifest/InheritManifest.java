/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.userdev.manifest;

import org.gradle.api.Action;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.ManifestMergeSpec;

public interface InheritManifest extends Manifest {
    InheritManifest inheritFrom(Object... inheritPaths);

    InheritManifest inheritFrom(Object inheritPaths, Action<ManifestMergeSpec> action);
}
