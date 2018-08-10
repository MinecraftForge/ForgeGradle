package net.minecraftforge.gradle.common.diff;

import com.cloudbees.diff.Hunk;

class HunkReport {

    ContextualPatch.PatchStatus status;
    Throwable failure;
    int index;
    int fuzz;
    int hunkID;
    Hunk hunk;

    HunkReport(ContextualPatch.PatchStatus status, Throwable failure, int index, int fuzz, int hunkID) {
        this.status = status;
        this.failure = failure;
        this.index = index;
        this.fuzz = fuzz;
        this.hunkID = hunkID;
    }

    HunkReport(ContextualPatch.PatchStatus status, Throwable failure, int index, int fuzz, int hunkID, Hunk hunk) {
        this(status, failure, index, fuzz, hunkID);
        this.hunk = hunk;
    }

    boolean hasFailed() {
        return status == ContextualPatch.PatchStatus.Failure;
    }

}