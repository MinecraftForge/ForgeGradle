package net.minecraftforge.gradle.delayed;

import java.io.File;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public class DelayedFile extends DelayedBase<File>
{
    private final File file;
    
    public DelayedFile(File file)
    {
        super(null, null);
        this.file = file;
    }
    
    public DelayedFile(Project owner, String pattern)
    {
        super(owner, pattern);
        file = null;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DelayedFile(Project owner, String pattern, IDelayedResolver resolver)
    {
        super(owner, pattern, resolver);
        file = null;
    }

    @Override
    public File resolveDelayed()
    {
        if (file != null)
            return file;
        else
            return project.file(DelayedBase.resolve(pattern, project, resolver));
    }

    public DelayedFileTree toZipTree()
    {
        return new DelayedFileTree(project, pattern, true, resolver);
    }
}
