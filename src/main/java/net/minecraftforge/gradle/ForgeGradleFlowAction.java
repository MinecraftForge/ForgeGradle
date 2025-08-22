/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import net.minecraftforge.gradleutils.shared.EnhancedFlowAction;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;

import static net.minecraftforge.gradle.ForgeGradlePlugin.LOGGER;

abstract class ForgeGradleFlowAction<P extends ForgeGradleFlowAction.Parameters> extends EnhancedFlowAction<P> {
    static abstract class Parameters extends EnhancedFlowAction.EnhancedFlowParameters<ForgeGradleProblems> {
        protected Parameters() {
            super(ForgeGradleProblems.class);
        }
    }

    static abstract class WelcomeMessage extends ForgeGradleFlowAction<WelcomeMessage.Parameters> {
        enum DisplayOption {
            ONCE, NEVER, ALWAYS
        }

        static abstract class Parameters extends ForgeGradleFlowAction.Parameters {
            final DirectoryProperty messagesDir;
            final Property<DisplayOption> displayOption;

            protected abstract @Inject ProviderFactory getProviders();

            @Inject
            public Parameters() {
                this.messagesDir = this.getObjects().directoryProperty();
                this.displayOption = this.getObjects().property(DisplayOption.class).convention(DisplayOption.ONCE).value(
                    this.getProviders().gradleProperty("net.minecraftforge.gradle.messages.welcome")
                        .orElse(this.getProviders().systemProperty("net.minecraftforge.gradle.messages.welcome"))
                        .map(it -> DisplayOption.valueOf(it.toUpperCase(Locale.ROOT)))
                );
            }
        }

        @Inject
        public WelcomeMessage() { }

        @Override
        protected void run(Parameters parameters) throws IOException {
            // if build failed, don't bother
            if (parameters.getFailure().isPresent()) return;

            // check for marker file
            var markerFile = parameters.messagesDir.file("7_0_BETA_WELCOME").get().getAsFile();
            if (markerFile.exists()) return;
            Files.createDirectories(markerFile.toPath().getParent());
            Files.createFile(markerFile.toPath());

            LOGGER.lifecycle(Constants.Messages.WELCOME, markerFile.getAbsolutePath());
        }
    }

    static abstract class AccessTransformersMissing extends ForgeGradleFlowAction<AccessTransformersMissing.Parameters> {
        static abstract class Parameters extends ForgeGradleFlowAction.Parameters {
            final Property<Boolean> appliedPlugin;

            @Inject
            public Parameters() {
                this.appliedPlugin = this.getObjects().property(Boolean.class).convention(false);
            }
        }

        @Inject
        public AccessTransformersMissing() { }

        @Override
        protected void run(Parameters parameters) {
            if (!parameters.getFailure().isPresent() || parameters.appliedPlugin.getOrElse(false)) return;

            var e = parameters.getFailure().get();
            if (!contains(e, "accesstransformer")) return;

            parameters.problems().reportAccessTransformersNotApplied(e);
        }
    }
}
