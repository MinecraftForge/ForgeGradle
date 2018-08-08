package net.minecraftforge.gradle.common.diff;

import com.cloudbees.diff.Hunk;
import com.cloudbees.diff.PatchException;

import java.util.ArrayList;
import java.util.List;

public interface PatchContextProvider {

    List<String> getData(String target);

    void setData(String target, List<String> data);

}
