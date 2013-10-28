package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;

import net.minecraftforge.gradle.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class FMLVersionPropTask extends DefaultTask
{   
    @OutputFile
    DelayedFile outputFile;

    @TaskAction
    public void doTask() throws IOException
    {
        String[] version = ((String)getProject().getVersion()).split("-")[1].split("\\.");
        String data = 
        "fmlbuild.major.number="    + version[0] + "\n" +
        "fmlbuild.minor.number="    + version[1] + "\n" +
        "fmlbuild.revision.number=" + version[2] + "\n" +
        "fmlbuild.build.number="    + version[3] + "\n" +
        "fmlbuild.mcversion=" + new DelayedString(getProject(), Constants.MC_VERSION).call() + "\n";
        //fmlbuild.mcpversion -- Not actually used anywhere
        //fmlbuild.deobfuscation.hash -- Not actually used anywhere
        Files.write(data.getBytes(Charsets.UTF_8), getOutputFile());
    }

    public void setOutputFile(DelayedFile output)
    {
        this.outputFile = output;
    }

    public File getOutputFile()
    {
        return outputFile.call();
    }
}
