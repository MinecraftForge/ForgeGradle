package net.minecraftforge.gradle.common.diff;

import com.cloudbees.diff.Hunk;
import com.cloudbees.diff.PatchException;

import java.util.ArrayList;
import java.util.List;

public class PatchUtils {

    static void applyPatch(ContextualPatch contextualPatch, ContextualPatch.SinglePatch patch, boolean dryRun) throws PatchException {
        List<String> target = contextualPatch.contextProvider.getData(patch.targetPath);

        if (target != null && !patch.binary) {
            if (contextualPatch.patchCreatesNewFileThatAlreadyExists(patch, target)) {
                return; // Skipped!
            }
        } else if (target == null) {
            target = new ArrayList<>();
        }

        if (patch.mode == ContextualPatch.Mode.DELETE) {
            target = new ArrayList<>();
        } else {
            if (!patch.binary) {
                for (Hunk hunk : patch.hunks) {
                    try {
                        contextualPatch.applyHunk(target, hunk);
                    } catch (Exception ex) {
                        throw new PatchException("Failed to apply patch!"); // Failure!
                    }
                }
            }
        }

        if (!dryRun) {
            contextualPatch.contextProvider.setData(patch.targetPath, target);
        }
    }

}
