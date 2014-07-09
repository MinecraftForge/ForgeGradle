package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
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
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask.Cached;
import net.minecraftforge.gradle.user.UserConstants;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.compile.JavaCompile;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class CreateStartTask extends JavaCompile
{
    @Input
    private Closure<AssetIndex> assetsJson;
    @Input
    private DelayedString assetIndex;
    @Input
    private DelayedFile assetsDir;
    @Input
    private DelayedString version;
    @Input
    private DelayedString tweaker;
    @Input
    private DelayedString serverBounce;
    @Input
    private DelayedString clientBounce;

    @Input
    private String clientResource = getResource("GradleStart.java");
    @Input
    private String serverResource = getResource("GradleStartServer.java");

    @OutputDirectory
    @Cached
    private DelayedFile startOut;

    @SuppressWarnings({ "unchecked", "rawtypes", "serial" })
    public CreateStartTask() throws IOException
    {
        super();

        final File clientJava = new File(getTemporaryDir(), "GradleStart.java");
        final File serverJava = new File(getTemporaryDir(), "GradleStartServer.java");

        Closure clientClosure = new Closure<File>(null, null) {

            @Override
            public File call()
            {
                try
                {
                    replaceResource(clientResource, clientJava);
                    return clientJava;
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
                return null;
            }
        };
        Closure serverClosure = new Closure<File>(null, null) {

            @Override
            public File call()
            {
                try
                {
                    replaceResource(serverResource, serverJava);
                    return serverJava;
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
                return null;
            }
        };
        
        // configure compilation
        this.source(clientClosure, serverClosure);
        this.setClasspath(getProject().getConfigurations().getByName(UserConstants.CONFIG_DEPS));
        this.setDestinationDir(getTemporaryDir());
        this.setSourceCompatibility("1.6");
        this.setTargetCompatibility("1.6");
        this.getOptions().setEncoding("UTF-8");

        // copy the stuff to the cache
        this.doLast(new Action() {

            @Override
            public void execute(Object arg0)
            {
                try
                {
                    for (File f : getTemporaryDir().listFiles())
                    {
                        if (f.isFile() && f.getName().endsWith(".class"))
                        {
                            Files.copy(f, new File(getStartOut(), f.getName()));
                        }
                    }
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
            }

        });

        // do the caching stuff
        // do this last, so it adds its stuff first.
        cachedStuff();
    }

    private String getResource(String resource)
    {
        try
        {
            return Resources.toString(getClass().getClassLoader().getResource(resource), Charsets.UTF_8);
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
            return "";
        }

    }

    private void replaceResource(String resource, File out) throws IOException
    {
        String assetsDir = getAssetsDir().replace("\\", "/");
        AssetIndex json = getAssetsJson(); 
        if (json != null && json.virtual)
        {
            assetsDir += "/virtual/" + getAssetIndex();
        }
        
        resource = resource.replace("@@MCVERSION@@", getVersion());
        resource = resource.replace("@@ASSETINDEX@@", getAssetIndex());
        resource = resource.replace("@@ASSETSDIR@@", assetsDir);
        resource = resource.replace("@@TWEAKER@@", getTweaker());
        resource = resource.replace("@@BOUNCERSERVER@@", getServerBounce());
        resource = resource.replace("@@BOUNCERCLIENT@@", getClientBounce());

        // because there are different versions of authlib
        resource = resource.replace("@@USERTYPE@@", "1.7.2".equals(getVersion()) ? "" : "auth.getUserType().getName();");

        out.getParentFile().mkdirs();
        Files.write(resource, out, Charsets.UTF_8);
    }

    // START CACHING  taken from CachedTask

    private final ArrayList<Annotated> cachedList = new ArrayList<Annotated>();
    private final ArrayList<Annotated> inputList  = new ArrayList<Annotated>();

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void cachedStuff()
    {
        Class<? extends Task> clazz = this.getClass();
        while (clazz != null)
        {
            for (Field f : clazz.getDeclaredFields())
            {
                if (f.isAnnotationPresent(CachedTask.Cached.class))
                {
                    addCachedOutput(new Annotated(clazz, f.getName()));
                }

                if (f.isAnnotationPresent(InputFile.class) ||
                        f.isAnnotationPresent(InputFiles.class) ||
                        f.isAnnotationPresent(InputDirectory.class) ||
                        f.isAnnotationPresent(Input.class)
                   )
                {
                    inputList.add(new Annotated(clazz, f.getName()));
                }
            }

            for (Method m : clazz.getDeclaredMethods())
            {
                if (m.isAnnotationPresent(CachedTask.Cached.class))
                {
                    addCachedOutput(new Annotated(clazz, m.getName(), true));
                }

                if (
                        m.isAnnotationPresent(InputFile.class) ||
                        m.isAnnotationPresent(InputFiles.class) ||
                        m.isAnnotationPresent(InputDirectory.class) ||
                        m.isAnnotationPresent(Input.class)
                   )
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

                        getProject().getLogger().info("Checksums found: " + foundMD5);
                        getProject().getLogger().info("Checksums calculated: " + calcMD5);

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
                getLogger().info(Constants.hash(getProject().file(input.getValue(instance))) + " " + input.getValue(instance));
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
                    getLogger().info(hash + " " + input.getValue(instance));
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
                    getLogger().info(Constants.hash((String) obj) + " " + (String) obj);
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
                            getLogger().info(Constants.hash(i) + " " + i);
                        }
                    }
                    else
                    {
                        hashes.add(Constants.hash(file));
                        getLogger().info(Constants.hash(file) + " " + file);
                    }
                }
                else
                {
                    hashes.add(obj.toString());
                }

            }
        }

        return Joiner.on(Constants.NEWLINE).join(hashes);
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

    // END CACHING

    public String getAssetIndex()
    {
        return assetIndex.call();
    }

    public void setAssetIndex(DelayedString assetIndex)
    {
        this.assetIndex = assetIndex;
    }

    public String getAssetsDir() throws IOException
    {
        return assetsDir.call().getCanonicalPath();
    }

    public void setAssetsDir(DelayedFile assetsDir)
    {
        this.assetsDir = assetsDir;
    }

    public String getVersion()
    {
        return version.call();
    }

    public void setVersion(DelayedString version)
    {
        this.version = version;
    }

    public String getTweaker()
    {
        return tweaker.call();
    }

    public void setTweaker(DelayedString version)
    {
        this.tweaker = version;
    }

    public String getServerBounce()
    {
        return serverBounce.call();
    }

    public void setServerBounce(DelayedString version)
    {
        this.serverBounce = version;
    }

    public String getClientBounce()
    {
        return clientBounce.call();
    }

    public void setClientBounce(DelayedString version)
    {
        this.clientBounce = version;
    }

    public File getStartOut()
    {
        File dir = startOut.call();
        if (!dir.exists())
            dir.mkdirs();
        return startOut.call();
    }

    public void setStartOut(DelayedFile outputFile)
    {
        this.startOut = outputFile;
    }

    public AssetIndex getAssetsJson()
    {
        return assetsJson.call();
    }

    public void setAssetsJson(Closure<AssetIndex> assetsJson)
    {
        this.assetsJson = assetsJson;
    }
}
