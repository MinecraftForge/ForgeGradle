package net.minecraftforge.gradle.util.delayed;


@SuppressWarnings("serial")
public class DelayedString extends DelayedBase<String>
{
    public DelayedString(ReplacementProvider provider,  String pattern)
    {
        super(provider, pattern);
    }
    
    public DelayedString(TokenReplacer replacer)
    {
        super(replacer);
    }

    @Override
    public String resolveDelayed(String replaced)
    {
        return replaced;
    }
}
