/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.process.ExecOperations;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

abstract class MavenizerValueSource implements ValueSource<Boolean, MavenizerValueSource.Parameters> {
    interface Parameters extends ValueSourceParameters {
        ConfigurableFileCollection getClasspath();
        RegularFileProperty getJavaLauncher();
        ListProperty<String> getArguments();
    }

    private final ExecOperations execOps;
    private static final Logger LOGGER = Logging.getLogger(MavenizerValueSource.class);

    @Inject
    public MavenizerValueSource(ExecOperations execOps) {
        this.execOps = execOps;
    }

    @Override
    @Nullable
    public Boolean obtain() {
        this.execOps.javaexec(spec -> {
            var params = this.getParameters();
            spec.setClasspath(params.getClasspath());
            spec.setExecutable(params.getJavaLauncher().get());
            spec.setArgs(params.getArguments().get());

            LOGGER.info("Executing Mavenizer: ");
            var itr = params.getClasspath().iterator();
            LOGGER.info("  Classpath: {}", itr.next().getAbsolutePath());
            while (itr.hasNext())
                LOGGER.info("             {}", itr.next().getAbsolutePath());

            LOGGER.info("  Java: {}", params.getJavaLauncher().get().getAsFile().getAbsolutePath());
            var args = params.getArguments().get();
            var prefix = "  Arguments: ";
            for (int x = 0; x < args.size(); x++) {
                var current = args.get(x);
                var next = args.size() > x + 1 ? args.get(x + 1) : null;
                var line = current;
                if (current.startsWith("--") && next != null && !next.startsWith("--")) {
                    x++;
                    line += ' ' + next;
                }
                LOGGER.info("{}{}", prefix, line);
                prefix = "             ";
            }
        }).rethrowFailure().assertNormalExitValue();
        return false;
    }
}
