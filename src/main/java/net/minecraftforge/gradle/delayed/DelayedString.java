package net.minecraftforge.gradle.delayed;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public class DelayedString extends DelayedBase<String>
{
    public DelayedString(Project owner, String pattern)
    {
        super(owner, pattern);
    }
    
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DelayedString(Project owner, String pattern, IDelayedResolver resolver)
    {
        super(owner, pattern, resolver);
    }

    @Override
    public String resolveDelayed()
    {
        return DelayedBase.resolve(pattern, project, resolver);
    }
    
    public DelayedString forceResolving()
    {
        resolveOnce = false;
        return this;
    }
}
