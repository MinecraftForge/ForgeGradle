/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.transform.PackageScopeTarget
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.ProjectLayout
import org.gradle.api.flow.FlowProviders
import org.gradle.api.flow.FlowScope
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.PluginAware
import org.gradle.api.problems.Problems
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.annotations.Nullable

import javax.inject.Inject

/**
 * The entry point for the ForgeGradle plugin.
 * <p>This class is kept intentionally dynamic to allow it to be applied to both {@link Project} and
 * {@link org.gradle.api.initialization.Settings}.</p>
 */
@CompileStatic
@PackageScope([PackageScopeTarget.CLASS, PackageScopeTarget.FIELDS])
class ForgeGradlePlugin implements Plugin<ExtensionAware> {
    /** The global logger for ForgeGradle, mostly used within {@link ForgeGradleProblems}. */
    static final Logger LOGGER = Logging.getLogger("ForgeGradle")

    final ForgeGradleProblems enhancedProblems

    /**
     * The default constructor for the ForgeGradle plugin, which is invoked by Gradle to create this plugin.
     * <p>It has been marked to be injected by Gradle, as that allows the class itself to stay package-private,
     * keeping it hidden from public API and further preventing unwanted usage from consumers.</p>
     */
    @Inject
    ForgeGradlePlugin() {
        this.enhancedProblems = new ForgeGradleProblems(this.&getProblems, this.&getProviders)
    }

    private @Nullable DirectoryProperty globalCaches

    /**
     * Applies the ForgeGradle plugin to the target.
     * <p>The target will have the {@linkplain ForgeGradleExtension ForgeGradle} and
     * {@linkplain MinecraftExtension minecraft} extensions added to it. Additional functionality may be added depending
     * on the target type (i.e. project).</p>
     *
     * @param target The target to apply the plugin to
     */
    @Override
    void apply(ExtensionAware target) {
        this.globalCaches = this.objects.directoryProperty().convention(
            this.objects.directoryProperty().fileValue(this.getGradleUserHomeDir(target)).dir(Constants.CACHES_LOCATION).map(this.enhancedProblems.ensureDirectory())
        )

        ForgeGradleExtensionImpl.register(
            target
        )

        MinecraftExtensionImpl.register(
            target,
            this,
            this.&getFlowScope,
            this.&getFlowProviders,
            this.&getObjects,
            this.&getProjectLayout,
            this.&getProviders,
            this.&getFileSystemOperations,
            this.&getArchiveOperations
        )
    }

    @PackageScope DirectoryProperty getGlobalCaches() {
        try {
            Objects.requireNonNull(this.globalCaches)
        } catch (Throwable e) {
            throw this.enhancedProblems.pluginNotYetApplied(new IllegalStateException("ForgeGradle does not have global caches", e))
        }
    }

    @SuppressWarnings('GrDeprecatedAPIUsage') // Intentional deprecation, please use this method
    @PackageScope Provider<File> getTool(Tools tool) {
        tool.get(this.globalCaches, this.providers)
    }

    @CompileDynamic
    private File getGradleUserHomeDir(ExtensionAware target) {
        try {
            target.gradle.startParameter.gradleUserHomeDir
        } catch (Throwable e) {
            throw this.enhancedProblems.illegalPluginTarget(new IllegalArgumentException("Cannot apply ForgeGradle to target: " + target, e))
        }
    }

    protected @Inject Problems getProblems() throws Exception {
        this.injectFailed()
    }

    protected @Inject FlowScope getFlowScope() throws Exception {
        this.injectFailed()
    }

    protected @Inject FlowProviders getFlowProviders() throws Exception {
        this.injectFailed()
    }

    protected @Inject ObjectFactory getObjects() throws Exception {
        this.injectFailed()
    }

    protected @Inject ProjectLayout getProjectLayout() throws Exception {
        this.injectFailed()
    }

    protected @Inject ProviderFactory getProviders() throws Exception {
        this.injectFailed()
    }

    protected @Inject FileSystemOperations getFileSystemOperations() throws Exception {
        this.injectFailed()
    }

    protected @Inject ArchiveOperations getArchiveOperations() throws Exception {
        this.injectFailed()
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    private <S> S injectFailed() {
        throw new Exception("Cannot use in current context (this is a ForgeGradle bug, please report it!)")
    }
}
