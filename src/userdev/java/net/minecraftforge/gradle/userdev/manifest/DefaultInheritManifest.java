/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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

    @Override
    public InheritManifest inheritFrom(Object inheritPaths, Action<ManifestMergeSpec> action) {
        DefaultManifestMergeSpec mergeSpec = new DefaultManifestMergeSpec();
        mergeSpec.from(inheritPaths);
        inheritMergeSpecs.add(mergeSpec);
        if (action != null)
            action.execute(mergeSpec);
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
