package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Set;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.dev.FmlDevPlugin;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.TaskAction;

public class SubprojectTask extends DefaultTask
{
    private DelayedFile                 buildFile;
    private String                      tasks;
    private LinkedList<Action<Project>> configureProject = new LinkedList<Action<Project>>();
    private Action<Task>                configureTask;

    @TaskAction
    public void doTask() throws IOException
    {
        Project childProj = FmlDevPlugin.getProject(getBuildFile(), getProject());
        
        // configure the project
        for (Action<Project> act : configureProject)
        {
            if (act != null)
                act.execute(childProj);
        }

        for (String task : tasks.split(" "))
        {
            Set<Task> list = childProj.getTasksByName(task, false);
            for (Task t : list)
            {
                if (configureTask != null)
                    configureTask.execute(t);
                executeTask((AbstractTask) t);
            }
        }

        System.gc();
    }

    private void executeTask(AbstractTask task)
    {
        for (Object dep : task.getTaskDependencies().getDependencies(task))
        {
            executeTask((AbstractTask) dep);
        }

        if (!task.getState().getExecuted())
        {
            getLogger().lifecycle(task.getPath());
            task.execute();
        }
    }

    public File getBuildFile()
    {
        return buildFile.call();
    }

    public void setBuildFile(DelayedFile buildFile)
    {
        this.buildFile = buildFile;
    }

    public String getTasks()
    {
        return tasks;
    }

    public void setTasks(String tasks)
    {
        this.tasks = tasks;
    }

    public Action<Task> getConfigureTask()
    {
        return configureTask;
    }

    public void setConfigureTask(Action<Task> configureTask)
    {
        this.configureTask = configureTask;
    }
    
    public void configureProject(Action<Project> action)
    {
        configureProject.add(action);
    }
}
