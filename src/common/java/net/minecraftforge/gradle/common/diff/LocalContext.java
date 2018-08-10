package net.minecraftforge.gradle.common.diff;

import java.io.IOException;
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

}
