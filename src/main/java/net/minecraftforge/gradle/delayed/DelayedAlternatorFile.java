package net.minecraftforge.gradle.delayed;

import java.io.File;
import java.util.LinkedList;

import org.gradle.api.Project;

/**
 * @author AbrarSyed
 *         This is just like the delayedFile except that it allows for having multiple patterns.
 *         It checks the patterns in order until it finds one that exists.
 *         This is useful for DelayedFiles where there could be different files that are needed.
 */
@SuppressWarnings("serial")
public class DelayedAlternatorFile extends DelayedFile
{
    private LinkedList<String> patterns = new LinkedList<String>();

    public DelayedAlternatorFile(Project owner, String pattern)
    {
        super(owner, pattern);
        patterns.add(pattern);
    }

    @SuppressWarnings("rawtypes")
    public DelayedAlternatorFile(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner, pattern, resolvers);
        patterns.add(pattern);
    }

    public DelayedAlternatorFile add(String pattern)
    {
        patterns.add(pattern);
        return this;
    }

    @Override
    public File call()
    {
        if (resolved != null)
            return resolved;

        for (String pattern : patterns)
        {
            resolved = project.file(DelayedBase.resolve(pattern, project, resolvers));

            if (resolved.exists()) // else keep getting the next pattern.
                break;
        }
        return resolved;
    }
}
