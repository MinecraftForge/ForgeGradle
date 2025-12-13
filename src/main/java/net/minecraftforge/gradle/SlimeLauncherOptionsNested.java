/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.Map;

public interface SlimeLauncherOptionsNested {
    /// The main class for Slime Launcher to use.
    ///
    /// This is the class that will be invoked by Slime Launcher, **not** the main class of the
    /// [org.gradle.api.tasks.JavaExec] task that will be produced from these options.
    ///
    /// @return A property for the main class
    @Input @Optional Property<String> getMainClass();

    /// Wither or not to inherit arguments from the UserDev provided run configs.
    ///
    /// If you set this to false you must specify all arguments to start the process manually.
    ///
    /// @return A property controlling inheritance of arguments from UserDev config file.
    @Input @Optional Property<Boolean> getInheritArgs();

    /// The arguments to pass to the main class.
    ///
    /// This is the arguments that will be passed to the main class through Slime Launcher, **not** the arguments for
    /// Slime Launcher itself.
    ///
    /// @return A property for the arguments to pass to the main class
    @Input @Optional ListProperty<String> getArgs();

    /// Wither or not to inherit JVM arguments from the UserDev provided run configs.
    ///
    /// If you set this to false you must specify all JVM arguments to start the process manually.
    ///
    /// @return A property controlling inheritance of JVM arguments from UserDev config file.
    @Input @Optional Property<Boolean> getInheritJvmArgs();

    /// The JVM arguments to use.
    ///
    /// These are applied immediately when Slime Launcher is executed. A reminder that Slime Launcher is not a
    /// re-launcher but a dev-environment bootstrapper.
    ///
    /// @return A property for the JVM arguments
    @Input @Optional ListProperty<String> getJvmArgs();

    /// The minimum memory heap size to use.
    ///
    /// Working with this property is preferred over manually using the `-Xms` argument in the [JVM
    /// arguments][#getJvmArgs()].
    ///
    /// @return A property for the minimum heap size
    @Input @Optional Property<String> getMinHeapSize();

    /// The maximum memory heap size to use.
    ///
    /// Working with this property is preferred over manually using the `-Xmx` argument in the [JVM
    /// arguments][#getJvmArgs()].
    ///
    /// @return A property for the maximum heap size
    @Input @Optional Property<String> getMaxHeapSize();

    /// The system properties to use.
    ///
    /// @return A property for the system properties
    @Input @Optional MapProperty<String, String> getSystemProperties();

    /// The environment variables to use.
    ///
    /// @return A property for the environment variables
    @Input @Optional MapProperty<String, String> getEnvironment();

    /// The working directory to use.
    ///
    /// To clarify: this is the working directory of the Java process. Slime Launcher uses absolute file locations to
    /// place its caches and metadata, which do not interfere with the working directory.
    ///
    /// @return A property for the working directory
    @Internal DirectoryProperty getWorkingDir();

    /// Adds to the arguments to pass to the [main class][#getMainClass()].
    ///
    /// @param args The arguments to add
    /// @apiNote To add multiple arguments, use [#args(Object...)]
    /// @see #getArgs()
    void args(Object args);

    /// Adds to the arguments to pass to the [main class][#getMainClass()].
    ///
    /// @param args The arguments to add
    /// @apiNote Unlike [#setArgs(String...)], this method does not replace the existing arguments.
    /// @see #getArgs()
    void args(Object... args);

    /// Adds to the arguments to pass to the [main class][#getMainClass()].
    ///
    /// @param args The arguments to add
    /// @apiNote Unlike [ListProperty#set(Iterable)], this method does not replace the existing arguments.
    /// @see #getArgs()
    void args(Iterable<?> args);

    /// Adds to the arguments to pass to the [main class][#getMainClass()].
    ///
    /// @param args The arguments to add
    /// @apiNote Unlike [ListProperty#set(Provider)], this method does not replace the existing arguments.
    /// @see #getArgs()
    void args(Provider<? extends Iterable<?>> args);

    /// Sets the arguments to pass to the [main class][#getMainClass()].
    ///
    /// @param args The arguments
    /// @see #getArgs()
    void setArgs(String... args);

    /// Adds to the JVM arguments to use.
    ///
    /// @param jvmArgs The JVM argument to add
    /// @apiNote To add multiple arguments, use [#jvmArgs(Object...)]
    /// @see #getJvmArgs()
    void jvmArgs(Object jvmArgs);

    /// Adds to the JVM arguments to use.
    ///
    /// @param jvmArgs The JVM arguments to add
    /// @apiNote Unlike [#setJvmArgs(Object...)], this method does not replace the existing arguments.
    /// @see #getJvmArgs()
    void jvmArgs(Object... jvmArgs);

    /// Adds to the JVM arguments to use.
    ///
    /// @param jvmArgs The JVM arguments to add
    /// @apiNote Unlike [ListProperty#set(Iterable)], this method does not replace the existing arguments.
    /// @see #getJvmArgs()
    void jvmArgs(Iterable<?> jvmArgs);

    /// Adds to the JVM arguments to use.
    ///
    /// @param jvmArgs The JVM arguments to add
    /// @apiNote Unlike [ListProperty#set(Provider)], this method does not replace the existing arguments.
    /// @see #getJvmArgs()
    void jvmArgs(Provider<? extends Iterable<?>> jvmArgs);

    /// Sets the JVM arguments to use.
    ///
    /// @param jvmArgs The arguments
    /// @see ListProperty#set(Iterable)
    void setJvmArgs(Object... jvmArgs);

    /// Adds a single system property to use.
    ///
    /// @param name  The name
    /// @param value The value
    /// @apiNote To add multiple system properties at once, use [#systemProperties(Provider)].
    /// @see #getSystemProperties()
    void systemProperty(String name, Object value);

    /// Adds to the system properties to use.
    ///
    /// @param properties The system properties
    /// @apiNote Unlike [MapProperty#set(Map)], this method does not replace the existing system properties. To add a
    /// single property, use [#systemProperty(String,Object)].
    /// @see #getSystemProperties()
    void systemProperties(Map<String, ?> properties);

    /// Adds to the system properties to use.
    ///
    /// @param properties The system properties
    /// @apiNote Unlike [MapProperty#set(Provider)], this method does not replace the existing system properties. To add
    /// a single property, use [#systemProperty(String,Object)].
    /// @see #getSystemProperties()
    void systemProperties(Provider<? extends Map<String, ?>> properties);

    /// Adds a single environment variable to use.
    ///
    /// @param name  The name
    /// @param value The value
    /// @apiNote To add multiple environment variables at once, use [#environment(Provider)].
    /// @see #getEnvironment()
    void environment(String name, Object value);

    /// Adds to the environment variables to use.
    ///
    /// @param properties The environment variables
    /// @apiNote Unlike [MapProperty#set(Map)], this method does not replace the existing environment variables. To add
    /// a single variable, use [#environment(String,Object)].
    /// @see #getEnvironment()
    void environment(Map<String, ?> properties);

    /// Adds to the environment variables to use.
    ///
    /// @param properties The environment variables
    /// @apiNote Unlike [MapProperty#set(Provider)], this method does not replace the existing environment variables. To
    /// add a single variable, use [#environment(String,Object)].
    /// @see #getEnvironment()
    void environment(Provider<? extends Map<String, ?>> properties);
}
