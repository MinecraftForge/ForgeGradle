package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;

import java.io.File;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;

import com.google.common.base.Joiner;
import com.google.common.io.Files;

/**
 * This class offers some extra helper methods for caching files.
 */
public abstract class CachedTask extends DefaultTask
{
    private boolean                    doesCache  = true;
    private boolean                    cacheSet   = false;
    private final ArrayList<Annotated> cachedList = new ArrayList<Annotated>();
    private final ArrayList<Annotated> inputList  = new ArrayList<Annotated>();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public CachedTask()
    {
        super();

        Class<? extends Task> clazz = this.getClass();
        while (clazz != null)
        {
            for (Field f : clazz.getDeclaredFields())
            {
                if (f.isAnnotationPresent(Cached.class))
                {
                    addCachedOutput(new Annotated(clazz, f.getName()));
                }

                if (!f.isAnnotationPresent(Excluded.class) &&
                        (
                        f.isAnnotationPresent(InputFile.class) ||
                        f.isAnnotationPresent(InputFiles.class) ||
                        f.isAnnotationPresent(InputDirectory.class) ||
                        f.isAnnotationPresent(Input.class)
                        ))
                {
                    inputList.add(new Annotated(clazz, f.getName()));
                }
            }
            
            for (Method m : clazz.getDeclaredMethods())
            {
                if (m.isAnnotationPresent(Cached.class))
                {
                    addCachedOutput(new Annotated(clazz, m.getName(), true));
                }

                if (!m.isAnnotationPresent(Excluded.class) &&
                        (
                        m.isAnnotationPresent(InputFile.class) ||
                        m.isAnnotationPresent(InputFiles.class) ||
                        m.isAnnotationPresent(InputDirectory.class) ||
                        m.isAnnotationPresent(Input.class)
                        ))
                {
                    inputList.add(new Annotated(clazz, m.getName(), true));
                }
            }

            clazz = (Class<? extends Task>) clazz.getSuperclass();
        }

        this.onlyIf(new Spec()
        {
            @Override
            public boolean isSatisfiedBy(Object obj)
            {
                Task task = (Task) obj;
                
                if (!doesCache())
                    return true;

                if (cachedList.isEmpty())
                    return true;

                for (Annotated field : cachedList)
                {

                    try
                    {
                        File file = getProject().file(field.getValue(task));

                        // not there? do the task.
                        if (!file.exists())
                        {
                            return true;
                        }

                        File hashFile = getHashFile(file);
                        if (!hashFile.exists())
                        {
                            file.delete(); // Kill the output file if the hash doesn't exist, else gradle will think it's up-to-date
                            return true;
                        }

                        String foundMD5 = Files.toString(getHashFile(file), Charset.defaultCharset());
                        String calcMD5 = getHashes(field, inputList, task);

                        if (!calcMD5.equals(foundMD5))
                        {
                            getProject().getLogger().info(" Corrupted Cache!");
                            getProject().getLogger().info("Checksums found: " + foundMD5);
                            getProject().getLogger().info("Checksums calculated: " + calcMD5);
                            file.delete();
                            getHashFile(file).delete();
                            return true;
                        }
                        
                        getProject().getLogger().debug("Checksums found: " + foundMD5);
                        getProject().getLogger().debug("Checksums calculated: " + calcMD5);

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
        });
    }

    private void addCachedOutput(final Annotated annot)
    {
        cachedList.add(annot);

        this.doLast(new Action<Task>()
        {
            @Override
            public void execute(Task task)
            {
                if (!doesCache())
                    return;

                try
                {
                    File outFile = getProject().file(annot.getValue(task));
                    if (outFile.exists())
                    {
                        File hashFile = getHashFile(outFile);
                        Files.write(getHashes(annot, inputList, task), hashFile, Charset.defaultCharset());
                    }
                }
                // error? spit it and do the task.
                catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
        });
    }

    private File getHashFile(File file)
    {
        if (file.isDirectory())
            return new File(file, ".cache");
        else
            return new File(file.getParentFile(), file.getName() + ".md5");
    }

    @SuppressWarnings("rawtypes")
    private String getHashes(Annotated output, List<Annotated> inputs, Object instance) throws NoSuchFieldException, IllegalAccessException, NoSuchAlgorithmException, NoSuchMethodException, SecurityException, IllegalArgumentException, InvocationTargetException
    {
        LinkedList<String> hashes = new LinkedList<String>();

        hashes.addAll(Constants.hashAll(getProject().file(output.getValue(instance))));

        for (Annotated input : inputs)
        {
            AnnotatedElement m = input.getElement();
            Object val = input.getValue(instance);
            
            if (val == null && m.isAnnotationPresent(Optional.class))
            {
                hashes.add("null");
            }
            else if (m.isAnnotationPresent(InputFile.class))
            {
                hashes.add(Constants.hash(getProject().file(input.getValue(instance))));
                getLogger().debug(Constants.hash(getProject().file(input.getValue(instance))) + " " + input.getValue(instance));
            }
            else if (m.isAnnotationPresent(InputDirectory.class))
            {
                File dir = (File) input.getValue(instance);
                hashes.addAll(Constants.hashAll(dir));
            }
            else if (m.isAnnotationPresent(InputFiles.class))
            {
                FileCollection files = (FileCollection) input.getValue(instance);
                for (File file : files.getFiles())
                {
                    String hash = Constants.hash(file);
                    hashes.add(hash);
                    getLogger().debug(hash + " " + input.getValue(instance));
                }
            }
            else
            // just @Input
            {
                Object obj = input.getValue(instance);

                while (obj instanceof Closure)
                    obj = ((Closure) obj).call();

                if (obj instanceof String)
                {
                    hashes.add(Constants.hash((String) obj));
                    getLogger().debug(Constants.hash((String) obj) + " " + (String) obj);
                }
                else if (obj instanceof File)
                {
                    File file = (File) obj;
                    if (file.isDirectory())
                    {
                        List<File> files = Arrays.asList(file.listFiles());
                        Collections.sort(files);
                        for (File i : files)
                        {
                            hashes.add(Constants.hash(i));
                            getLogger().debug(Constants.hash(i) + " " + i);
                        }
                    }
                    else
                    {
                        hashes.add(Constants.hash(file));
                        getLogger().debug(Constants.hash(file) + " " + file);
                    }
                }
                else
                {
                    hashes.add(Constants.hash(obj.toString()));
                }
                    
            }
        }

        return Joiner.on(Constants.NEWLINE).join(hashes);
    }

    @Target( { ElementType.FIELD, ElementType.METHOD })
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cached
    {
    }

    @Target(ElementType.FIELD)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Excluded
    {
    }

    private static class Annotated
    {
        private final Class<? extends Task> taskClass;
        private final String                symbolName;
        private final boolean               isMethod;

        private Annotated(Class<? extends Task> taskClass, String symbolName, boolean isMethod)
        {
            this.taskClass = taskClass;
            this.symbolName = symbolName;
            this.isMethod = isMethod;
        }
        
        private Annotated(Class<? extends Task> taskClass, String fieldName)
        {
            this.taskClass = taskClass;
            this.symbolName = fieldName;
            isMethod = false;
        }
        
        private AnnotatedElement getElement() throws NoSuchMethodException, SecurityException, NoSuchFieldException
        {
            if (isMethod)
                return taskClass.getDeclaredMethod(symbolName);
            else
                return taskClass.getDeclaredField(symbolName);
        }

        protected Object getValue(Object instance) throws NoSuchMethodException, SecurityException, IllegalArgumentException, IllegalAccessException, NoSuchFieldException, InvocationTargetException
        {
            Method method;
            
            if (isMethod)
                method = taskClass.getDeclaredMethod(symbolName);
            else
            {
                // finds the getter, and uses that if possible.
                Field f = taskClass.getDeclaredField(symbolName);
                String methodName = f.getType().equals(boolean.class) ? "is" : "get";
                
                char[] name = symbolName.toCharArray();
                name[0] = Character.toUpperCase(name[0]);
                methodName += new String(name);
                
                try {
                    method = taskClass.getMethod(methodName, new Class[0]);
                }
                catch (NoSuchMethodException e)
                {
                    // method not found. Grab the field via reflection
                    f.setAccessible(true);
                    return f.get(instance);
                }
            }
            
            return method.invoke(instance, new Object[0]);
        }
    }
    
    protected boolean defaultCache()
    {
        return true;
    }

    public boolean doesCache()
    {
        if (cacheSet)
            return doesCache;
        else
            return defaultCache();
    }

    public void setDoesCache(boolean cacheStuff)
    {
        this.cacheSet = true;
        this.doesCache = cacheStuff;
    }
}
