/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

abstract class MinecraftMappingsImpl implements MinecraftMappingsInternal {
    private final String channel;
    private final String version;

    protected abstract @Inject ObjectFactory getObjects();

    @Inject
    public MinecraftMappingsImpl(String channel, String version) {
        var problems = this.getObjects().newInstance(ForgeGradleProblems.class);
        this.channel = Util.checkMappingsParam(problems, channel, "channel");
        this.version = Util.checkMappingsParam(problems, version, "version");
    }

    @Override
    public String getChannel() {
        return this.channel;
    }

    @Override
    public String getVersion() {
        return this.version;
    }
}
