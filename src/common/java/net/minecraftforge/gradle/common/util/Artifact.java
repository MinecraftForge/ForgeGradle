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

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Predicate;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import net.minecraftforge.artifactural.api.artifact.ArtifactIdentifier;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.specs.Spec;

public class Artifact implements ArtifactIdentifier, Comparable<Artifact>, Serializable {
    private static final long serialVersionUID = 1L;

    // group:name:version[:classifier][@extension]
    private final String group;
    private final String name;
    private final String version;
    private final String classifier;
    private final String ext;

    // Cached so we don't rebuild every time we're asked.
    // Transient field so these aren't serialized
    // TODO: investigate whether this needs to be rebuilt because deserialization
    private transient final String path;
    private transient final String file;
    private transient final String fullDescriptor;
    private transient final ComparableVersion comparableVersion;
    private transient final boolean isSnapshot;

    public static Artifact from(String descriptor) {
        String group, name, version;
        String ext = null, classifier = null;

        String[] pts = Iterables.toArray(Splitter.on(':').split(descriptor), String.class);
        group = pts[0];
        name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) { // we have an extension
            ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        version = pts[2];

        if (pts.length > 3) // We have a classifier
            classifier = pts[3];

        return new Artifact(group, name, version, classifier, ext);
    }

    public static Artifact from(ArtifactIdentifier identifier) {
        return new Artifact(identifier.getGroup(), identifier.getName(), identifier.getVersion(), identifier.getClassifier(), identifier.getExtension());
    }

    public static Artifact from(String group, String name, String version, @Nullable String classifier, @Nullable String ext) {
        return new Artifact(group, name, version, classifier, ext);
    }

    Artifact(String group, String name, String version, @Nullable String classifier, @Nullable String ext) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.comparableVersion = new ComparableVersion(this.version);
        this.isSnapshot = this.version.toLowerCase(Locale.ROOT).endsWith("-snapshot");
        this.classifier = classifier;
        this.ext = ext != null ? ext : "jar";

        StringBuilder buf = new StringBuilder();
        buf.append(this.group).append(':').append(this.name).append(':').append(this.version);
        if (this.classifier != null) {
            buf.append(':').append(this.classifier);
        }
        if (ext != null && !"jar".equals(this.ext)) {
            buf.append('@').append(this.ext);
        }
        this.fullDescriptor = buf.toString();

        String file;
        file = this.name + '-' + this.version;
        if (this.classifier != null) file += '-' + this.classifier;
        file += '.' + this.ext;
        this.file = file;

        this.path = String.join("/", this.group.replace('.', '/'), this.name, this.version, this.file);
    }

    public String getLocalPath() {
        return path.replace('/', File.separatorChar);
    }

    public String getDescriptor(){ return fullDescriptor; }
    public String getPath()      { return path;       }
    @Override
    public String getGroup()     { return group;      }
    @Override
    public String getName()      { return name;       }
    @Override
    public String getVersion()   { return version;    }
    @Override
    public String getClassifier(){ return classifier; }
    @Override
    public String getExtension() { return ext;        }
    public String getFilename()  { return file;       }

    public boolean isSnapshot()  { return isSnapshot; }

    public Artifact withVersion(String version) {
        return Artifact.from(group, name, version, classifier, ext);
    }

    @Override
    public String toString() {
        return getDescriptor();
    }

    public Spec<Dependency> asDependencySpec() {
        return (dep) -> group.equals(dep.getGroup()) && name.equals(dep.getName()) && version.equals(dep.getVersion());
    }

    public Predicate<ResolvedArtifact> asArtifactMatcher() {
        return (art) -> {
            String theirClassifier;
            if (art.getClassifier() == null) {
                theirClassifier = "";
            } else {
                theirClassifier = art.getClassifier();
            }

            String theirExt;
            if (art.getExtension().isEmpty()) {
                theirExt = "jar";
            } else {
                theirExt = art.getExtension();
            }

            return (classifier == null || classifier.equals(theirClassifier)) && (ext == null || ext.equals(theirExt));
        };
    }

    @Override
    public int compareTo(Artifact o) {
        int ret;
        if ((ret = group.compareTo(o.group)) != 0) return ret;
        if ((ret = name.compareTo(o.name)) != 0) return ret;
        if ((ret = comparableVersion.compareTo(o.comparableVersion)) != 0) return ret;
        // TODO: comparison of timestamps for snapshot versions (isSnapshot)
        if ((ret = classifier.compareTo(o.classifier)) != 0) return ret;
        return ext.compareTo(o.ext);
    }
}
