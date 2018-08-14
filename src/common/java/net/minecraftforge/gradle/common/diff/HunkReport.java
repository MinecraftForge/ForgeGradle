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