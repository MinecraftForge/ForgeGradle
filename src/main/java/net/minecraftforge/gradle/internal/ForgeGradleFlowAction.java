/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradleutils.shared.EnhancedFlowAction;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.ArrayList;

import static net.minecraftforge.gradle.internal.ForgeGradlePlugin.LOGGER;

abstract class ForgeGradleFlowAction<P extends ForgeGradleFlowAction.Parameters> extends EnhancedFlowAction<P> {
    static abstract class Parameters extends EnhancedFlowAction.EnhancedFlowParameters<ForgeGradleProblems> {
        protected Parameters() {
            super(ForgeGradleProblems.class);
        }
    }

    static abstract class MessageBoard extends ForgeGradleFlowAction<MessageBoard.Parameters> {
        static abstract class Parameters extends ForgeGradleFlowAction.Parameters {
            final SetProperty<ForgeGradleMessage.QueuedMessage> messages = this.getObjects().setProperty(ForgeGradleMessage.QueuedMessage.class);

            @Inject
            public Parameters() { }

            void queue(Project project, DirectoryProperty globalCaches, ForgeGradleMessage message) {
                this.messages.add(message.queue(project, globalCaches));
            }
        }

        @Inject
        public MessageBoard() { }

        @Override
        protected void run(Parameters parameters) {
            // if build failed, don't bother
            if (parameters.getFailure().isPresent()) return;

            var pending = parameters.messages.get();
            var messages = new ArrayList<ForgeGradleMessage.QueuedMessage>(pending.size());
            @Nullable File markerFile = null;
            for (var queued : pending) {
                switch (queued.displayOption()) {
                    case NEVER:
                        continue;

                    case ONCE:
                        // check for marker file
                        markerFile = queued.markerFile().get().getAsFile();
                        if (markerFile.exists()) continue;

                        try {
                            Files.createDirectories(markerFile.toPath().getParent());
                            Files.createFile(markerFile.toPath());
                        } catch (IOException e) {
                            parameters.problems().reportMessageBoardCacheBroken(e, markerFile, queued.message().getProperty());
                        }

                    case ALWAYS:
                        messages.add(queued);
                        break;
                }
            }

            if (messages.isEmpty())
                return;

            if (!LOGGER.isLifecycleEnabled()) {
                LOGGER.warn("WARNING: ForgeGradle messages cannot be displayed as the logger is not configured to display lifecycle messages.");
            }

            var count = messages.size();
            LOGGER.lifecycle("You have " + count + " new message" + (count == 1 ? "" : "s") + " from ForgeGradle.\n");
            for (var i = 0; i < count; i++) {
                var queued = messages.get(i);

                LOGGER.lifecycle("---   Message %d/%d   ---".formatted(i + 1, count));
                LOGGER.lifecycle(MessageFormat.format(queued.message().getText(), queued));

                if (markerFile != null && queued.displayOption() == ForgeGradleMessage.DisplayOption.ONCE)
                    LOGGER.lifecycle('\n' + queued.message().getCondition(), markerFile.getAbsolutePath());

                LOGGER.lifecycle("--- End of Message ---");
                if (i != count - 1)
                    LOGGER.lifecycle("");
            }
        }
    }

    static abstract class AccessTransformersMissing extends ForgeGradleFlowAction<AccessTransformersMissing.Parameters> {
        static abstract class Parameters extends ForgeGradleFlowAction.Parameters {
            final Property<Boolean> appliedPlugin = this.getObjects().property(Boolean.class).convention(false);

            @Inject
            public Parameters() { }
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
