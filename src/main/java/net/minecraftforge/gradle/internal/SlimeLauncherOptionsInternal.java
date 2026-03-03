/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.SlimeLauncherOptions;
import net.minecraftforge.gradle.SlimeLauncherOptionsNested;
import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import java.util.Map;

public interface SlimeLauncherOptionsInternal extends SlimeLauncherOptions, HasPublicType {
    Logger LOGGER = Logging.getLogger(SlimeLauncherOptions.class);

    @Override
    default @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(SlimeLauncherOptions.class);
    }

    @Input @Optional Property<Boolean> getClient();

    @Override
    @Internal NamedDomainObjectContainer<? extends ModConfig> getMods();

    @Nested MapProperty<String, SlimeLauncherOptionsNested> getNested();

    default SlimeLauncherOptionsInternal inherit(Map<String, RunConfig> configs, String sourceSetName) {
        return this.inherit(configs, sourceSetName, this.getName());
    }

    SlimeLauncherOptionsInternal inherit(Map<String, RunConfig> configs, String sourceSetName, String name);
}
