package net.minecraftforge.gradle.tasks.dev;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedString;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

public class ForgeVersionReplaceTask extends DefaultTask
{
    DelayedFile outputFile;
    DelayedString replacement;

    @TaskAction
    public void doTask() throws IOException
    {
        String data = Files.readLines(getOutputFile(), Charset.defaultCharset(), new LineProcessor<String>()
        {
            StringBuilder buf = new StringBuilder();

            @Override
            public boolean processLine(String line) throws IOException
            {
                if (line.contains("public static final int") && line.contains("buildVersion"))
                {
                    buf.append(line.split("=")[0]).append("= ").append(getReplacement()).append(";\n");
                }
                else
                {
                    buf.append(line).append('\n');
                }
                return true;
            }

            @Override
            public String getResult()
            {
                return buf.toString();
            }
        });
        Files.write(data, getOutputFile(), Charset.defaultCharset());
    }

    public void setOutputFile(DelayedFile output)
    {
        this.outputFile = output;
    }

    public File getOutputFile()
    {
        return outputFile.call();
    }

    public void setReplacement(DelayedString value)
    {
        this.replacement = value;
    }

    public String getReplacement()
    {
        return replacement.call();
    }
}
