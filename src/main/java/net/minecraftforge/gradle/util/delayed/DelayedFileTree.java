package net.minecraftforge.gradle.util.delayed;

import java.io.File;

import net.minecraftforge.gradle.util.ZipFileTree;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;

@SuppressWarnings("serial")
public class DelayedFileTree extends DelayedBase<FileTree>
{
    protected final File hardcoded;
    protected transient final Project project;
    
    public DelayedFileTree(File file)
    {
        super((TokenReplacer)null);
        hardcoded = file;
        project = null;
    }
    
    public DelayedFileTree(Project project, String pattern)
    {
        super(pattern);
        hardcoded = null;
        this.project = project;
    }
    
    public DelayedFileTree(Project project, TokenReplacer replacer)
    {
        super(replacer);
        hardcoded = null;
        this.project = project;
        
    }

    @Override
    public FileTree resolveDelayed(String replaced)
    {
        String name;
        File file;
        
        if (hardcoded != null)
        {
            name = hardcoded.getName();
            file = hardcoded;
        }
        else
        {
            name = replaced;
            file = project.file(replaced);
        }
        
        if (name.endsWith(".jar") || name.endsWith(".zip"))
        {
            return new FileTreeAdapter(new ZipFileTree(file));
        }
        else
        {
            return project.fileTree(file);
        }
    }
}
