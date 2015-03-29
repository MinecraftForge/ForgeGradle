package net.minecraftforge.gradle.delayed;

import net.minecraftforge.gradle.ZipFileTree;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;

@SuppressWarnings("serial")
public class DelayedFileTree extends DelayedBase<FileTree>
{
    private boolean zipTree = false;

    public DelayedFileTree(Project owner, String pattern)
    {
        super(owner, pattern);
    }

    public DelayedFileTree(Project owner, String pattern, boolean zipTree)
    {
        super(owner, pattern);
        this.zipTree = zipTree;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DelayedFileTree(Project owner, String pattern, IDelayedResolver resolver)
    {
        super(owner, pattern, resolver);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public DelayedFileTree(Project owner, String pattern, boolean zipTree, IDelayedResolver resolver)
    {
        super(owner, pattern, resolver);
        this.zipTree = zipTree;
    }

    @Override
    public FileTree resolveDelayed()
    {
        if (zipTree)
            //resolved = project.zipTree(DelayedString.resolve(pattern, project, resolvers));
            return new FileTreeAdapter(new ZipFileTree(project.file(DelayedBase.resolve(pattern, project, resolver))));
        else
            return project.fileTree(DelayedBase.resolve(pattern, project, resolver));
    }
}
