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
    public DelayedString(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner, pattern, resolvers);
    }

    @Override
    public String resolveDelayed()
    {
        return DelayedBase.resolve(pattern, project, resolvers);
    }
    
    public DelayedString forceResolving()
    {
        resolveOnce = false;
        return this;
    }
}
