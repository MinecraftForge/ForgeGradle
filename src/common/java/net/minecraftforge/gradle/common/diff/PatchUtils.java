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

    static boolean similar(ContextualPatch patch, String target, String hunk, char lineType) {
        if (patch.c14nAccess) {
            if (patch.c14nWhitespace) {
                target = target.replaceAll("[\t| ]+", " ");
                hunk = hunk.replaceAll("[\t| ]+", " ");
            }
            String[] t = target.split(" ");
            String[] h = hunk.split(" ");

            //don't check length, changing any modifier to default (removing it) will change length
            int targetIndex = 0;
            int hunkIndex = 0;
            while (targetIndex < t.length && hunkIndex < h.length) {
                boolean isTargetAccess = isAccess(t[targetIndex]);
                boolean isHunkAccess = isAccess(h[hunkIndex]);
                if (isTargetAccess || isHunkAccess) {
                    //Skip access modifiers
                    if (isTargetAccess) {
                        targetIndex++;
                    }
                    if (isHunkAccess) {
                        hunkIndex++;
                    }
                    continue;
                }
                String hunkPart = h[hunkIndex];
                String targetPart = t[targetIndex];
                boolean labels = isLabel(targetPart) && isLabel(hunkPart);
                if (!labels && !targetPart.equals(hunkPart)) {
                    return false;
                }
                hunkIndex++;
                targetIndex++;
            }
            return h.length == hunkIndex && t.length == targetIndex;
        }
        if (patch.c14nWhitespace) {
            return target.replaceAll("[\t| ]+", " ").equals(hunk.replaceAll("[\t| ]+", " "));
        } else {
            return target.equals(hunk);
        }
    }

    static boolean isAccess(String data) {
        return data.equalsIgnoreCase("public") ||
                data.equalsIgnoreCase("private") ||
                data.equalsIgnoreCase("protected") ||
                data.equalsIgnoreCase("final");
    }

    static boolean isLabel(String data) { //Damn FernFlower
        return data.startsWith("label");
    }

}
