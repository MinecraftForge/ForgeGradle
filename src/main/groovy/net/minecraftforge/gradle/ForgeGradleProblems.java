/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.file.Directory;
import org.gradle.api.problems.Problem;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.ProblemSpec;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.ProviderFactory;
import org.jetbrains.annotations.UnknownNullability;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static net.minecraftforge.gradle.ForgeGradlePlugin.LOGGER;

/**
 * This concrete extension of Gradle's {@linkplain Problems} API is used to enhance the reporting of problems throughout
 * ForgeGradle. This includes problems that are specific to ForgeGradle and the ability to suppress warnings using
 * {@linkplain ProviderFactory#gradleProperty(String) Gradle properties}.
 *
 * @see Problems
 */
record ForgeGradleProblems(Problems problems, Predicate<String> properties) implements Problems {
    private static final ProblemGroup GROUP = ProblemGroup.create("forgegradle", "ForgeGradle");
    private static final String HELP_MESSAGE = "Consult the documentation or ask for help on the Forge Forums, GitHub, or Discord server.";

    @Override
    public ProblemReporter getReporter() {
        return this.problems.getReporter();
    }

    ForgeGradleProblems(Problems problems, ProviderFactory providers) {
        this(problems, property -> Util.isTrue(providers, property));
    }

    ForgeGradleProblems(Callable<? extends @UnknownNullability Problems> problems, Callable<? extends @UnknownNullability ProviderFactory> providers) {
        this(unwrapProblems(problems), unwrapProperties(providers));
    }

    private static Problems unwrapProblems(Callable<? extends @UnknownNullability Problems> supplier) {
        return Util.tryElse(supplier, EmptyReporter.AS_PROBLEMS);
    }

    private static Predicate<String> unwrapProperties(Callable<? extends @UnknownNullability ProviderFactory> supplier) {
        return Util.tryElse(
            () -> {
                var providers = Objects.requireNonNull(supplier.call());
                return property -> Util.isTrue(providers, property);
            },
            Boolean::getBoolean
        );
    }

    private static ProblemId id(String name, String displayName) {
        return ProblemId.create(name, displayName, GROUP);
    }

    //region Plugin
    RuntimeException illegalPluginTarget(Exception e) {
        return this.getReporter().throwing(e, id("invalid-plugin-target", "Invalid plugin target"), spec -> spec
            .details("""
                Attempted to apply the ForgeGradle plugin to an invalid target.
                ForgeGradle can only be applied on the project, the settings, or Gradle.""")
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Use a valid plugin target (Project, Settings, Gradle)")
            .solution(HELP_MESSAGE));
    }

