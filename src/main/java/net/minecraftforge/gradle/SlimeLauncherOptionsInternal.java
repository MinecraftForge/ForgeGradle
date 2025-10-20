/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.util.data.json.RunConfig;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

non-sealed interface SlimeLauncherOptionsInternal extends SlimeLauncherOptions, HasPublicType {
    Logger LOGGER = Logging.getLogger(SlimeLauncherOptions.class);

    @Override
    default @Internal TypeOf<?> getPublicType() {
        return TypeOf.typeOf(SlimeLauncherOptions.class);
    }

    @Input @Optional Property<Boolean> getClient();

    default SlimeLauncherOptionsInternal inherit(Map<String, RunConfig> configs) {
        return this.inherit(configs, this.getName());
    }

    SlimeLauncherOptionsInternal inherit(Map<String, RunConfig> configs, String name);
}
