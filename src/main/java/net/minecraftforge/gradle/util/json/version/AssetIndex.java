package net.minecraftforge.gradle.util.json.version;

import java.util.Map;

public class AssetIndex
{
    public boolean virtual = false; // sane default
    public Map<String, AssetEntry> objects;

    public static class AssetEntry
    {
        public final String hash;
        public final long   size;

        AssetEntry(String hash, long size)
        {
            this.hash = hash.toLowerCase();
            this.size = size;
        }
    }
}
