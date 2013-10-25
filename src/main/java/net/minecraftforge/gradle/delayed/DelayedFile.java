package net.minecraftforge.gradle.delayed;

import java.io.File;

import net.minecraftforge.gradle.delayed.DelayedString.IDelayedResolver;

import org.gradle.api.Project;

import groovy.lang.Closure;

@SuppressWarnings("serial")
public class DelayedFile extends Closure<File>
{
    private Project project;
    private File resolved;
    private String pattern;
    private IDelayedResolver[] resolvers;

    public DelayedFile(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }

    @Override
    public File call()
    {
        if (resolved == null)
        {
            resolved = project.file(DelayedString.resolve(pattern, project, resolvers));
        }
        return resolved;
    }
}
