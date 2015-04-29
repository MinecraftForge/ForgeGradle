package net.minecraftforge.gradle.util.caching;

import java.io.File;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.Action;
import org.gradle.api.Task;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class WriteCacheAction implements Action<Task>
{
    private final Annotated annot;
    private final List<Annotated> inputs;

    public WriteCacheAction(Annotated annot, List<Annotated> inputs)
    {
        this.annot = annot;
        this.inputs = inputs;
    }

    @Override
    public void execute(Task task)
    {
        execute((ICachableTask) task);
    }

    public void execute(ICachableTask task)
    {
        if (!task.doesCache())
            return;

        try
        {
            File outFile = task.getProject().file(annot.getValue(task));
            if (outFile.exists())
            {
                File hashFile = CacheUtil.getHashFile(outFile);
                Files.write(CacheUtil.getHashes(annot, inputs, task), hashFile, Constants.CHARSET);
            }
        }
        // error? spit it and do the task.
        catch (Exception e)
        {
            Throwables.propagate(e);
        }
    }

}
