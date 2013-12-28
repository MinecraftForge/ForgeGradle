package net.minecraftforge.gradle.tasks.user.reobf;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedThingy;
import net.minecraftforge.gradle.extrastuff.ReobfExceptor;
import net.minecraftforge.gradle.user.UserConstants;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.collections.SimpleFileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

public class ReobfTask extends DefaultTask
{
    final private DefaultDomainObjectSet<ObfArtifact> obfOutput = new DefaultDomainObjectSet<ObfArtifact>(ObfArtifact.class);

    private boolean useRG = false;

    @InputFile
    private DelayedFile deobfFile;


    @SuppressWarnings("serial")
    public ReobfTask()
    {
        super();

        getInputs().files(new Closure<Object>(obfOutput) {
            public Object call(Object... objects)
            {
                return getFilesToObfuscate();
            }
        });

        getOutputs().files(new Closure<Object>(obfOutput) {
            public Object call(Object... objects)
            {
                return getObfuscatedFiles();
            }
        });
    }

    public void reobf(Task task, Action<ArtifactSpec> artifactSpec)
    {
        reobf(task, new ActionClosure(artifactSpec));
    }

    public void reobf(Task task, Closure<Object> artifactSpec)
    {
        if (!(task instanceof AbstractArchiveTask))
        {
            throw new InvalidUserDataException("You cannot reobfuscate tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)");
        }

        ArtifactSpec spec = new ArtifactSpec((AbstractArchiveTask) task);
        artifactSpec.call(spec);

        dependsOn(task);
        addArtifact(new ObfArtifact(new DelayedThingy(task), spec, this));
    }

    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     */
    public void reobf(Task... tasks)
    {
        for (Task task : tasks)
        {
            if (!(task instanceof AbstractArchiveTask))
            {
                throw new InvalidUserDataException("You cannot reobfuscate tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)");
            }

            dependsOn((AbstractArchiveTask) task);
            addArtifact(new ObfArtifact(new DelayedThingy(task), new ArtifactSpec(getProject()), this));
        }
    }

