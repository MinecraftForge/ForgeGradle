package net.minecraftforge.gradle.common.diff;

public class PatchUtils {

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
