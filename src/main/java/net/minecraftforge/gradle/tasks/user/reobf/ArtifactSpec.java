package net.minecraftforge.gradle.tasks.user.reobf;

import groovy.lang.Closure;

import java.io.File;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.Project;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import com.google.common.base.Strings;
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
    
    protected Object  srg = null;

    private Project project;

    private boolean archiveSet = false;

    public ArtifactSpec(Project proj)
    {
        project = proj;
    }

    public ArtifactSpec(File file, Project proj)
    {
        archiveName = file.getName();
        extension = Files.getFileExtension(file.getName());
        project = proj;
    }

    public ArtifactSpec(String file, Project proj)
    {
        archiveName = file;
        extension = Files.getFileExtension(file);
        project = proj;
    }

    public ArtifactSpec(PublishArtifact artifact, Project proj)
    {
        baseName = artifact.getName();
        classifier = artifact.getClassifier();
        extension = artifact.getExtension();
        project = proj;
    }

    @SuppressWarnings({ "serial", "rawtypes" })
    public ArtifactSpec(final AbstractArchiveTask task)
    {
        project = task.getProject();
        baseName = new Closure(null) { public Object call() {return task.getBaseName();} };
        appendix = new Closure(null) { public Object call() {return task.getAppendix();} };
        version = new Closure(null) { public Object call() {return task.getVersion();} };
        classifier = new Closure(null) { public Object call() {return task.getClassifier();} };
        extension = new Closure(null) { public Object call() {return task.getExtension();} };
        archiveName = new Closure(null) { public Object call() {return task.getArchiveName();} };
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
            classpath = project.files((Object)new String[] {});
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
    
    public Object getSrg()
    {
        return srg;
    }
    
    public void setSrg(Object srg)
    {
        this.srg = srg;
    }
    
    /**
     * sets it to SRG names.
     */
    public void setSrgSrg()
    {
        this.srg = new DelayedFile(project, UserConstants.REOBF_SRG, ((UserExtension)project.getExtensions().getByName(Constants.EXT_NAME_MC)).plugin);
    }
    
    /**
     * Sets it to noth names.
     */
    public void setSrgMcp()
    {
        this.srg = new DelayedFile(project, UserConstants.REOBF_NOTCH_SRG, ((UserExtension)project.getExtensions().getByName(Constants.EXT_NAME_MC)).plugin);
    }

    protected void resolve()
    {

        // resolve fields
        baseName = resolveString(baseName);
        appendix = resolveString(appendix);
        version = resolveString(version);
        classifier = resolveString(classifier);
        extension = resolveString(extension);
        srg = resolveFile(srg);

        // resolve classpath
        if (classpath != null)
            classpath = project.files(classpath);

        // skip if its already been set by the user.
        if (archiveSet && archiveName != null)
            return;
        else
        {
            archiveName = resolveString(archiveName);
            if (!Strings.isNullOrEmpty((String)archiveName)) // the jar set it.. we dont need to reset this stuff.
            {
                return;
            }
        }

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
    private Object resolveString(Object obj)
    {
        if (obj == null)
            return "";
        else if (obj instanceof Closure)
            return resolveString(((Closure<Object>) obj).call());
        else
            return obj.toString();
    }
    
    private Object resolveFile(Object obj)
    {
        if (obj == null)
            return null;
        else
            return project.file(obj);
    }
}
