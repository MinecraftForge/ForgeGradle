package net.minecraftforge.gradle.util.caching;

import java.io.File;
import java.nio.charset.Charset;

import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.specs.Spec;

import com.google.common.io.Files;

public class CacheCheckSpec implements Spec<Task>
{
    private final CacheContainer container;

    public CacheCheckSpec(CacheContainer container)
    {
        this.container = container;
    }
    
    @Override
    public boolean isSatisfiedBy(Task task)
    {
        return isSatisfiedBy((ICachableTask)task);
    }

    public boolean isSatisfiedBy(ICachableTask task)
    {
        Logger logger = task.getProject().getLogger();
        
        task.getInputs();

        if (!task.doesCache() || container.cachedList.isEmpty())
            return true;

        for (Annotated field : container.cachedList)
        {
            try
            {
                File file = task.getProject().file(field.getValue(task));

                // not there? do the task.
                if (!file.exists())
                {
                    logger.info("No output file found.");
                    return true;
                }

                File hashFile = CacheUtil.getHashFile(file);
                if (!hashFile.exists())
                {
                    logger.info("No cache file found.");
                    file.delete(); // Kill the output file if the hash doesn't exist, else gradle will think it's up-to-date
                    return true;
                }

                String foundMD5 = Files.toString(CacheUtil.getHashFile(file), Charset.defaultCharset());
                String calcMD5 = CacheUtil.getHashes(field, container.inputList, task);

                if (!calcMD5.equals(foundMD5))
                {
                    logger.info(" Corrupted Cache!");
                    logger.info("Checksums found: " + foundMD5);
                    logger.info("Checksums calculated: " + calcMD5);
                    file.delete();
                    CacheUtil.getHashFile(file).delete();
                    return true;
                }

                logger.debug("Checksums found: " + foundMD5);
                logger.debug("Checksums calculated: " + calcMD5);

            }
            // error? spit it and do the task.
            catch (Exception e)
            {
                e.printStackTrace();
                return true;
            }
        }

        // no problems? all of em are here? skip the task.
        return false;
    }
}
