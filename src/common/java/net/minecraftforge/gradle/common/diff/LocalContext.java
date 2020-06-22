package net.minecraftforge.gradle.common.diff;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

class LocalContext implements PatchContextProvider {

    private final ContextualPatch contextualPatch;

    LocalContext(ContextualPatch contextualPatch) {
        this.contextualPatch = contextualPatch;
    }

    @Override
    public List<String> getData(ContextualPatch.SinglePatch patch) throws IOException {
        patch.targetFile = contextualPatch.computeTargetFile(patch);
        if (!patch.targetFile.exists() || patch.binary) return null;
        return contextualPatch.readFile(patch.targetFile);
    }

    @Override
    public void setData(ContextualPatch.SinglePatch patch, List<String> data) throws IOException {
        contextualPatch.backup(patch.targetFile);
        contextualPatch.writeFile(patch, data);
    }

    @Override
    public void setFailed(ContextualPatch.SinglePatch patch, List<String> lines) throws IOException {
        if (lines.isEmpty()) return;
        try (PrintWriter p = new PrintWriter(new FileOutputStream(patch.targetFile + ".rej"))) {
            for (String line : lines) {
                p.println(line);
            }
        }
    }
}