    public void reobf(PublishArtifact art, Action<ArtifactSpec> artifactSpec)
    {
        reobf(art, new ActionClosure(artifactSpec));
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    public void reobf(PublishArtifact publishArtifact, Closure<Object> artifactSpec)
    {
        ArtifactSpec spec = new ArtifactSpec(publishArtifact, getProject());
        artifactSpec.call(spec);

        dependsOn(publishArtifact);
        addArtifact(new ObfArtifact(publishArtifact, spec, this));
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    public void reobf(PublishArtifact... publishArtifacts)
    {
        for (PublishArtifact publishArtifact : publishArtifacts)
        {
            dependsOn(publishArtifact);
            addArtifact(new ObfArtifact(publishArtifact, new ArtifactSpec(publishArtifact, getProject()), this));
        }
    }

    public void reobf(File file, Action<ArtifactSpec> artifactSpec)
    {
        reobf(file, new ActionClosure(artifactSpec));
    }

    /**
     * Configures the task to reobf each of the given files
     */
    public void reobf(File file, Closure<Object> artifactSpec)
    {
        ArtifactSpec spec = new ArtifactSpec(file, getProject());
        artifactSpec.call(spec);

        addArtifact(new ObfArtifact(file, spec, this));
    }

    /**
     * Configures the task to reobf each of the given files
     */
    public void reobf(File... files)
    {
        for (File file : files)
        {
            addArtifact(new ObfArtifact(file, new ArtifactSpec(file, getProject()), this));
        }
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     */
    public void reobf(Configuration configuration, final Closure<Object> artifactSpec)
    {
        configuration.getAllArtifacts().all(new Action<PublishArtifact>() {
            public void execute(PublishArtifact artifact)
            {
                if (!(artifact instanceof ObfArtifact))
                {
                    reobf(artifact, artifactSpec);
                }
            }

        });

        configuration.getAllArtifacts().whenObjectRemoved(new Action<PublishArtifact>() {
            public void execute(PublishArtifact artifact)
            {
                ObfArtifact removed = null;
                for (ObfArtifact it : obfOutput)
                {
                    if (it.toObfArtifact == artifact)
                    {
                        removed = it;
                        break;
                    }
                }

                if (removed != null)
                    obfOutput.remove(removed);
            }

        });
    }

    /**
     * Configures the task to obfuscate every artifact of the given configurations
     */
    public void reobf(Configuration... configurations)
    {
        for (Configuration configuration : configurations)
        {
            configuration.getAllArtifacts().all(new Action<PublishArtifact>() {
                public void execute(PublishArtifact artifact)
                {
                    if (!(artifact instanceof ObfArtifact))
                    {
                        reobf(artifact);
                    }
                }

            });

            configuration.getAllArtifacts().whenObjectRemoved(new Action<PublishArtifact>() {
                public void execute(PublishArtifact artifact)
                {
                    ObfArtifact removed = null;
                    for (ObfArtifact it : obfOutput)
                    {
                        if (it.toObfArtifact == artifact)
                        {
                            removed = it;
                            break;
                        }
                    }

                    if (removed != null)
                        obfOutput.remove(removed);
                }

            });
        }
    }

    /**
     * Generates the signature files.
     * @throws IOException
     */
    @TaskAction
    public void generate() throws IOException
    {
        // do stuff.
        ReobfExceptor exc = new ReobfExceptor();
        exc.deobfJar = getDeobfFile();
        exc.inSrg = new DelayedFile(getProject(), UserConstants.REOBF_SRG).call();
        exc.outSrg = new File(getTemporaryDir(), "reobf.srg");
        exc.fieldCSV = new DelayedFile(getProject(), UserConstants.FIELD_CSV).call();
        exc.methodCSV = new DelayedFile(getProject(), UserConstants.METHOD_CSV).call();

        exc.doFirstThings();

        for (ObfArtifact obf : getObfuscated())
            obf.generate(exc);
    }

    private void addArtifact(ObfArtifact artifact)
    {
        obfOutput.add(artifact);
    }

    /**
     * The signatures generated by this task.
     */
    DomainObjectSet<ObfArtifact> getObfuscated()
    {
        return obfOutput;
    }

    /**
     * All of the files that will be signed by this task.
     */
    FileCollection getFilesToObfuscate()
    {
        ArrayList<File> collect = new ArrayList<File>();

        for (ObfArtifact obf : getObfuscated())
        {
            if (obf != null && obf.getToObf() != null)
                collect.add(obf.getToObf());
        }

        return new SimpleFileCollection(collect.toArray(new File[collect.size()]));
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    FileCollection getObfuscatedFiles() {
        ArrayList<File> collect = new ArrayList<File>();

        for (ObfArtifact obf : getObfuscated())
        {
            if (obf != null && obf.getFile() != null)
                collect.add(obf.getFile());
        }

        return new SimpleFileCollection(collect.toArray(new File[collect.size()]));
    }

    @SuppressWarnings({ "serial" })
    private class ActionClosure extends Closure<Object>
    {
        @SuppressWarnings("rawtypes")
        private final Action act;

        @SuppressWarnings("rawtypes")
        public ActionClosure(Action artifactSpec)
        {
            super(null);
            this.act = artifactSpec;
        }

        @SuppressWarnings("unchecked")
        public Object call(Object obj)
        {
            act.execute(obj);
            return null;
        }
    }

    public boolean getUseRetroGuard()
    {
        return useRG;
    }

    public void setUseRetroGuard(boolean useRG)
    {
        this.useRG = useRG;
    }

    public File getDeobfFile()
    {
        return deobfFile != null ? deobfFile.call() : null;
    }

    public void setDeobfFile(DelayedFile deobfFile)
    {
        this.deobfFile = deobfFile;
    }
}
