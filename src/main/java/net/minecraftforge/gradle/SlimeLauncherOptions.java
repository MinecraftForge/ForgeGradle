/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.SourceSet;

public interface SlimeLauncherOptions extends SlimeLauncherOptionsNested, Named {
    @Override
    @Input String getName();

    default void with(SourceSet sourceSet, Action<? super SlimeLauncherOptionsNested> action) {
        this.with(sourceSet.getName(), action);
    }

    void with(String sourceSetName, Action<? super SlimeLauncherOptionsNested> action);

    /// The classpath to use.
    ///
    /// The classpath in question **must include** Slime Launcher in it. By default, Slime Launcher is added on top of
    /// the [project's][org.gradle.api.Project]
    /// [runtimeClasspath][org.gradle.api.plugins.JavaPlugin#RUNTIME_CLASSPATH_CONFIGURATION_NAME] configuration for
    /// this task.
    ///
    /// Keep in mind that if the [org.gradle.api.tasks.JavaExec] task is configured to have a different
    /// {@linkplain org.gradle.api.tasks.JavaExec#getMainClass() main class}, the classpath does not need to include
    /// Slime Launcher.
    ///
    /// @return The classpath to use
    @InputFiles @Classpath @Optional ConfigurableFileCollection getClasspath();

    /// The working directory to use.
    ///
    /// By default, this will be `run/`{@link SourceSet#getName() sourceSet}`/`{@link #getName() name}.
    ///
    /// To clarify: this is the working directory of the Java process. Slime Launcher uses absolute file locations to
    /// place its caches and metadata, which do not interfere with the working directory.
    ///
    /// @return A property for the working directory
    @Override
    @Internal DirectoryProperty getWorkingDir();

    /// Adds to the classpath to use.
    ///
    /// @param classpath The classpath to include with the existing classpath
    /// @apiNote Unlike [#setClasspath(Object...)], this method does not replace the existing classpath.
    /// @see #getClasspath()
    default void classpath(Object... classpath) {
        this.getClasspath().from(classpath);
    }

    /// Adds to the classpath to use.
    ///
    /// @param classpath The classpath to include with the existing classpath
    /// @apiNote Unlike [#setClasspath(Iterable)], this method does not replace the existing classpath.
    /// @see #getClasspath()
    default void classpath(Iterable<?> classpath) {
        this.getClasspath().from(classpath);
    }

    /// Adds to the classpath to use.
    ///
    /// @param classpath The classpath to include with the existing classpath
    /// @apiNote Unlike [#setClasspath(FileCollection)], this method does not replace the existing classpath.
    /// @see #getClasspath()
    default void classpath(FileCollection classpath) {
        this.getClasspath().from(classpath);
    }

    /// Sets the classpath to use.
    ///
    /// @param classpath The classpath
    /// @apiNote This method will replace the existing classpath. To add to it, use [#classpath(Object...)].
    /// @see #getJvmArgs()
    default void setClasspath(Object... classpath) {
        this.getClasspath().setFrom(classpath);
    }

    /// Sets the classpath to use.
    ///
    /// @param classpath The classpath
    /// @apiNote This method will replace the existing classpath. To add to it, use [#classpath(Iterable)].
    /// @see #getJvmArgs()
    default void setClasspath(Iterable<?> classpath) {
        this.getClasspath().setFrom(classpath);
    }

    /// Sets the classpath to use.
    ///
    /// @param classpath The classpath
    /// @apiNote This method will replace the existing classpath. To add to it, use [#classpath(FileCollection)].
    /// @see #getJvmArgs()
    default void setClasspath(FileCollection classpath) {
        this.getClasspath().setFrom(classpath);
    }
}
