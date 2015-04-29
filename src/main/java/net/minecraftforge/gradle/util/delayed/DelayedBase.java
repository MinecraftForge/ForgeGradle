package net.minecraftforge.gradle.util.delayed;

import groovy.lang.Closure;

@SuppressWarnings("serial")
public abstract class DelayedBase<V> extends Closure<V>
{
    protected TokenReplacer replacer;

    public DelayedBase(String pattern)
    {
        super(null);
        replacer = new TokenReplacer(pattern);
    }
    
    public DelayedBase(TokenReplacer replacer)
    {
        super(null);
        this.replacer = replacer;
    }

    /**
     * Does something with the replaced token and returns the proper type.
     * @return
     */
    public abstract V resolveDelayed(String replaced);
    
    @Override
    public final V call()
    {
        String replaced = null;
        if (replacer != null)
            replaced = replacer.replace();
        
        return resolveDelayed(replaced);
    }
    
    @Override
    public final V call(Object obj)
    {
        return call();
    }
    
    @Override
    public final V call(Object... objects)
    {
        return call();
    }

    @Override
    public String toString()
    {
        return call().toString();
    }
}
