package net.minecraftforge.gradle.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.BaseExtension;

import com.google.common.base.Strings;

public class UserExtension extends BaseExtension
{
    public transient UserBasePlugin<? extends UserExtension> plugin;
    private HashMap<String, Object> replacements = new HashMap<String, Object>();
    private ArrayList<String> includes = new ArrayList<String>();
    private boolean isDecomp = false;

    public UserExtension(UserBasePlugin<? extends UserExtension> plugin)
    {
        super(plugin);
        this.plugin = plugin;
    }
    
    public void replace(Object token, Object replacement)
    {
        replacements.put(token.toString(), replacement);
    }
    
    public void replace(Map<Object, Object> map)
    {
        for (Entry<Object, Object> e : map.entrySet())
        {
            replace(e.getKey(), e.getValue());
        }
    }
    
    public Map<String, Object> getReplacements()
    {
        return replacements;
    }
    
    public List<String> getIncludes()
    {
        return includes;
    }
    
    public void replaceIn(String path)
    {
        includes.add(path);
    }

    public boolean isDecomp()
    {
        return isDecomp;
    }
    
    public void setDecomp()
    {
        this.isDecomp = true;
    }
    
    public void setMappings(String mappings)
    {
        super.setMappings(mappings);
        
        if (!Strings.isNullOrEmpty(mappings))
        {
            this.setMcpVersion(mappings);
        }
    }
}
