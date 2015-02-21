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
    private DelayedString assetIndex;
    @Input
    private DelayedFile assetsDir;
    @Input
    private DelayedFile nativesDir;
    @Input
    private DelayedFile srgDir;
    @Input
    private DelayedFile csvDir;
    @Input
    private DelayedString version;
    @Input
    private DelayedString clientTweaker;
    @Input
    private DelayedString serverTweaker;
    @Input
    private DelayedString clientBounce;
    @Input
    private DelayedString serverBounce;

    @Input
    private String clientResource = getResource("GradleStart.java");
    @Input
    private String serverResource = getResource("GradleStartServer.java");
    @Input
    private String commonResource = getResource("net/minecraftforge/gradle/GradleStartCommon.java");

    @OutputDirectory
    @Cached
    private DelayedFile startOut;

    @SuppressWarnings({ "unchecked", "rawtypes"})
    public CreateStartTask() throws IOException
    {
        super();

        final File clientJava = new File(getTemporaryDir(), "GradleStart.java");
        final File serverJava = new File(getTemporaryDir(), "GradleStartServer.java");
        final File commonJava = new File(getTemporaryDir(), "net/minecraftforge/gradle/GradleStartCommon.java");

        // configure compilation
        this.source(getReplaceClosure(clientResource, clientJava), getReplaceClosure(serverResource, serverJava),getReplaceClosure(commonResource, commonJava));
        this.setClasspath(getProject().getConfigurations().getByName(UserConstants.CONFIG_DEPS));
        this.setDestinationDir(getTemporaryDir());
        this.setSourceCompatibility("1.6");
        this.setTargetCompatibility("1.6");
        this.getOptions().setEncoding("UTF-8");
        this.getOptions().setWarnings(false);

        // copy the stuff to the cache
        this.doLast(new Action() {

            @Override
            public void execute(Object arg0)
            {
                try
                {
                    for (File f : getProject().fileTree(getTemporaryDir()))
                    {
                        if (f.isFile() && f.getName().endsWith(".class"))
                        {
                            if (f.getParentFile().equals(getTemporaryDir()))
                            {
                                Files.copy(f, new File(getStartOut(), f.getName()));
                            }
                            else
                            {
                                File out = new File(getStartOut(), f.getAbsolutePath().substring(getTemporaryDir().getAbsolutePath().length()));
                                out.getParentFile().mkdirs();
                                Files.copy(f, out);
                            }
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
    
    @SuppressWarnings("serial")
    private Closure<File> getReplaceClosure(final String resource, final File out)
    {
        return new Closure<File>(null, null) {

            @Override
            public File call()
            {
                try
                {
                    replaceResource(resource, out);
                    return out;
                }
                catch (IOException e)
                {
                    Throwables.propagate(e);
                }
                return null;
            }
        };
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
        resource = resource.replace("@@MCVERSION@@",     getVersion());
        resource = resource.replace("@@ASSETINDEX@@",    getAssetIndex());
        resource = resource.replace("@@ASSETSDIR@@",     getAssetsDir().replace('\\', '/'));
        resource = resource.replace("@@NATIVESDIR@@",    getNativesDir().replace('\\', '/'));
        resource = resource.replace("@@SRGDIR@@",        getSrgDir().replace('\\', '/'));
        resource = resource.replace("@@CSVDIR@@",        getCsvDir().replace('\\', '/'));
        resource = resource.replace("@@CLIENTTWEAKER@@", getClientTweaker());
        resource = resource.replace("@@SERVERTWEAKER@@", getServerTweaker());
        resource = resource.replace("@@BOUNCERCLIENT@@", getClientBounce());
        resource = resource.replace("@@BOUNCERSERVER@@", getServerBounce());

        // because there are different versions of authlib
        if (!"1.7.2".equals(getVersion()))
        {
            resource = resource.replace("//@@USERTYPE@@", "argMap.put(\"userType\", auth.getUserType().getName());");
            resource = resource.replace("//@@USERPROP@@", "argMap.put(\"userPropertiesMap\", new GsonBuilder().registerTypeAdapter(com.mojang.authlib.properties.PropertyMap.class, new com.mojang.authlib.properties.PropertyMap.Serializer()).create().toJson(auth.getUserProperties()));");
        }
        else
        {
            resource = resource.replace("//@@USERPROP@@", "argMap.put(\"userProperties\", (new Gson()).toJson(auth.getUserProperties()));");
        }

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
                            getProject().getLogger().warn(" Corrupted Cache!");
                            getProject().getLogger().debug("Checksums found: " + foundMD5);
                            getProject().getLogger().debug("Checksums calculated: " + calcMD5);
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
    
    public String getNativesDir() throws IOException
    {
        return nativesDir.call().getCanonicalPath();
    }

    public void setNativesDir(DelayedFile nativesDir)
    {
        this.nativesDir = nativesDir;
    }
    
    public String getSrgDir() throws IOException
    {
        return srgDir.call().getCanonicalPath();
    }

    public void setSrgDir(DelayedFile srgDir)
    {
        this.srgDir = srgDir;
    }
    
    public String getCsvDir() throws IOException
    {
        return csvDir.call().getCanonicalPath();
    }

    public void setCsvDir(DelayedFile csvDir)
    {
        this.csvDir = csvDir;
    }

    public String getVersion()
    {
        return version.call();
    }

    public void setVersion(DelayedString version)
    {
        this.version = version;
    }

    public String getClientTweaker()
    {
        return clientTweaker.call();
    }

    public void setClientTweaker(DelayedString tweaker)
    {
        this.clientTweaker = tweaker;
    }
    
    public String getServerTweaker()
    {
        return serverTweaker.call();
    }

    public void setServerTweaker(DelayedString tweaker)
    {
        this.serverTweaker = tweaker;
    }

    public String getClientBounce()
    {
        return clientBounce.call();
    }

    public void setClientBounce(DelayedString version)
    {
        this.clientBounce = version;
    }

    public String getServerBounce()
    {
        return serverBounce.call();
    }

    public void setServerBounce(DelayedString version)
    {
        this.serverBounce = version;
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
}
