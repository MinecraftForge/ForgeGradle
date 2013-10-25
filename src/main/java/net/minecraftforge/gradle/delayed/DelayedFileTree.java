package net.minecraftforge.gradle.delayed;

import groovy.lang.Closure;
import net.minecraftforge.gradle.ZipFileTree;
import net.minecraftforge.gradle.delayed.DelayedString.IDelayedResolver;

import org.gradle.api.Project;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.collections.FileTreeAdapter;

@SuppressWarnings("serial")
public class DelayedFileTree extends Closure<FileTree>
{
    private Project            project;
    private FileTree           resolved;
    private String             pattern;
    private boolean            zipTree = false;
    private IDelayedResolver[] resolvers;

    public DelayedFileTree(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }

    public DelayedFileTree(Project owner, String pattern, boolean zipTree, IDelayedResolver... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
        this.zipTree = zipTree;
    }

    @Override
    public FileTree call()
    {
        if (resolved == null)
        {
            if (zipTree)
                //resolved = project.zipTree(DelayedString.resolve(pattern, project, resolvers));
                resolved = new FileTreeAdapter(new ZipFileTree(project.file(DelayedString.resolve(pattern, project, resolvers))));
            else
                resolved = project.fileTree(DelayedString.resolve(pattern, project, resolvers));
        }
        return resolved;
    }
}
