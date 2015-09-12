package net.minecraftforge.gradle.util.delayed;

import java.io.File;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public class DelayedFile extends DelayedBase<File>
{
    protected final File hardcoded;
    protected transient final Project project;
    
    public DelayedFile(File file)
    {
        super((TokenReplacer)null);
        hardcoded = file;
        project = null;
    }
    
    public DelayedFile(Project project, ReplacementProvider provider, String pattern)
    {
        super(provider, pattern);
        hardcoded = null;
        this.project = project;
    }
    
    public DelayedFile(Project project, TokenReplacer replacer)
    {
        super(replacer);
        hardcoded = null;
        this.project = project;
        
    }

    @Override
    public File resolveDelayed(String replaced)
    {
        if (hardcoded != null)
            return hardcoded;
        
        return project.file(replaced);
    }

    public DelayedFileTree toZipTree()
    {
        if (hardcoded != null)
            return new DelayedFileTree(hardcoded);
        else
            return new DelayedFileTree(project, replacer);
        
    }
}
