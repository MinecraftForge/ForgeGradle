package net.minecraftforge.gradle.util.json.fgversion;

import java.util.Arrays;

public class ForgeGradleVersion
{
    public String version, docUrl;
    public boolean outdated, broken;
    public String[] changes, bugs;
    public int      index;

    @Override
    public String toString()
    {
        return "ForgeGradleVersion [version=" + version + ", docUrl=" + docUrl + ", outDated=" + outdated + ", broken=" + broken + ", changes=" + Arrays.toString(changes) + ", bugs=" + Arrays.toString(bugs) + ", index=" + index + "]";
    }
}
