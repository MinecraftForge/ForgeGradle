/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.util.Locale;

enum ForgeGradleMessage {
    WELCOME("7_0_RC_WELCOME_1", Constants.Messages.WELCOME, Constants.Messages.WELCOME_CONDITION),
    MAGIC("7_0_RC_MAGIC_1", Constants.Messages.MAGIC);

    private static final String MESSAGES_DIR = "messages";

    private final String marker;
    private final String property;
    private final String text;
    private final String condition;

    ForgeGradleMessage(String marker, String text) {
        this(marker, text, """
            This message will not display again unless the below file is deleted:
            {}""");
    }

    ForgeGradleMessage(String marker, String text, String condition) {
        var name = this.name().toLowerCase(Locale.ENGLISH);
        this.marker = marker;
        this.property = "net.minecraftforge.gradle.messages." + name;
        this.text = text;
        this.condition = condition;
    }

    String getProperty() {
        return this.property;
    }

    String getText() {
        return this.text;
    }

    String getCondition() {
        return this.condition;
    }

    QueuedMessage queue(Project project, DirectoryProperty globalCaches) {
        return new QueuedMessage(project, globalCaches, this);
    }

    enum DisplayOption {
        ONCE, NEVER, ALWAYS
    }

    static Provider<Directory> getMessagesDirectory(DirectoryProperty globalCaches) {
        return globalCaches.dir(MESSAGES_DIR);
    }

    private Provider<RegularFile> getMarkerFile(DirectoryProperty globalCaches) {
        return getMessagesDirectory(globalCaches).map(d -> d.file(this.marker));
    }

    private DisplayOption getDisplayOption(Project project) {
        return project.getProviders().gradleProperty(this.property)
                      .orElse(project.getProviders().systemProperty(this.property))
                      .map(it -> DisplayOption.valueOf(it.toUpperCase(Locale.ROOT)))
                      .getOrElse(DisplayOption.ONCE);
    }

    // NOTE: Has to be public so Gradle can reconstruct it for configuration cache.
    //       Can't be accessed though since ForgeGradleMessage is package-private.
    public record QueuedMessage(
        ForgeGradleMessage message, Provider<RegularFile> markerFile, DisplayOption displayOption
    ) implements Comparable<QueuedMessage> {
        @SuppressWarnings("ClassEscapesDefinedScope")
        public QueuedMessage(Project project, DirectoryProperty globalCaches, ForgeGradleMessage message) {
            this(message, message.getMarkerFile(globalCaches), message.getDisplayOption(project));
        }

        @Override
        public int compareTo(ForgeGradleMessage.QueuedMessage that) {
            return this.message.compareTo(that.message);
        }
    }
}
