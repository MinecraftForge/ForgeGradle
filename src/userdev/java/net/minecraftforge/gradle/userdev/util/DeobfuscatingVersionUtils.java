/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
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
