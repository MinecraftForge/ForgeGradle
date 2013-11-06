package net.minecraftforge.gradle;

import groovy.lang.Closure;

import org.gradle.api.file.CopySpec;

@SuppressWarnings("serial")
public class CopyInto extends Closure<Object>
{
    private String dir;
    String[] filters;

    public CopyInto(String dir, String... filters)
    {
        super(null);
        this.dir = dir;
        this.filters = filters;
    }

    @Override
    public Object call(Object... args)
    {
        CopySpec spec = (CopySpec)getDelegate();
        for (String s : filters)
        {
            if (s.startsWith("!")) spec.exclude(s.substring(1));
            else                   spec.include(s);
        }
        spec.into(dir);
        return null;
    }
};