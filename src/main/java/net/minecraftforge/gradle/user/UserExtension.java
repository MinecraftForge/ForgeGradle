package net.minecraftforge.gradle.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.BaseExtension;

public class UserExtension extends BaseExtension
{
    private HashMap<String, Object> replacements = new HashMap<String, Object>();
    private ArrayList<String>       includes     = new ArrayList<String>();
    private ArrayList<Object>       ats          = new ArrayList<Object>();

    public UserExtension(UserBasePlugin<UserExtension> plugin)
    {
        super(plugin);
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

    //@formatter:off
    public void accessT(Object obj) { at(obj); }
    public void accessTs(Object... obj) { ats(obj); }
    public void accessTransformer(Object obj) { at(obj); }
    public void accessTransformers(Object... obj) { ats(obj); }
    //@formatter:on

    public void at(Object obj)
    {
        ats.add(obj);
    }

    public void ats(Object... obj)
    {
        for (Object object : obj)
            ats.add(object);
    }

    public List<Object> getAccessTransformers()
    {
        return ats;
    }
}
