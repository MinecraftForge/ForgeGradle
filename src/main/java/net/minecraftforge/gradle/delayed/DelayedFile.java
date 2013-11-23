package net.minecraftforge.gradle.delayed;

import java.io.File;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public class DelayedFile extends DelayedBase<File>
{
    public DelayedFile(Project owner, String pattern)
    {
        super(owner, pattern);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DelayedFile(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner, pattern, resolvers);
    }

    @Override
    public File call()
    {
        if (resolved == null)
        {
            resolved = project.file(DelayedBase.resolve(pattern, project, resolvers));
        }
        return resolved;
    }
    
    public DelayedFileTree toZipTree()
    {
        return new DelayedFileTree(project, pattern, true, resolvers);
    }
}
