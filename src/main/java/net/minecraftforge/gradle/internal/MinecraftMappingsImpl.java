/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.model.ObjectFactory;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.Serial;

abstract class MinecraftMappingsImpl implements MinecraftMappingsInternal {
    private static final @Serial long serialVersionUID = 6944934115402768018L;

    private final String channel;
    private final @Nullable String version;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public MinecraftMappingsImpl(String channel, String version) {
        var problems = this.getObjects().newInstance(ForgeGradleProblems.class);
        this.channel = Util.checkMappingsParam(problems, channel, "channel");
        if (this.channel.equals("parchment"))
            this.version = ParchmentVersion.parse(Util.checkMappingsParam(problems, version, "version")).toFriendly();
        else if (this.channel.equals("auto"))
            this.version = null;
        else if (this.channel.equals("official"))
            this.version = version.isEmpty() ? null : version;
        else
            this.version = Util.checkMappingsParam(problems, version, "version");
    }

    @Override
    public String getChannel() {
        return this.channel;
    }

    @Override
    public @Nullable String getVersion() {
        return this.version;
    }
}
