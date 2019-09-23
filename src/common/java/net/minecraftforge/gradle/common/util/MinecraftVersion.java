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

public class MinecraftVersion implements Comparable<MinecraftVersion> {
    public static final MinecraftVersion NEGATIVE = from("-1");
    public static final MinecraftVersion v1_14_4 = from("1.14.4");
    public static MinecraftVersion from(String version) {
        return new MinecraftVersion(version);
    }

    private static String fromSnapshot(int year, int week) {
        int value = (year * 100) + week;
        if (value >= 1147 && value <= 1201) return "1.1";
        if (value >= 1203 && value <= 1208) return "1.2";
        if (value >= 1215 && value <= 1230) return "1.3";
        if (value >= 1232 && value <= 1242) return "1.4";
        if (value >= 1249 && value <= 1250) return "1.4.6";
        if (value >= 1301 && value <= 1310) return "1.5";
        if (value >= 1311 && value <= 1312) return "1.5.1";
        if (value >= 1316 && value <= 1326) return "1.6";
        if (value >= 1336 && value <= 1343) return "1.7";
        if (value >= 1347 && value <= 1349) return "1.7.4";
        if (value >= 1402 && value <= 1434) return "1.8";
        if (value >= 1531 && value <= 1607) return "1.9";
        if (value >= 1614 && value <= 1615) return "1.9.3";
        if (value >= 1620 && value <= 1621) return "1.10";
        if (value >= 1632 && value <= 1644) return "1.11";
        if (value >= 1650 && value <= 1650) return "1.11.1";
        if (value >= 1706 && value <= 1718) return "1.12";
        if (value >= 1731 && value <= 1731) return "1.12.1";
        if (value >= 1743 && value <= 1822) return "1.13";
        if (value >= 1830 && value <= 1833) return "1.13.1";
        if (value >= 1843 && value <= 1914) return "1.14";
        if (value >= 1934 && value <= 9999) return "1.15";
        throw new IllegalArgumentException("Invalid snapshot date: " + value);
    }

    private static int[] splitDots(String version) {
        String[] pts = version.split("\\.");
        int[] values = new int[pts.length];
        for (int x = 0; x < pts.length; x++)
            values[x] = Integer.parseInt(pts[x]);
        return values;
    }

    private final boolean isSnapshot;
    private final String full;
    private final int[] nearest;
    private final int week;
    private final int year;
    private final int pre;
    private final String revision;

    private MinecraftVersion(String version) {
        this.full = version;
        if (version.length() == 6 && version.charAt(2) == 'w') {
            this.year = Integer.parseInt(version.substring(0, 2));
            this.week = Integer.parseInt(version.substring(3, 5));
            this.revision = version.substring(5);
            this.isSnapshot = true;
            this.nearest = splitDots(fromSnapshot(this.year, this.week));
            this.pre = 0;
        } else {
            this.week = -1;
            this.year = -1;
            this.isSnapshot = false;
            this.revision = null;
            if (this.full.contains("-pre")) {
                String[] pts = full.split("-pre");
                this.pre = Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else if (this.full.contains("_Pre-Release_")) {
                String[] pts = full.split("_Pre-Release_");
                this.pre = Integer.parseInt(pts[1]);
                this.nearest = splitDots(pts[0]);
            } else {
                this.pre = 0;
                this.nearest = splitDots(full);
            }
        }
    }

    @Override
    public String toString() {
        return this.full;
    }

    @Override
    public int hashCode() {
        return this.full.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MinecraftVersion))
            return false;
        return this.full.equals(((MinecraftVersion)o).full);
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        if (o == null)
            return 1;
        if (this.isSnapshot == o.isSnapshot) {
            if (this.isSnapshot)
                return this.year != o.year ? this.year - o.year : this.week != o.week ? this.week - o.week : this.revision.compareTo(o.revision);
            return compareFull(o);
        } else if (this.isSnapshot)
            return compareFull(o) == 0 ? -1 : 1;
        else
            return compareFull(o) == 0 ? 1 : -1;
    }

    private int compareFull(MinecraftVersion o) {
        for (int x = 0; x < this.nearest.length; x++) {
            if (x >= o.nearest.length) return 1;
            if (this.nearest[x] != o.nearest[x])
                return this.nearest[x] - o.nearest[x];
        }
        if (this.nearest.length < o.nearest.length)
            return -1;
        return this.pre - o.pre;
    }
}
