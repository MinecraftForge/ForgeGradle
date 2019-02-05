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

import java.io.File;
import java.util.Locale;
import org.apache.maven.artifact.versioning.ComparableVersion;

import com.amadornes.artifactural.api.artifact.ArtifactIdentifier;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;

public class Artifact implements ArtifactIdentifier, Comparable<Artifact> {
    //Descriptor parts: group:name:version[:classifier][@extension]
    private String group;
    private String name;
    private String version;
    private String classifier = null;
    private String ext = "jar";

    //Caches so we don't rebuild every time we're asked.
    private String path;
    private String file;
    private String descriptor;
    private ComparableVersion comp;
    private boolean isSnapshot = false;

    public static Artifact from(String descriptor) {
        Artifact ret = new Artifact();
        ret.descriptor = descriptor;

        String[] pts = Iterables.toArray(Splitter.on(':').split(descriptor), String.class);
        ret.group = pts[0];
        ret.name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) {
            ret.ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        ret.version = pts[2];
        ret.comp = new ComparableVersion(ret.version);
        ret.isSnapshot = ret.version.toLowerCase(Locale.ENGLISH).endsWith("-snapshot");

        if (pts.length > 3)
            ret.classifier = pts[3];

        ret.file = ret.name + '-' + ret.version;
        if (ret.classifier != null) ret.file += '-' + ret.classifier;
        ret.file += '.' + ret.ext;

        ret.path = ret.group.replace('.', '/') + '/' + ret.name + '/' + ret.version + '/' + ret.file;

        return ret;
    }

    public static Artifact from(String group, String name, String version, String classifier, String ext) {
        StringBuilder buf = new StringBuilder();
        buf.append(group).append(':').append(name).append(':').append(version);
        if (classifier != null)
            buf.append(':').append(classifier);
        if (ext != null && !"jar".equals(ext))
            buf.append('@').append(ext);
        return from(buf.toString());
    }

    public File getLocalPath(File base) {
        return new File(base, path.replace('/', File.separatorChar));
    }

    public String getDescriptor(){ return descriptor; }
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
    @Override
    public String toString() {
        return getDescriptor();
    }

    @Override
    public int compareTo(Artifact o) {
        int ret = 0;
        if ((ret = group.compareTo(o.group)) != 0) return ret;
        if ((ret = name.compareTo(o.name)) != 0) return ret;
        if ((ret = comp.compareTo(o.comp)) != 0) return ret;
        if (isSnapshot) {
            //TODO: Timestamps
        }
        if ((ret = classifier.compareTo(o.classifier)) != 0) return ret;
        return ext.compareTo(o.ext);
    }
}
