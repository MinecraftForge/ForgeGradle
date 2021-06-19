/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
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
}
