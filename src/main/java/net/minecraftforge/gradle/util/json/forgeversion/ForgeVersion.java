package net.minecraftforge.gradle.util.json.forgeversion;

import java.util.List;
import java.util.Map;

public class ForgeVersion
{
    public String adfly, artifact, homepage, name, webpath;
    public Map<String, List<Integer>> branches, mcversion;
    public Map<Integer, ForgeBuild> number;
    public Map<String, Integer> promos;
}
