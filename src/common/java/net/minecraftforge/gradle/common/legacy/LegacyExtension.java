/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.legacy;

import groovy.lang.GroovyObjectSupport;
import net.minecraftforge.gradle.common.tasks.ExtractZip;
import net.minecraftforge.gradle.common.util.MinecraftExtension;
import net.minecraftforge.srgutils.MinecraftVersion;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import java.io.File;


/**
 * Provides an extension block named "legacy".
 *
 * <code>
 * legacy {
 *     flag = set;
 * }</code>
 *
 * It allows for configuring RetroGradle patches and fixes, which are otherwise set by version.
 * Each fix is documented in this class and in the application function.
 * {@link LegacyExtension#runRetrogradleFixes}
 *
 * @author Curle
 */
public abstract class LegacyExtension extends GroovyObjectSupport {
    private static final Logger LOGGER = LogManager.getLogger(LegacyExtension.class);
    public static final String EXTENSION_NAME = "legacy";
    private static final MinecraftVersion FG3 = MinecraftVersion.from("1.13");

    /**
     * The RetroGradle project aims to port older (below 1.13) versions of Minecraft to the current toolchains.
     * In the process, some quirks with the older versions of ForgeGradle were discovered that need to be replicated here.
     *
     * Each quirk is documented and accounted for in this function.
     *  - Classpath / Resources;
     *      FG2 Userdev puts all classes and resources into a single jar file for FML to consume.
     *      FG3+ puts classes and resources into separate folders, which breaks on older versions.
     *      We replicate the FG2 behavior by replacing these folders by the jar artifact on the runtime classpath.
     *  - Dependency AccessTransformers / Coremods;
     *      FG2 GradleStart exposes a <code>net.minecraftforge.gradle.GradleStart.csvDir</code> property
     *      that points to a directory containing CSV mappings files. This is used by LegacyDev to remap dependencies' AT modifiers.
     *      We replicate the FG2 behavior by extracting the mappings to a folder in the build directory
     *      and setting the property to point to it.
     *
     * This is called from Utils.
     *
     * In other words, it's a containment zone for version-specific hacks.
     * For issues you think are caused by this function, contact Curle or any other Retrogradle maintainer.
     */
    public static void runRetrogradleFixes(Project project) {
        final LegacyExtension config = (LegacyExtension) project.getExtensions().getByName(LegacyExtension.EXTENSION_NAME);
        // Get the Userdev extension for the run configs
        final MinecraftExtension minecraft = project.getExtensions().getByType(MinecraftExtension.class);
        final boolean shouldFixClasspath = config.getFixClasspath().get();
        final boolean shouldExtractMappings = config.getExtractMappings().get();

        if(shouldFixClasspath) {
            LOGGER.info("LegacyExtension: Fixing classpath");
            // create a singleton collection from the jar task's output
            final FileCollection jar = project.files(project.getTasks().named("jar"));

            minecraft.getRuns().stream()
                    // get all RunConfig SourceSets
                    .flatMap(runConfig -> runConfig.getAllSources().stream())
                    .distinct()
                    // replace output directories with the jar artifact on each SourceSet's classpath
                    .forEach(sourceSet -> sourceSet.setRuntimeClasspath(sourceSet.getRuntimeClasspath().minus(sourceSet.getOutput()).plus(jar)));
        }

        if (shouldExtractMappings) {
            LOGGER.info("LegacyExtension: Extracting Mappings");
            // Extracts CSV mapping files to a folder in the build directory for further use
            // by the <code>net.minecraftforge.gradle.GradleStart.csvDir</code> property.
            final ExtractZip extractMappingsTask = project.getTasks().create("extractMappings", ExtractZip.class, t -> {
                t.getZip().fileProvider(project.provider(() -> {
                    // create maven dependency for mappings
                    String MAPPING_DEP = "net.minecraft:mappings_{CHANNEL}:{VERSION}@zip";
                    String coordinates = MAPPING_DEP.replace("{CHANNEL}", minecraft.getMappingChannel().get()).replace("{VERSION}", minecraft.getMappingVersion().get());

                    Dependency dep = project.getDependencies().create(coordinates);
                    // download and cache the mappings
                    return project.getConfigurations().detachedConfiguration(dep).getSingleFile();
                }));
                // mappings are extracted to <root>/build/<taskName> by default
                t.getOutput().convention(project.getLayout().getBuildDirectory().dir(t.getName()));
            });
            final File csvDir = extractMappingsTask.getOutput().get().getAsFile();

            // attach the csvDir location property to each run configuration
            minecraft.getRuns().configureEach(run -> run.property("net.minecraftforge.gradle.GradleStart.csvDir", csvDir));

            // execute extractMappings before each run task
            project.getTasks().getByName("prepareRuns").dependsOn(extractMappingsTask);
        }
    }

    public LegacyExtension(Project project) {
        Provider<Boolean> isLegacy = project.provider(() -> {
            final MinecraftVersion version = MinecraftVersion.from((String) (project.getParent() != null ? project.getParent() : project).getExtensions().getExtraProperties().get("MC_VERSION"));
            
            // enable patches by default if version is below FG 3
            return version.compareTo(FG3) < 0;
        });
        
        getFixClasspath().convention(isLegacy);
        getExtractMappings().convention(isLegacy);
    }

    /**
     * fixClassPath;
     *  FG2 Userdev puts all classes and resources into a single jar file for FML to consume.
     *  FG3+ puts classes and resources into separate folders, which breaks on older versions.
     *  We replicate the FG2 behavior by replacing these folders by the jar artifact on the runtime classpath.
     *
     * Takes a boolean - true for apply fix, false for no fix.
     */
    public abstract Property<Boolean> getFixClasspath();

    /**
     * extractMappings;
     * FG2 GradleStart exposes a <code>net.minecraftforge.gradle.GradleStart.csvDir</code> property
     * that points to a directory containing CSV mappings files. This is used by LegacyDev to remap dependencies' AT modifiers.
     * We replicate the FG2 behavior by extracting the mappings to a folder in the build directory
     * and setting the property to point to it.
     * <p>
     * Takes a boolean - true for apply fix, false for no fix.
     */
    public abstract Property<Boolean> getExtractMappings();
}
