package net.minecraftforge.gradle.util.delayed;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public class TokenReplacer
{
    protected static Logger LOGGER = LoggerFactory.getLogger(DelayedBase.class);
    
    private static Map<String, String> replaceMap = Maps.newHashMap();
    
    public static void putReplacement(String key, String value)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length()-1) == '}')
        {
            key = key.substring(1, key.length()-1);
        }
        
        replaceMap.put(key, value);
    }
    
    public static boolean hasReplacement(String key)
    {
        // strip off the {}
        if (key.charAt(0) == '{' && key.charAt(key.length()-1) == '}')
        {
            key = key.substring(1, key.length()-1);
        }
        
        return replaceMap.containsKey(key);
    }
    
    private final String input;
    private String outCache;
    
    public TokenReplacer(String input)
    {
        this.input = input;
    }
    
    public String replace()
    {
        if (outCache != null)
            return outCache;
        
        LOGGER.debug("Resolving: {}", input);
        
        StringBuilder builder = new StringBuilder(input);
        boolean saveOutCache = true;
        
        int index = 0;
        int endIndex;
        
        while(true)
        {
            index = builder.indexOf("{", index);
            
            
            if (index < 0) // no more found
                break;
            
            endIndex = builder.indexOf("}", index);
            
            if (endIndex < 0) // random { with no closing brace?
                break;
            
            String key = builder.substring(index+1, endIndex); // skip the {}
            String repl = replaceMap.get(key);
            if (repl == null)
            {
                index = endIndex; // skip this {} token, we dont have the necessray info for it.
                saveOutCache = false;
                
                // eventually remove.. or keep forever?
                throw new RuntimeException("MISSING REPLACEMENT DATA FOR "+key);
            }
            else
            {
                // replace the {} too
                builder.replace(index, endIndex+1, repl);
                // do not move the index or lastIndex pointers.
                // there is the chance that the replacement string has tokens in it that need replacing.
            }
        }
        
        String out = builder.toString();
        
        if (saveOutCache)
            outCache = out;
        
        LOGGER.debug("Resolved: {}", out);
        
        return out;
    }
    
    /**
     * Cleanes the cached replacedOutput.
     * This is only useful if the token replacements have changed since the data was cached.
     */
    public void cleanCache()
    {
        outCache = null;
    }
}
