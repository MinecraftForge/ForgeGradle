package net.minecraftforge.gradle.tasks.user.reobf;

import groovy.lang.Closure;

import java.io.File;

import joptsimple.internal.Strings;
import net.minecraftforge.gradle.common.BasePlugin;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import com.google.common.io.Files;

public class ArtifactSpec
{
    private Object  baseName;
    private Object  appendix;
    private Object  version;
    private Object  classifier;
    private Object  extension;
    private Object  archiveName;
    private Object  classpath;

    private boolean archiveSet = false;

    public ArtifactSpec()
    {
    }

    public ArtifactSpec(File file)
    {
        archiveName = file.getName();
        extension = Files.getFileExtension(file.getName());
    }

    public ArtifactSpec(String file)
    {
        archiveName = file;
        extension = Files.getFileExtension(file);
    }

    public ArtifactSpec(PublishArtifact artifact)
    {
        baseName = artifact.getName();
        classifier = artifact.getClassifier();
        extension = artifact.getExtension();
    }

    @SuppressWarnings({ "serial", "rawtypes" })
    public ArtifactSpec(final AbstractArchiveTask task)
    {
        baseName = new Closure(null) { public Object call() {return task.getBaseName();} };
        appendix = new Closure(null) { public Object call() {return task.getAppendix();} };
        version = new Closure(null) { public Object call() {return task.getVersion();} };
        classifier = new Closure(null) { public Object call() {return task.getClassifier();} };
        extension = new Closure(null) { public Object call() {return task.getExtension();} };
        classpath = new Closure(null) { public Object call() {return task.getSource();} };
    }

    public Object getBaseName()
    {
        return baseName;
    }

    public void setBaseName(Object baseName)
    {
        this.baseName = baseName;
    }

    public Object getAppendix()
    {
        return appendix;
    }

    public void setAppendix(Object appendix)
    {
        this.appendix = appendix;
    }

    public Object getVersion()
    {
        return version;
    }

    public void setVersion(Object version)
    {
        this.version = version;
    }

    public Object getClassifier()
    {
        return classifier;
    }

    public void setClassifier(Object classifier)
    {
        this.classifier = classifier;
    }

    public Object getExtension()
    {
        return extension;
    }

    public void setExtension(Object extension)
    {
        this.extension = extension;
    }

    public Object getClasspath()
    {
        if (classpath == null)
            classpath = BasePlugin.project.files((Object)new String[] {});
        return classpath;
    }

    public void setClasspath(Object classpath)
    {
        this.classpath = classpath;
    }

    public boolean isArchiveSet()
    {
        return archiveSet;
    }

    public void setArchiveSet(boolean archiveSet)
    {
        this.archiveSet = archiveSet;
    }

    public Object getArchiveName()
    {
        return archiveName;
    }
    
    public void setArchiveName(Object archiveName)
    {
        this.archiveName = archiveName;
        archiveSet = true;
    }

    protected void resolve()
    {

        // resolve fields
        baseName = resolve(baseName, true);
        appendix = resolve(appendix, true);
        version = resolve(version, true);
        classifier = resolve(classifier, true);
        extension = resolve(extension, true);

        // resolve classpath
        if (classpath != null)
            classpath = BasePlugin.project.files(classpath);

        // skip if its already been set by the user.
        if (archiveSet)
            return;

        StringBuilder builder = new StringBuilder();
        builder.append(baseName);

        if (!Strings.isNullOrEmpty((String) appendix))
        {
            builder.append('-');
            builder.append(appendix);
        }

        if (!Strings.isNullOrEmpty((String) version))
        {
            builder.append('-');
            builder.append(version);
        }

        if (!Strings.isNullOrEmpty((String) classifier))
        {
            builder.append('-');
            builder.append(classifier);
        }

        builder.append('.');
        builder.append(extension);

        archiveName = builder.toString();
    }

    @SuppressWarnings("unchecked")
    private Object resolve(Object obj, boolean isString)
    {
        if (obj instanceof Closure)
            obj = ((Closure<Object>) obj).call();
        
        if (obj == null)
            return null;

        if (isString)
            obj = obj.toString();

        return obj;
    }
}