    RuntimeException pluginNotYetApplied(Exception e) {
        return this.getReporter().throwing(e, id("plugin-not-yet-applied", "ForgeGradle is not applied"), spec -> spec
            .details("""
                Attempted to get details from the ForgeGradle plugin, but it has not yet been applied to the target.""")
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Apply the ForgeGradle plugin before attempting to use it from the target's plugin manager.")
            .solution("Apply the ForgeGradle plugin before attempting to register any of its tasks that require in-house caching or tools.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion

    //region Minecraft
    //region Mappings
    RuntimeException missingMappings(Throwable throwable) {
        return this.getReporter().throwing(throwable, ProblemId.create("missing-mappings", "Missing Minecraft mappings", ForgeGradleProblems.GROUP), spec -> spec
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
        return this.getReporter().throwing(new IllegalArgumentException("Mappings %s cannot be null".formatted(name)), id("null-mappings-param", "Null mappings parameter"), spec -> spec
            .details("""
                Attempted to create a Mappings object, but the %s parameter was null.
                The parameters for the Mappings object are not null.""".formatted(name))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Do not pass in any null values to the mappings constructor or MinecraftExtension#mappings.")
            .solution(HELP_MESSAGE)
        );
    }

    void reportOverriddenMappings(MinecraftExtension.Mappings original, MinecraftExtension.Mappings replacement) {
        if (!this.properties.test("net.minecraftforge.gradle.warnings.overriddenMappings")) return;

        var comparison = "Old: (channel: %s, version: %s), New: (channel: %s, version: %s)"
            .formatted(original.channel(), original.version(), replacement.channel(), replacement.version());
        LOGGER.warn("WARNING: Overriding previously declared mappings! {}", comparison);
        this.getReporter().report(id("multiple-mappings", "Multiple mappings declared"), spec -> spec
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
        if (!this.properties.test("net.minecraftforge.gradle.warnings.missingMinecraftDependency")) return;

        LOGGER.error("ERROR: No Minecraft dependency declared! Disabling ForgeGradle. See Problems report for details.");
        this.getReporter().report(id("missing-dependency", "Missing Minecraft dependency"), spec -> spec
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
        return this.getReporter().throwing(new IllegalArgumentException("Minecraft dependency is not a module dependency"), id("unsupported-minecraft-dependency-type", "Non-module dependency used as Minecraft dependency"), spec -> spec
            .details("""
                Attempted to use a non-module (or internal module) dependency as a Minecraft dependency.
                The Minecraft dependency must be an external module dependency, as it is resolved from the Minecraft Maven.
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

    @Deprecated(forRemoval = true)
    void reportMissingMetadata(Throwable throwable) {
        this.getReporter().report(id("missing-metadata", "Failed to extract metadata"), spec -> spec
            .details("""
                ForgeGradle failed to locate or extract the metadata generated for the Minecraft dependency.
                This is expected if the Minecraft Maven has not yet been synced.
                If you are seeing this after your first project sync, please report this as it might be a ForgeGradle bug.""")
            .severity(Severity.WARNING)
            .withException(throwable)
            .stackLocation()
            .solution("Re-run the synchronization for your Gradle project.")
            .solution("Manually run the " + SyncMinecraftMaven.NAME + " task if necessary.")
            .solution(HELP_MESSAGE)
        );
    }

    RuntimeException changingMinecraftDependency(Dependency dependency) {
        return this.getReporter().throwing(new IllegalArgumentException("Minecraft dependency cannot be changing"), id("changing-minecraft-dependency", "Minecraft dependency marked as changing"), spec -> spec
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
    void reportMcMavenNotDeclared() {
        if (!properties.test("net.minecraftforge.gradle.warnings.missingRepository.mcmaven")) return;

        this.getReporter().report(id("minecraft-maven-not-declared", "Minecraft Maven not declared"), spec -> spec
            .details("""
                ForgeGradle was configured to sync the Minecraft Maven, but it was not declared as a repository!
                This will result in a "cannot resolve dependency" error.""")
            .severity(Severity.ERROR)
            .solution("Declare the Miencraft Maven (`minecraft.maven`) in your project/settings repositories.")
            .solution(HELP_MESSAGE)
        );
    }

    void reportMcLibsMavenNotDeclared() {
        if (!properties.test("net.minecraftforge.gradle.warnings.missingRepository.mclibs")) return;

        this.getReporter().report(id("minecraft-libs-maven-not-declared", "Minecraft Libraries maven not declared"), spec -> spec
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
        if (!properties.test("net.minecraftforge.gradle.warnings.missingRepository.forge")) return;

        this.getReporter().report(id("forge-maven-not-declared", "Forge maven not declared"), spec -> spec
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

    //region Deobfuscation
    RuntimeException invalidDeobfDependencyType(Dependency dependency) {
        return this.getReporter().throwing(new IllegalArgumentException("Non-module deobf dependencies are not supported"), id("unsupported-dependency-type", "Non-module dependency used as Minecraft/deobf dependency"), spec -> spec
            .details("""
                Attempted to use a non-module dependency as a deobf dependency, which is currently unsupported.
                Support for file dependencies may come at a later time. Project dependencies will not be supported.
                Expected: (implementation of) %s, Actual: '%s
                Dependency: '%s'"""
                .formatted(ExternalModuleDependency.class.getName(), dependency.getClass().getName(), dependency.toString()))
            .severity(Severity.ERROR)
            .stackLocation()
            .solution("Declare a module dependency instead.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion
    //endregion

    //region Access Transformers
    RuntimeException accessTransformerFailed(RuntimeException e, File inJar, File atFile) {
        return this.getReporter().throwing(e, id("access-transformer-failed", "Access transformer failed"), spec -> spec
            .details("""
                The access transformer failed to apply the transformations.
                This could potentially be caused by an invalid access transformer configuration.
                Input Jar: %s
                AccessTransformer Config: %s"""
                .formatted(inJar, atFile))
            .severity(Severity.ERROR)
            .stackLocation()
            .fileLocation(atFile.getAbsolutePath())
            .solution("Check your access transformer configuration file and ensure it is valid.")
            .solution(HELP_MESSAGE)
        );
    }
    //endregion

    //region Utilities
    Transformer<Directory, Directory> ensureDirectory() {
        return dir -> {
            try {
                Files.createDirectories(dir.getAsFile().toPath());
                return dir;
            } catch (IOException e) {
                throw this.getReporter().throwing(e, id("cannot-ensure-directory", "Failed to create directory"), spec -> spec
                    .details("""
                        Failed to create a directory required for ForgeGradle to function.
                        Directory: %s"""
                        .formatted(dir.getAsFile().getAbsolutePath()))
                    .severity(Severity.ERROR)
                    .stackLocation()
                    .solution("Ensure that the you have write access to the directory that needs to be created.")
                    .solution(HELP_MESSAGE));
            }
        };
    }
    //endregion

    interface EmptyReporter extends ProblemReporter {
        EmptyReporter INSTANCE = new EmptyReporter() { };
        Problems AS_PROBLEMS = () -> INSTANCE;

        @Override
        default Problem create(ProblemId problemId, Action<? super ProblemSpec> action) {
            return new Problem() { };
        }

        @Override
        default void report(ProblemId problemId, Action<? super ProblemSpec> spec) { }

        @Override
        default void report(Problem problem) { }

        @Override
        default void report(Collection<? extends Problem> problems) { }

        @Override
        default RuntimeException throwing(Throwable exception, ProblemId problemId, Action<? super ProblemSpec> spec) {
            return this.toRTE(exception);
        }

        @Override
        default RuntimeException throwing(Throwable exception, Problem problem) {
            return this.toRTE(exception);
        }

        @Override
        default RuntimeException throwing(Throwable exception, Collection<? extends Problem> problems) {
            return this.toRTE(exception);
        }

        private RuntimeException toRTE(Throwable exception) {
            return exception instanceof RuntimeException rte ? rte : new RuntimeException(exception);
        }
    }
}
