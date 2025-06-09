/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.model.ObjectFactory
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional

import javax.inject.Inject
import java.nio.file.Files

import static net.minecraftforge.gradle.ForgeGradlePlugin.LOGGER

@CompileStatic
@PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.CONSTRUCTORS])
abstract class ForgeGradleFlowAction<P extends Parameters> implements FlowAction<P> {
    @PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.CONSTRUCTORS])
    static abstract class Parameters implements FlowParameters {
        protected final ForgeGradleProblems problems

        protected final @Optional @Input Property<Throwable> failure

        Parameters(Problems problems, ObjectFactory objects, ProviderFactory providers) {
            this.problems = new ForgeGradleProblems(problems, providers)

            this.failure = objects.property(Throwable)
        }
    }

    @PackageScope static boolean contains(Throwable e, String s) {
        for (var cause = e; cause = cause.cause; cause !== null) {
            if (cause.message.containsIgnoreCase(s)) return true
        }

        return false
    }

    @Override
    final void execute(P parameters) throws Exception {
        try {
            this.run(parameters)
        } catch (Throwable e) {
            // wrapping in this exception will prevent gradle from eating the stacktrace
            throw new RuntimeException(e)
        }
    }

    abstract void run(P parameters) throws Throwable;

    @CompileStatic
    @PackageScope static abstract class WelcomeMessage extends ForgeGradleFlowAction<Parameters> {
        @CompileStatic
        static enum DisplayOption {
            ONCE, NEVER, ALWAYS
        }

        @CompileStatic
        @PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.FIELDS])
        static abstract class Parameters extends ForgeGradleFlowAction.Parameters {
            final DirectoryProperty messagesDir
            final Property<DisplayOption> displayOption

            @Inject
            Parameters(Problems problems, ObjectFactory objects, ProviderFactory providers) {
                super(problems, objects, providers)

                this.messagesDir = objects.directoryProperty()
                this.displayOption = objects.property(DisplayOption).convention(DisplayOption.ONCE)
            }
        }

        @Override
        void run(Parameters parameters) {
            // if build failed, don't bother
            if (parameters.failure.present) return

            // check for marker file
            final markerFile = parameters.messagesDir.file('7_0_WELCOME').get().asFile
            if (markerFile.exists()) return
            Files.createDirectories(markerFile.toPath().parent)
            Files.createFile(markerFile.toPath())

            LOGGER.lifecycle(Constants.Messages.WELCOME, markerFile.absolutePath)
        }
    }
}
