package net.minecraftforge.gradle.json.version;

import java.io.Serializable;
import java.util.Map;

public class AssetIndex implements Serializable
{
    private static final long serialVersionUID = -1521334204736262787L;
    
    public boolean            virtual;
    public Map<String, AssetEntry> objects;

    public static class AssetEntry implements Serializable
    {
        private static final long serialVersionUID = 3222732991617117379L;
        
        public final String hash;
        public final long    size;

        AssetEntry(String hash, long size)
        {
            this.hash = hash.toLowerCase();
            this.size = size;
        }
    }
}
