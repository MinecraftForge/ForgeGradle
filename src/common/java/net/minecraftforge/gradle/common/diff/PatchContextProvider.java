package net.minecraftforge.gradle.common.diff;

import java.io.IOException;
import java.util.List;

public interface PatchContextProvider {

    List<String> getData(ContextualPatch.SinglePatch patch) throws IOException;

    void setData(ContextualPatch.SinglePatch patch, List<String> data) throws IOException;

    void setFailed(ContextualPatch.SinglePatch patch, List<String> lines) throws IOException;
}
