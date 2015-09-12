package net.minecraftforge.gradle.util.delayed;

import java.io.Serializable;
import java.util.Map;

import com.google.common.collect.Maps;

public class ReplacementProvider implements Serializable
{
    private static final long serialVersionUID = 1L;

    private Map<String, String> replaceMap = Maps.newHashMap();

    public void putReplacement(String key, String value)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length() - 1) == '}')
        {
            key = key.substring(1, key.length() - 1);
        }

        replaceMap.put(key, value);
    }

    public boolean hasReplacement(String key)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length() - 1) == '}')
        {
            key = key.substring(1, key.length() - 1);
        }

        return replaceMap.containsKey(key);
    }
    
    public String get(String key)
    {
        return replaceMap.get(key);
    }
}
