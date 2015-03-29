package net.minecraftforge.gradle.tweakers;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import net.minecraftforge.gradle.GradleStartCommon;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CoremodTweaker implements ITweaker
{
    protected static final Logger LOGGER             = LogManager.getLogger("GradleStart");
    private static final String   COREMOD_CLASS      = "fml.relauncher.CoreModManager";
    private static final String   TWEAKER_SORT_FIELD = "tweakSorting";

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile)
    {
    }

    @SuppressWarnings("unchecked")
    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader)
    {
        try
        {
            Field coreModList = GradleStartCommon.getFmlClass("fml.relauncher.CoreModManager", classLoader).getDeclaredField("loadPlugins");
            coreModList.setAccessible(true);

            // grab constructor.
            Class<ITweaker> clazz = (Class<ITweaker>) GradleStartCommon.getFmlClass("fml.relauncher.CoreModManager$FMLPluginWrapper", classLoader);
            Constructor<ITweaker> construct = (Constructor<ITweaker>) clazz.getConstructors()[0];
            construct.setAccessible(true);

            Field[] fields = clazz.getDeclaredFields();
            Field pluginField = fields[1];  // 1
            Field fileField = fields[3];  // 3
            Field listField = fields[2];  // 2

            Field.setAccessible(clazz.getConstructors(), true);
            Field.setAccessible(fields, true);

            List<ITweaker> oldList = (List<ITweaker>) coreModList.get(null);

            for (int i = 0; i < oldList.size(); i++)
            {
                ITweaker tweaker = oldList.get(i);

                if (clazz.isInstance(tweaker))
                {
                    Object coreMod = pluginField.get(tweaker);
                    Object oldFile = fileField.get(tweaker);
                    File newFile = GradleStartCommon.coreMap.get(coreMod.getClass().getCanonicalName());

                    LOGGER.info("Injecting location in coremod {}", coreMod.getClass().getCanonicalName());

                    if (newFile != null && oldFile == null)
                    {
                        // build new tweaker.
                        oldList.set(i, construct.newInstance(new Object[] {
                                (String) fields[0].get(tweaker), // name
                                coreMod, // coremod
                                newFile, // location
                                fields[4].getInt(tweaker), // sort index?
                                ((List<String>) listField.get(tweaker)).toArray(new String[0])
                        }));
                    }
                }
            }
        }
        catch (Throwable t)
        {
            LOGGER.log(Level.ERROR, "Something went wrong with the coremod adding.");
            t.printStackTrace();
        }

        // inject the additional AT tweaker
        String atTweaker = "net.minecraftforge.gradle.tweakers.AccessTransformerTweaker";
        ((List<String>) Launch.blackboard.get("TweakClasses")).add(atTweaker);

        // make sure its after the deobf tweaker
        try
        {
            Field f = GradleStartCommon.getFmlClass(COREMOD_CLASS, classLoader).getDeclaredField(TWEAKER_SORT_FIELD);
            f.setAccessible(true);
            ((Map<String, Integer>) f.get(null)).put(atTweaker, Integer.valueOf(1001));
        }
        catch (Throwable t)
        {
            LOGGER.log(Level.ERROR, "Something went wrong with the adding the AT tweaker adding.");
            t.printStackTrace();
        }
    }

    @Override
    public String getLaunchTarget()
    {
        // if it gets here... something went terribly wrong..
        return null;
    }

    @Override
    public String[] getLaunchArguments()
    {
        // if it gets here... something went terribly wrong.
        return new String[0];
    }
}
