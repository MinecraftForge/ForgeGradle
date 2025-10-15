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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

non-sealed interface SlimeLauncherOptionsInternal extends SlimeLauncherOptions, HasPublicType {
    Logger LOGGER = Logging.getLogger(SlimeLauncherOptions.class);

    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(SlimeLauncherOptions.class);
    }

    Property<Boolean> getClient();

    default void inherit(Map<String, RunConfig> configs) {
        this.inherit(configs, this.getName());
    }

    default void inherit(Map<String, RunConfig> configs, String name) {
        var config = configs.get(name);
        if (config == null) return;

        if (config.parents != null && !config.parents.isEmpty())
            config.parents.forEach(parent -> this.inherit(configs, parent));

        if (config.main != null)
            this.getMainClass().convention(config.main);

        if (config.args != null && !config.args.isEmpty())
            this.getArgs().convention(List.copyOf(config.args));

        if (config.jvmArgs != null && !config.jvmArgs.isEmpty())
            this.jvmArgs(config.jvmArgs);

        this.getClient().set(config.client);

        if (config.buildAllProjects)
            LOGGER.warn("WARNING: ForgeGradle 7 does not support the buildAllProjects feature.");

        if (config.env != null && !config.env.isEmpty())
            this.environment(config.env);

        if (config.props != null && !config.props.isEmpty())
            this.systemProperties(config.props);
    }
}
