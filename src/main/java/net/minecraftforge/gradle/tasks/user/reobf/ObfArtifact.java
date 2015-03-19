package net.minecraftforge.gradle.tasks.user.reobf;

import groovy.lang.Closure;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedThingy;
import net.minecraftforge.gradle.extrastuff.ReobfExceptor;
import net.minecraftforge.gradle.tasks.dev.ObfuscateTask;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import COM.rl.NameProvider;
import COM.rl.obf.RetroGuardImpl;

import com.google.common.base.Joiner;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ObfArtifact extends AbstractPublishArtifact
{
    Object                toObfArtifact;

    private String                name;
    private String                extension;
    private String                classifier;
    private Date                  date;
    private File                  file;
    private FileCollection        classpath;

    @SuppressWarnings("unused")
    private String                type;

    private final Closure<Object> toObfGenerator;
    private final ReobfTask       caller;

    final ArtifactSpec            spec;

    /**
     * Creates an obfuscated artifact for the given public artifact.
     * <p>
     * The file to obfuscate will be the file of the given artifact and the name of this obfuscated artifact will default to the name of the given artifact to obfuscate.
     * </p>
     * <p>
     * The artifact to obfuscate may change after being used as the source.
     * </p>
     * @param toObf The artifact that is to be obfuscated
     * @param artifactSpec The specification of how the obfuscated artifact is to be named
     * @param task The task(s) that will  reobfuscate this jar.
     */
    public ObfArtifact(AbstractArchiveTask toObf, ArtifactSpec artifactSpec, ReobfTask task)
    {
        this(new DelayedThingy(toObf), artifactSpec, task);
        this.toObfArtifact = (PublishArtifact) toObf;
    }

    /**
     * Creates an obfuscated artifact for the given public artifact.
     * <p>
     * The file to obfuscate will be the file of the given artifact and the name of this obfuscated artifact will default to the name of the given artifact to obfuscate.
     * </p>
     * <p>
     * The artifact to obfuscate may change after being used as the source.
     * </p>
     * @param toObf The artifact that is to be obfuscated
     * @param artifactSpec The specification of how the obfuscated artifact is to be named
     * @param task The task(s) that will  reobfuscate this jar.
     */
    public ObfArtifact(PublishArtifact toObf, ArtifactSpec artifactSpec, ReobfTask task)
    {
        this(new DelayedThingy(toObf), artifactSpec, task);
        this.toObfArtifact = toObf;
    }

    /**
     * Creates an obfuscated artifact for the given file.
     * @param toObf The file that is to be obfuscated
     * @param artifactSpec The specification of how the obfuscated artifact is to be named
     * @param task The task(s) that will  reobfuscate this jar.
     */
    public ObfArtifact(File toObf, ArtifactSpec artifactSpec, ReobfTask task)
    {
        this(new DelayedThingy(toObf), artifactSpec, task);
        this.toObfArtifact = toObf;
    }

    /**
     * Creates an obfuscated artifact for the file returned by the {@code toObf} closure.
     * <p>
     * The closures will be evaluated on demand whenever the value is needed (e.g. at generation time)
     * </p>
     * @param toObf A closure that produces a File for the object to obfuscate (non File return values will be used as the path to the file)
     * @param outputSpec The specification of artifact to outputted
     * @param task The task(s) that will reobfuscate this jar.
     */
    public ObfArtifact(Closure<Object> toObf, ArtifactSpec outputSpec, ReobfTask task)
    {
        super(task);
        this.caller = task;
        toObfGenerator = toObf;
        this.spec = outputSpec;
    }

    /**
     * The file that is to be obfuscated.
     * @return The file. May be {@code null} if unknown at this time.
     */
    public File getToObf()
    {
        Object toObf = null;
        if (toObfGenerator != null)
            toObf = toObfGenerator.call();

        if (toObf == null)
            return null;
        else if (toObf instanceof File)
            return (File) toObf;
        else
            return new File(toObf.toString());
    }

    /**
     * The name of the obfuscated artifact.
     * <p>
     * Defaults to the name of the obfuscated artifact {@link #getFile() file}.
     * @return The name. May be {@code null} if unknown at this time.
     */
    public String getName()
    {
        if (name != null)
            return name;
        else if (toObfArtifact != null)
            return ((File) toObfArtifact).getName();
        else if (spec.getBaseName() != null)
            return spec.getBaseName().toString();
        else
            return getFile() == null ? null : getFile().getName();
    }

    /**
     * The name of the obfuscated artifact.
     * <p>
     * Defaults to the name of the obfuscated artifact {@link #getFile() file}.
     * @return The name. May be {@code null} if unknown at this time.
     */
    public FileCollection getClasspath()
    {
        if (classpath != null)
            return classpath;
        else if (spec.getClasspath() != null)
            return (FileCollection) spec.getClasspath();
        else
            return null;
    }

    /**
     * The extension of the obfuscated artifact.
     * <p>
     * Defaults to '.jar'.
     * </p>
     * @return The extension. May be {@code null} if unknown at this time.
     */
    public String getExtension()
    {
        if (extension != null)
            return extension;
        else if (toObfArtifact != null)
            return ((PublishArtifact) toObfArtifact).getExtension();
        else if (spec.getExtension() != null)
            return spec.getExtension().toString();
        else
            return Files.getFileExtension(getFile() == null ? null : getFile().getName());
    }

    public String getType()
    {
        return getExtension();
    }

    public void setType(String type)
    {
        this.type = type;
    }

    /**
     * The classifier of the obfuscated artifact.
     * <p>
     * Defaults to the classifier of the source artifact (if obfuscating an artifact) or the given classifier at construction (if given).
     * </p>
     * @return The classifier. May be {@code null} if unknown at this time.
     */
    public String getClassifier()
    {
        if (classifier != null)
            return classifier;
        else if (toObfArtifact != null)
            return ((PublishArtifact) toObfArtifact).getClassifier();
        else if (spec.getClassifier() != null)
            return spec.getClassifier().toString();
        else
            return null;
    }

    /**
     * The date of the obfuscated artifact.
     * <p>
     * Defaults to the last modified time of the {@link #getFile() obfuscated file} (if exists)
     * </p>
     * @return The date of the obfuscation. May be {@code null} if unknown at this time.
     */
    public Date getDate()
    {
        if (date == null)
        {
            File file = getFile();
            if (file == null)
                return null;
            else
            {
                long modified = file.lastModified();
                if (modified == 0)
                    return null;
                else
                    new Date(modified);
            }
        }

        return date;
    }

    /**
     * The file for the obfuscated artifact, which may not yet exist.
     * <p>
     * Defaults to a the {@link #getToObf()} () file to obfuscate}
     * </p>
     * @return The obfuscated file. May be {@code null} if unknown at this time.
     */
    public File getFile()
    {
        if (file == null)
        {
            File input = getToObf();

            spec.resolve();
            this.name = spec.getArchiveName().toString();
            this.classifier = spec.getClassifier().toString();
            this.extension = spec.getExtension().toString();
            this.classpath = (FileCollection) spec.getClasspath();

            file = new File(input.getParentFile(), spec.getArchiveName().toString());
            return file;
        }
        else
        {
            return file;
        }
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public void setExtension(String extension)
    {
        this.extension = extension;
    }

    public void setClassifier(String classifier)
    {
        this.classifier = classifier;
    }

    public void setDate(Date date)
    {
        this.date = date;
    }

    public void setFile(File file)
    {
        this.file = file;
    }

    public void setClasspath(FileCollection classpath)
    {
        this.classpath = classpath;
    }

    /**
     * Obfuscates the file
     * @throws IOException
     * @throws org.gradle.api.InvalidUserDataException if the there is insufficient information available to generate the signature.
     */
    void generate(ReobfExceptor exc, File defaultSrg, File extraSrg, FileCollection extraSrgFiles) throws Exception
    {
        File toObf = getToObf();
        if (toObf == null)
        {
            throw new InvalidUserDataException("Unable to obfuscate as the file to obfuscate has not been specified");
        }

        // ready artifacts
        File output = getFile();
        File toObfTemp = File.createTempFile("toObf", ".jar", caller.getTemporaryDir());
        File toInjectTemp = File.createTempFile("toInject", ".jar", caller.getTemporaryDir());
        Files.copy(toObf, toObfTemp);

        // ready Srg
        File srg = (File) (spec.srg == null ? defaultSrg : spec.srg);
        boolean isTempSrg = false;
        if (exc != null && srg != defaultSrg) // defualt SRG is already passed through this.
        {
            File tempSrg = File.createTempFile("reobf", ".srg", caller.getTemporaryDir());
            isTempSrg = true;

            exc.buildSrg(srg, tempSrg);
            srg = tempSrg;

        }

        // obfuscate!
        if (caller.getUseRetroGuard())
            applyRetroGuard(toObfTemp, toInjectTemp, srg, extraSrg, extraSrgFiles);
        else
            applySpecialSource(toObfTemp, toInjectTemp, srg, extraSrg, extraSrgFiles);

        // inject mcVersion!
        if (caller.getMcVersion().startsWith("1.8"))
        {
            new McVersionTransformer(toInjectTemp, output).transform(caller.getMcVersion());
        }
        else
        {
            Files.copy(toInjectTemp, output);
        }
        
        // delete temporary files
        toObfTemp.delete();
        toInjectTemp.delete();
        if (isTempSrg)
            srg.delete();

        System.gc(); // clean anything out.. I hope..
    }

    private void applySpecialSource(File input, File output, File srg, File extraSrg, FileCollection extraSrgFiles) throws IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(srg);
        mapping.loadMappings(extraSrg);
        
        for (File f : extraSrgFiles)
        {
            mapping.loadMappings(f);
        }

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping);

        // load jar
        Jar inputJar = Jar.init(input);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(inputJar));
        if (classpath != null)
            inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(ObfuscateTask.toUrls(classpath))));
        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        // remap jar
        remapper.remapJar(inputJar, output);
    }

    private void applyRetroGuard(File input, File output, File srg, File extraSrg, FileCollection extraSrgFiles) throws Exception
    {
        File cfg =    new File(caller.getTemporaryDir(), "retroguard.cfg");
        File log =    new File(caller.getTemporaryDir(), "retroguard.log");
        File script = new File(caller.getTemporaryDir(), "retroguard.script");
        File packedJar = new File(caller.getTemporaryDir(), "rgPackaged.jar");
        File outPackedJar = new File(caller.getTemporaryDir(), "rgOutPackaged.jar");

        HashSet<String> modFiles = Sets.newHashSet();
        { // pack in classPath
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(packedJar));
            ZipEntry entry = null;

            // pack input jar
            ZipInputStream in = new ZipInputStream(new FileInputStream(input));
            while ((entry = in.getNextEntry()) != null)
            {
                modFiles.add(entry.getName());
                out.putNextEntry(entry);
                ByteStreams.copy(in, out);
                in.closeEntry();
                out.closeEntry();
            }
            in.close();

            HashSet<String> antiDuplicate = Sets.newHashSet();
            for (File f : classpath)
            {
                if (f.isDirectory())
                {
                    LinkedList<File> dirStack = new LinkedList<File>();
                    dirStack.push(f);
                    String root = f.getCanonicalPath();

                    while (!dirStack.isEmpty())
                    {
                        File dir = dirStack.pop();
                        for (File file : dir.listFiles())
                        {
                            if (f.isDirectory())
                            {
                                dirStack.push(file);
                            }
                            else
                            {
                                String relPath = file.getCanonicalPath().replace(root, "");
                                if (antiDuplicate.contains(relPath) || modFiles.contains(relPath))
                                    continue;
                                FileInputStream inStream = new FileInputStream(f);
                                antiDuplicate.add(relPath);
                                out.putNextEntry(new ZipEntry(relPath));
                                ByteStreams.copy(inStream, out);
                                out.closeEntry();
                                inStream.close();
                            }
                        }
                    }
                }
                else if (f.getName().endsWith("jar") || f.getName().endsWith("zip"))
                {
                    in = new ZipInputStream(new FileInputStream(f));
                    while ((entry = in.getNextEntry()) != null)
                    {
                        if (antiDuplicate.contains(entry.getName()) || modFiles.contains(entry.getName()))
                            continue;
                        antiDuplicate.add(entry.getName());
                        out.putNextEntry(new ZipEntry(entry.getName()));
                        ByteStreams.copy(in, out);
                        out.closeEntry();
                    }
                    in.close();
                }
            }

            out.close();
        }

        generateRgConfig(cfg, script, srg, extraSrg, extraSrgFiles);

        String[] args = new String[] {
                "-notch",
                cfg.getCanonicalPath()
        };

        // load in classpath... ewww
        @SuppressWarnings("unused")
        ClassLoader loader = BasePlugin.class.getClassLoader(); // dunno.. maybe this will load the classes??
        if (classpath != null)
        {
            loader = new URLClassLoader(ObfuscateTask.toUrls(classpath), BasePlugin.class.getClassLoader());
        }

        // the name provider
