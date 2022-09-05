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

package net.minecraftforge.gradle.userdev.util;

import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.Restriction;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.Iterator;

public class DeobfuscatingVersionUtils {

    private DeobfuscatingVersionUtils() {
        throw new IllegalStateException("Can not instantiate an instance of: DeobfuscatingVersionUtils. This is a utility class");
    }

    public static String adaptDeobfuscatedVersion(final String version) {
        if (version.contains("_mapped_")) {
            return version.split("_mapped_")[0];
        }

        return version;
    }

    public static String adaptDeobfuscatedVersionRange(final String version) {
        final VersionRange range;
        try {
            range = VersionRange.createFromVersionSpec(version);
        } catch (InvalidVersionSpecificationException e) {
            throw new IllegalArgumentException("Invalid version range: " + version, e);
        }

        if (range.getRecommendedVersion() != null) {
            return adaptDeobfuscatedVersion(range.getRecommendedVersion().toString());
        }

        StringBuilder buf = new StringBuilder();
        for (Iterator<Restriction> i = range.getRestrictions().iterator(); i.hasNext(); ) {
            Restriction r = i.next();

            buf.append(adaptDeobfuscatedVersionRangeRestriction(r));

            if (i.hasNext()) {
                buf.append(',');
            }
        }
        return buf.toString();
    }

    public static String adaptDeobfuscatedVersionRangeRestriction(final Restriction restriction) {
        StringBuilder buf = new StringBuilder();

        buf.append(restriction.isLowerBoundInclusive() ? '[' : '(');
        if (restriction.getLowerBound() != restriction.getUpperBound()) {
            if (restriction.getLowerBound() != null) {
                buf.append(adaptDeobfuscatedVersion(restriction.getLowerBound().toString()));
            }
            buf.append(',');
            if (restriction.getUpperBound() != null) {
                buf.append(adaptDeobfuscatedVersion(restriction.getUpperBound().toString()));
            }
        } else {
            buf.append(adaptDeobfuscatedVersion(restriction.getLowerBound().toString()));
        }

        buf.append(restriction.isUpperBoundInclusive() ? ']' : ')');

        return buf.toString();
    }
}
