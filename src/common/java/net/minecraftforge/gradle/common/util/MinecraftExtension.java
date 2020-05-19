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

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingPropertyException;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class MinecraftExtension extends GroovyObjectSupport {

    protected final Project project;
    protected final NamedDomainObjectContainer<RunConfig> runs;

    protected String mapping_channel;
    protected String mapping_version;
    protected List<File> accessTransformers;
    protected List<File> sideAnnotationStrippers;

    @Inject
    public MinecraftExtension(final Project project) {
        this.project = project;

        this.runs = project.container(RunConfig.class, name -> new RunConfig(project, name));
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

        @SuppressWarnings("rawtypes")
        final Closure closure = (Closure) value;
        final RunConfig runConfig = getRuns().maybeCreate(name);

        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.setDelegate(runConfig);
        closure.call();
    }

    @Deprecated  //Remove when we can break things.
    public void setMappings(String mappings) {
        project.getLogger().warn("Deprecated MinecraftExtension.setMappings called. Use mappings(channel, version)");
        int idx = mappings.lastIndexOf('_');
        if (idx == -1)
            throw new RuntimeException("Invalid mapping string format, must be {channel}_{version}. Consider using mappings(channel, version) directly.");
        String channel = mappings.substring(0, idx);
        String version = mappings.substring(idx + 1);
        mappings(channel, version);
    }

    public void mappings(String channel, String version) {
        this.mapping_channel = channel;
        this.mapping_version = version;
    }

    public void mappings(Map<String, CharSequence> mappings) {
        CharSequence channel = mappings.get("channel");
        CharSequence version = mappings.get("version");

        if (channel == null || version == null) {
            throw new IllegalArgumentException("Must specify both mappings channel and version");
        }

        mappings(channel.toString(), version.toString());
    }

    public String getMappings() {
        return mapping_channel == null || mapping_version == null ? null : mapping_channel + '_' + mapping_version;
    }
    public String getMappingChannel() {
        return mapping_channel;
    }
    public void setMappingChannel(String value) {
        this.mapping_channel = value;
    }
    public String getMappingVersion() {
        return mapping_version;
    }
    public void setMappingVersion(String value) {
        this.mapping_version = value;
    }

    public void setAccessTransformers(List<File> accessTransformers) {
        this.accessTransformers = new ArrayList<>(accessTransformers);
    }

    public void setAccessTransformers(File... accessTransformers) {
        setAccessTransformers(Arrays.asList(accessTransformers));
    }

    public void setAccessTransformer(File accessTransformers) {
        setAccessTransformers(accessTransformers);
    }

    public void accessTransformer(File... accessTransformers) {
        getAccessTransformers().addAll(Arrays.asList(accessTransformers));
    }

    public void accessTransformers(File... accessTransformers) {
        accessTransformer(accessTransformers);
    }

    public List<File> getAccessTransformers() {
        if (accessTransformers == null) {
            accessTransformers = new ArrayList<>();
        }

        return accessTransformers;
    }

    public void setSideAnnotationStrippers(List<File> value) {
        this.sideAnnotationStrippers = new ArrayList<>(value);
    }
    public void setSideAnnotationStrippers(File... value) {
        setSideAnnotationStrippers(Arrays.asList(value));
    }
    public void setSideAnnotationStripper(File value) {
        getSideAnnotationStrippers().add(value);
    }
    public void setSideAnnotationStripper(File... values) {
        for (File value : values)
            setSideAnnotationStripper(value);
    }
    public void sideAnnotationStripper(File... values) {
        setSideAnnotationStripper(values);
    }
    public void sideAnnotationStrippers(File... values) {
        sideAnnotationStripper(values);
    }
    public List<File> getSideAnnotationStrippers() {
        if (sideAnnotationStrippers == null) {
            sideAnnotationStrippers = new ArrayList<>();
        }
        return sideAnnotationStrippers;
    }
}