//        Class clazz = getClass().forName("COM.rl.NameProvider", true, loader);
//        clazz.getMethod("parseCommandLine", String[].class).invoke(null, new Object[] { args });
        NameProvider.parseCommandLine(args);

        // actual retroguard
//        clazz = getClass().forName("COM.rl.obf.RetroGuardImpl", true, loader);
//        clazz.getMethod("obfuscate", File.class, File.class, File.class, File.class).invoke(null, packedJar, outPackedJar, script, log);
        RetroGuardImpl.obfuscate(packedJar, outPackedJar, script, log);

        loader = null; // if we are lucky.. this will be dropped...

        // unpack jar.
        {
            ZipOutputStream out = new ZipOutputStream(new FileOutputStream(output));
            ZipEntry entry = null;

            // pack input jar
            ZipInputStream in = new ZipInputStream(new FileInputStream(outPackedJar));
            while ((entry = in.getNextEntry()) != null)
            {
                if (modFiles.contains(entry.getName()))
                {
                    out.putNextEntry(entry);
                    ByteStreams.copy(in, out);
                    in.closeEntry();
                    out.closeEntry();
                }
            }
            in.close();
            out.close();
        }
    }

    private void generateRgConfig(File config, File script, File srg, File extraSrg, FileCollection extraSrgFiles) throws IOException
    {
        // the config
        ArrayList<String> configOut = new ArrayList<String>(10);
        
        configOut.add("reob = "+srg.getCanonicalPath());
        configOut.add("reob = "+extraSrg.getCanonicalPath()); // because it should work
        
        for (File f : extraSrgFiles)
        {
            configOut.add("reob = "+f.getCanonicalPath());
        }

        configOut.add("script = "+script.getCanonicalPath());
        configOut.add("verbose = 0");
        configOut.add("quiet = 1");
        configOut.add("fullmap = 0");
        configOut.add("startindex = 0");

        Files.write(Joiner.on(Constants.NEWLINE).join(configOut), config, Charset.defaultCharset());

        // the script.
        String[] lines = new String[] {
                ".option Application",
                ".option Applet",
                ".option Repackage",
                ".option Annotations",
                ".option MapClassString",
                ".attribute LineNumberTable",
                ".attribute EnclosingMethod",
                ".attribute Deprecated"
        };

        Files.write(Joiner.on(Constants.NEWLINE).join(lines), script, Charset.defaultCharset());
    }
}
