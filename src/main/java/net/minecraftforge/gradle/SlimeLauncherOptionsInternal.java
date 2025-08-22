/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;

non-sealed interface SlimeLauncherOptionsInternal extends SlimeLauncherOptions, HasPublicType {
    @Override
    default TypeOf<?> getPublicType() {
        return TypeOf.typeOf(SlimeLauncherOptions.class);
    }

    default void apply(SlimeLauncherExec task) {
        if (this.getMainClass().filter(Util::isPresent).isPresent())
            task.getBootstrapMainClass().set(this.getMainClass());

        if (this.getArgs().filter(Util::isPresent).isPresent())
            task.getMcBootstrapArgs().set(this.getArgs().map(it -> it.stream().map(Object::toString).toList()));

        if (this.getJvmArgs().filter(Util::isPresent).isPresent())
            task.jvmArgs(this.getJvmArgs().get());

        if (!this.getClasspath().isEmpty())
            task.setClasspath(this.getClasspath());

        if (this.getMinHeapSize().filter(Util::isPresent).isPresent())
            task.setMinHeapSize(this.getMinHeapSize().get());

        if (this.getMaxHeapSize().filter(Util::isPresent).isPresent())
            task.setMinHeapSize(this.getMaxHeapSize().get());

        if (this.getSystemProperties().filter(Util::isPresent).isPresent())
            task.systemProperties(this.getSystemProperties().get());

        if (this.getEnvironment().filter(Util::isPresent).isPresent())
            task.environment(this.getEnvironment().get());

        if (this.getWorkingDir().isPresent())
            task.workingDir(this.getWorkingDir());
    }
}
