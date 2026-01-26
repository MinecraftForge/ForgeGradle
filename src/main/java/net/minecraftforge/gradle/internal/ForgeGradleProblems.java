/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftMappings;
import net.minecraftforge.gradleutils.shared.EnhancedProblems;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;

import static net.minecraftforge.gradle.internal.ForgeGradlePlugin.LOGGER;

/**
 * This concrete extension of Gradle's {@linkplain Problems} API is used to enhance the reporting of problems throughout
 * ForgeGradle. This includes problems that are specific to ForgeGradle and the ability to suppress warnings using
 * {@linkplain ProviderFactory#gradleProperty(String) Gradle properties}.
 *
 * @see Problems
 */
abstract class ForgeGradleProblems extends EnhancedProblems {
    @Inject
    public ForgeGradleProblems() {
        super(ForgeGradlePlugin.NAME, ForgeGradlePlugin.DISPLAY_NAME);
    }

    //region Minecraft
    //region Mappings
    RuntimeException missingMappings(Throwable throwable) {
        return this.throwing(throwable, "missing-mappings", "Missing Minecraft mappings", spec -> spec
            .details("""
                Attempted to consume Minecraft mappings, but none were declared.
                Minecraft dependencies cannot be resolved without mappings.""")
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Declare mappings in your buildscript in the minecraft {} closure, such as `mappings channel: 'official', version: '1.21.5'`.")
            .solution("Move the minecraft {} closure above the dependencies {} closure in your buildscript.")
            .solution("Consult the documentation or ask for help on the Forge Forums, GitHub, or Discord server.")
        );
    }

    RuntimeException nullMappingsParam(String name) {
        return this.throwing(new IllegalArgumentException("Mappings %s cannot be null".formatted(name)), "null-mappings-param", "Null mappings parameter", spec -> spec
            .details("""
                Attempted to create a Mappings object, but the %s parameter was null.
                The parameters for the Mappings object are not null.""".formatted(name))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Do not pass in any null values to the mappings constructor or MinecraftExtension#mappings.")
            .solution(HELP_MESSAGE)
        );
    }

    void reportOverriddenMappings(MinecraftMappings original, MinecraftMappings replacement) {
        if (!this.test("net.minecraftforge.gradle.warnings.minecraft.mappings.overridden")) return;

        var comparison = "Old: (channel: %s, version: %s), New: (channel: %s, version: %s)"
            .formatted(original.getChannel(), original.getVersion(), replacement.getChannel(), replacement.getVersion());
        LOGGER.warn("WARNING: Overriding previously declared mappings! {}", comparison);
        this.report("multiple-mappings", "Multiple mappings declared", spec -> spec
            .details("""
                Mappings are being set, even though they have already been declared.
                This will cause the current mappings to be overridden, which may lead to unexpected behavior.
                """ + comparison)
            .severity(Severity.WARNING)
            .stackLocation()
            .solution("Declare mappings only once in your buildscript.")
            .solution("Do not attempt to re-declare mappings in a loop or closure. Instead, evaluate the channel and version you want, then declare mappings using them.")
        );
    }
    //endregion

    //region Dependencies
    void reportMissingMinecraftDependency() {
        if (!this.test("net.minecraftforge.gradle.warnings.minecraft.dependency.missing")) return;

        LOGGER.error("ERROR: No Minecraft dependency declared! Disabling ForgeGradle. See Problems report for details.");
        this.report("missing-dependency", "Missing Minecraft dependency", spec -> spec
            .details("""
                ForgeGradle was applied, but no Minecraft dependency was declared.
                ForgeGradle will now be disabled and stop all further registrations.""")
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Declare a Minecraft dependency in your build.gradle file, such as `implementation minecraft.dep('net.minecraftforge:forge:1.21.5-55.0.3')`")
            .solution("Ensure that your Minecraft dependency is declared using the `minecraft.dep(...)` method.")
            .solution("Ensure that your buildscript with your dependencies is being loaded correctly.")
            .solution("Do not apply ForgeGradle if you are not planning on developing for Minecraft.")
            .solution(HELP_MESSAGE)
        );
    }

