package net.minecraftforge.gradle.util.json.version;

import java.util.List;

public class ExtractRule
{
    private List<String> exclude;

    public boolean exclude(String name)
    {
        if (exclude == null) return false;
        for (String s : exclude)
        {
            if (name.startsWith(s)) return true;
        }
        return false;
    }
}
