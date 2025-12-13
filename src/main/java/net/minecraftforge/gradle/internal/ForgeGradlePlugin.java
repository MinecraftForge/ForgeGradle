/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.EnhancedPlugin;
import org.gradle.api.Project;
import org.gradle.api.flow.FlowProviders;
import org.gradle.api.flow.FlowScope;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.plugins.ExtensionAware;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.function.Consumer;

abstract class ForgeGradlePlugin extends EnhancedPlugin<ExtensionAware> {
    static final String NAME = "forgegradle";
    static final String DISPLAY_NAME = "ForgeGradle";

    static final Logger LOGGER = Logging.getLogger(ForgeGradlePlugin.class);

    private final ForgeGradleProblems problems = this.getObjects().newInstance(ForgeGradleProblems.class);

    static {
        LOGGER.lifecycle("ForgeGradle 7 is an incubating plugin.");
    }

    protected abstract @Inject FlowScope getFlowScope();

    protected abstract @Inject FlowProviders getFlowProviders();

    @Inject
    public ForgeGradlePlugin() {
        super(NAME, DISPLAY_NAME, "fgtools");
    }

    @Override
    public void setup(ExtensionAware target) {
        if (target instanceof Project project) {
            this.getFlowScope().always(ForgeGradleFlowAction.MessageBoard.class, spec -> {
                spec.parameters(parameters -> {
                    this.messageBoard = message -> parameters.queue(project, globalCaches(), message);
                    parameters.getFailure().set(this.getFlowProviders().getBuildWorkResult().map(p -> p.getFailure().orElse(null)));
                });
            });

            if (!problems.testFalse("net.minecraftforge.gradle.magic")) {
                LOGGER.info("Applying ForgeGradle Magic to {}", project);
                project.getPluginManager().apply(ForgeGradleMagicPlugin.class);
            }
        }

        ForgeGradleExtensionImpl.register(this, target);
        MinecraftExtensionImpl.register(this, target);
    }

    private @Nullable Consumer<ForgeGradleMessage> messageBoard;

    void queueMessage(ForgeGradleMessage message) {
        if (messageBoard != null)
            messageBoard.accept(message);
    }
}