    RuntimeException invalidMinecraftDependencyType(Dependency dependency) {
        return this.throwing(new IllegalArgumentException("Minecraft dependency is not a module dependency"), "unsupported-minecraft-dependency-type", "Non-module dependency used as Minecraft dependency", spec -> spec
            .details("""
                Attempted to use a non-module (or internal module) dependency as a Minecraft dependency.
                The Minecraft dependency must be an external module dependency, as it is resolved from the Mavenizer output.
                This means that it cannot be substituted with file or project dependencies.
                Expected: (implementation of) %s, Actual: '%s
                Dependency: '%s'"""
                .formatted(ExternalModuleDependency.class.getName(), dependency.getClass().getName(), Util.toString(dependency)))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Declare a module dependency instead.")
            .solution(HELP_MESSAGE)
        );
    }

    RuntimeException changingMinecraftDependency(Dependency dependency) {
        return this.throwing(new IllegalArgumentException("Minecraft dependency cannot be changing"), "changing-minecraft-dependency", "Minecraft dependency marked as changing", spec -> spec
            .details("""
                Attempted to use a Minecraft dependency that was marked as changing.
                This is currently unsupported.
                Dependency: %s"""
                .formatted(Util.toString(dependency)))
            .severity(Severity.ERROR)
            .solution("Do not mark the Minecraft dependency as changing.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion

    //region Minecraft Maven
    void mavenizerOutOfDate(boolean throwIt, Object dependency) {
        String name = "mavenizer-out-of-date";
        String displayName = "Minecraft Mavenizer is out-of-date";
        Action<? super ProblemSpec> problemSpec = spec -> spec
            .details("""
                Gradle cannot compile your sources or run the game because the Minecraft Mavenizer is out-of-date.
                The Mavenizer must be re-run in order for the changes made to the Minecraft dependency to take effect.
                Affected dependency: '%s'"""
                .formatted(dependency))
            .severity(Severity.ERROR)
            .solution("Re-import your project in your IDE, as this will automatically synchronize the Mavenizer.")
            .solution("Run `gradlew` with no arguments, as this will automatically synchronize the Mavenizer.")
            .solution("Temporary revert any edits to the Minecraft dependency until the Mavenizer is re-run.")
            .solution(HELP_MESSAGE);

        if (throwIt)
            throw this.throwing(new IllegalStateException(displayName), name, displayName, problemSpec);
        else
            this.report(name, displayName, problemSpec);
    }

    void reportCannotAccessSettingsRepos(Exception e) {
        this.report("cannot-access-settings-repos", "Cannot access Settings repositories", spec -> spec
            .details("""
                ForgeGradle is unable to access the repositories defined by the Settings' dependency resolution management.
                This is a ForgeGradle bug and needs to be reported to the ForgeGradle issue tracker on GitHub.""")
            .severity(Severity.ERROR)
            .withException(e)
            .solution("For now, only declare repositories in the project.")
            .solution(HELP_MESSAGE));
    }

    void reportMcMavenNotDeclared() {
        if (!this.test("net.minecraftforge.gradle.warnings.repository.missing.mavenizer")) return;

        this.report("minecraft-maven-not-declared", "Minecraft Maven not declared", spec -> spec
            .details("""
                ForgeGradle was configured to sync the Minecraft Maven, but it was not declared as a repository!
                This will result in a "cannot resolve dependency" error.""")
            .severity(Severity.ERROR)
            .solution("Declare the Miencraft Maven (`minecraft.maven`) in your project/settings repositories.")
            .solution(HELP_MESSAGE)
        );
    }

    void reportMcLibsMavenNotDeclared() {
        if (!this.test("net.minecraftforge.gradle.warnings.repository.missing.mojang")) return;

        this.report("minecraft-libs-maven-not-declared", "Minecraft Libraries maven not declared", spec -> spec
            .details("""
                ForgeGradle was configured to sync the Minecraft Maven, but the Minecraft Libraries maven was not declared!
                The generated Minecraft artifact has dependencies from libraries that may only exist on there.
                This may result in a "cannot resolve dependency" error.""")
            .severity(Severity.WARNING)
            .solution("Declare the Minecraft Libs maven (`fg.minecraftLibsMaven`) in your project/settings repositories.")
            .solution(HELP_MESSAGE)
        );
    }

    void reportForgeMavenNotDeclared() {
        if (!this.test("net.minecraftforge.gradle.warnings.repository.missing.forge")) return;

        this.report("forge-maven-not-declared", "Forge maven not declared", spec -> spec
            .details("""
                ForgeGradle was configured to sync the Minecraft Maven, but the Forge maven was not declared!
                The generated Minecraft artifact has dependencies from libraries that may only exist on there.
                This may result in a "cannot resolve dependency" error.""")
            .severity(Severity.WARNING)
            .solution("Declare the Forge maven (`fg.forgeMaven`) in your project/settings repositories.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion
    //endregion

    //region Eclipse
    void reportUnmergedSourceSets() {
        report("source-sets-not-merged-for-eclipse", "Source Set outputs are not merged", spec -> spec
            .details("""
                ForgeGradle was not configured to merge source set outputs, but this project is using Eclipse!
                Unmerged source sets may cause problems when using the Eclipse launch configurations generated by ForgeGradle.""")
            .severity(Severity.WARNING)
            .solution("Set the 'net.minecraftforge.gradle.merge-source-sets' Gradle property to true.")
            .solution("Do not use the 'eclipse' plugin if you (or no one in your team) is using Eclipse.")
            .solution(HELP_MESSAGE));
    }

    void reportMissingEclipsePlugin(String taskName) {
        report("eclipse-missing-for-run-generation", "Eclipse plugin is not loaded", spec -> spec
            .details("""
                The Eclipse plugin is not loaded into this build, yet it was tasked with creating Eclipse run configurations.
                This may cause incorrect values to be used for the Eclipse project's output directory and project name.
                It is highly recommended to use the 'eclipse' plugin with your project if you plan on regularly using Eclipse."""
            )
            .severity(Severity.WARNING)
            .solution("Use the 'eclipse' plugin in your build.")
            .solution("Run the '%s' task directly from Eclipse.".formatted(taskName))
            .solution(HELP_MESSAGE));
    }
    //endregion

    //region Access Transformers
    void reportAccessTransformersNotApplied(Throwable e) {
        this.report("access-transformers-not-applied", "AccessTransformers plugin not applied", spec -> spec
            .details("""
                The build failed with an exception when trying to access access transformers.
                The project using ForgeGradle does not have the AccessTransformers Gradle plugin applied, and thus it cannot be used.
                AccessTransformers Gradle must be applied in order to use it with the Minecraft dependency.""")
            .severity(Severity.ERROR)
            .withException(e)
            .stackLocation()
            .solution("Apply the 'net.minecraftforge.accesstransformers' plugin before ForgeGradle.")
            .solution(HELP_MESSAGE)
        );
    }

    RuntimeException accessTransformersNotOnClasspath(Throwable e) {
        return this.throwing(e, "access-transformers-not-on-classpath", "AccessTransformers plugin not on classpath", spec -> spec
            .details("""
                The AccessTransformers plugin was not loaded in the classpath before ForgeGradle.
                ForgeGradle cannot create the 'minecraft' extension without referencing classes from the plugin.
                It must be added to the classpath, even if it isn't applied (i.e. in settings.gradle)
                ```groovy
                plugins {
                    id 'net.minecraftforge.accesstransformers' version '2.0.0' apply false
                    id 'net.minecraftforge.gradle' version '7.0.0'
                }
                ```""")
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Declare the 'net.minecraftforge.accesstransformers' plugin before ForgeGradle.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion

    //region Message Board
    void reportMessageBoardCacheBroken(Throwable e, File file, String property) {
        this.report("message-board-cache-broken", "ForgeGradle's message board cannot save data", spec -> spec
            .details("""
                ForgeGradle's message board cannot save data to its cache directory.
                This will prevent ForgeGradle from remembering that a message has been displayed.""")
            .severity(Severity.ERROR)
            .withException(e)
            .solution("Ensure read/write access for the following file: " + file)
            .solution("Disable the message by setting the following property: " + property + "=never")
            .solution(HELP_MESSAGE));
    }
    //endregion
}
