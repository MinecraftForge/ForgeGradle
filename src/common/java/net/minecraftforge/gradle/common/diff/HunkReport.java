/*
 * ForgeGradle
 * Copyright (C) 2018.
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

package net.minecraftforge.gradle.common.diff;

import com.cloudbees.diff.Hunk;

public class HunkReport {

    public final ContextualPatch.PatchStatus status;
    public final Throwable failure;
    public final int index;
    public final int fuzz;
    public final int hunkID;
    public final Hunk hunk;

    HunkReport(ContextualPatch.PatchStatus status, Throwable failure, int index, int fuzz, int hunkID) {
        this(status, failure, index, fuzz, hunkID, null);
    }

    HunkReport(ContextualPatch.PatchStatus status, Throwable failure, int index, int fuzz, int hunkID, Hunk hunk) {
        this.status = status;
        this.failure = failure;
        this.index = index;
        this.fuzz = fuzz;
        this.hunkID = hunkID;
        this.hunk = hunk;
    }

    public boolean hasFailed() {
        return status == ContextualPatch.PatchStatus.Failure;
    }

}