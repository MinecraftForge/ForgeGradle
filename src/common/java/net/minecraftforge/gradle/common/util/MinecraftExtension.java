/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import java.util.Map;

import javax.inject.Inject;

public abstract class MinecraftExtension extends GroovyObjectSupport {

    protected final Project project;
    protected final NamedDomainObjectContainer<RunConfig> runs;
    protected final ConfigurableFileCollection accessTransformers;

    private final Provider<String> mapping;

    @Inject
    public MinecraftExtension(final Project project) {
        this.project = project;
        this.mapping = getMappingChannel().zip(getMappingVersion(), (ch, ver) -> ch + '_' + ver);
        this.runs = project.getObjects().domainObjectContainer(RunConfig.class, name -> new RunConfig(project, name));
        this.accessTransformers = project.getObjects().fileCollection();

        // If you update these conventions, make sure to update the property documentation as well
        getCopyIdeResources().convention(false);
        getEnableIdeaPrepareRuns().convention(false);
        getEnableEclipsePrepareRuns().convention(false);
        getGenerateRunFolders().convention(false);
    }

    public Project getProject() {
        return project;
    }

    public NamedDomainObjectContainer<RunConfig> runs(@SuppressWarnings("rawtypes") Closure closure) {
        return runs.configure(closure);
    }

    public NamedDomainObjectContainer<RunConfig> getRuns() {
        return runs;
    }

    public void propertyMissing(String name, Object value) {
        if (!(value instanceof Closure)) {
            throw new MissingPropertyException(name);
        }

        @SuppressWarnings("rawtypes") final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    public abstract Property<String> getMappingChannel();

    public abstract Property<String> getMappingVersion();

    public Provider<String> getMappings() {
        return mapping;
    }

    public void mappings(Provider<String> channel, Provider<String> version) {
        getMappingChannel().set(channel);
        getMappingVersion().set(version);
    }

    public void mappings(String channel, String version) {
        getMappingChannel().set(channel);
        getMappingVersion().set(version);
    }

    public void mappings(Map<String, ? extends CharSequence> mappings) {
        CharSequence channel = mappings.get("channel");
        CharSequence version = mappings.get("version");

        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify both mappings channel and version");
        }

        mappings(channel.toString(), version.toString());
    }

    public ConfigurableFileCollection getAccessTransformers() {
        return accessTransformers;
    }

    // Following are helper methods for adding ATs
    // Equivalents are not added for SASs as it's not used outside of Forge

    public void setAccessTransformers(Object... files) {
        getAccessTransformers().setFrom(files);
    }

    public void setAccessTransformer(Object files) {
        getAccessTransformers().setFrom(files);
    }

    public void accessTransformers(Object... files) {
        getAccessTransformers().from(files);
    }

    public void accessTransformer(Object file) {
        getAccessTransformers().from(file);
    }

    public abstract ConfigurableFileCollection getSideAnnotationStrippers();

    /**
     * If the Eclipse configurations should run the {@code prepareX} task before starting the game.
     * <p>
     * Default: {@code false}
     */
    public abstract Property<Boolean> getEnableEclipsePrepareRuns();

    /**
     * If the IntelliJ IDEA configurations should run the {@code prepareX} task before starting the game.
     * <p>
     * Default: {@code false}
     */
    public abstract Property<Boolean> getEnableIdeaPrepareRuns();

    /**
     * If Gradle resources should be copied to the respective IDE output folders before starting the game.
     * <p>
     * Default: {@code false}
     */
    public abstract Property<Boolean> getCopyIdeResources();

    /**
     * If run configurations should be grouped in folders.
     * <p>
     * Default: {@code false}
     */
    public abstract Property<Boolean> getGenerateRunFolders();
}
