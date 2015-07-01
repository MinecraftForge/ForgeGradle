package net.minecraftforge.gradle.util.json.fgversion;

import java.util.List;
import java.util.Map;

public class ForgeGradleWrapper
{
    public List<String>                    versionNumbers;
    public Map<String, ForgeGradleVersion> versionObjects;

    @Override
    public String toString()
    {
        return "ForgeGradleWrapper [versionNumbers=" + versionNumbers + ", versionObjects=" + versionObjects + "]";
    }
}
