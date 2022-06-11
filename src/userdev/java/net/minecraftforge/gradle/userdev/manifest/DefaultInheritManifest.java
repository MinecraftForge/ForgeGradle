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

package net.minecraftforge.gradle.userdev.manifest;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.java.archives.Attributes;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.java.archives.ManifestException;
import org.gradle.api.java.archives.ManifestMergeSpec;
import org.gradle.api.java.archives.internal.DefaultManifest;
import org.gradle.api.java.archives.internal.DefaultManifestMergeSpec;
import org.gradle.util.ConfigureUtil;

import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultInheritManifest implements InheritManifest {

    private List<DefaultManifestMergeSpec> inheritMergeSpecs = new ArrayList<>();
    private final FileResolver fileResolver;
    private final Manifest internalManifest;

    public DefaultInheritManifest(FileResolver fileResolver) {
        this.internalManifest = new DefaultManifest(fileResolver);
        this.fileResolver = fileResolver;
    }

    @Override
    public InheritManifest inheritFrom(Object... inheritPaths) {
        inheritFrom(inheritPaths, null);
        return this;
    }

    @SuppressWarnings("deprecation") //That is the only exposed method for now.....
    @Override
    public InheritManifest inheritFrom(Object inheritPaths, Closure closure) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec();
        mergeSpec.from(inheritPaths);
        inheritMergeSpecs.add(mergeSpec);
        ConfigureUtil.configure(closure, mergeSpec);
        return this;
    }

    @Override
    public Attributes getAttributes() {
        return internalManifest.getAttributes();
    }

    @Override
    public Map<String, Attributes> getSections() {
        return internalManifest.getSections();
    }

    @Override
    public Manifest attributes(Map<String, ?> map) throws ManifestException {
        internalManifest.attributes(map);
        return this;
    }

    @Override
    public Manifest attributes(Map<String, ?> map, String s) throws ManifestException {
        internalManifest.attributes(map, s);
        return this;
    }

    @Override
    public DefaultManifest getEffectiveManifest() {
        DefaultManifest base = new DefaultManifest(fileResolver);
        for (DefaultManifestMergeSpec mergeSpec : inheritMergeSpecs) {
            base = mergeSpec.merge(base, fileResolver);
        }

        base.from(internalManifest);

        return base.getEffectiveManifest();
    }

    @Override
    public Manifest writeTo(Object o) {
        this.getEffectiveManifest().writeTo(o);
        return this;
    }

    @Override
    public Manifest from(Object... objects) {
        internalManifest.from(objects);
        return this;
    }

    @Override
    public Manifest from(Object o, Closure<?> closure) {
        internalManifest.from(o, closure);
        return this;
    }

    @Override
    public Manifest from(Object o, Action<ManifestMergeSpec> action) {
        internalManifest.from(o, action);
        return this;
    }
}
