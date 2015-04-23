package net.minecraftforge.gradle.tasks.patcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.Resources;

public class SubprojectCall extends DefaultTask
{
    private Object projectDir;
    private Object callLine;
    private final List<URL> initResources = Lists.newArrayList();
    private final Map<String, Object> replacements = Maps.newHashMap();
    
    @TaskAction
    public void doTask() throws IOException
    {
        // extract custom initscript
        File initscript = new File(getTemporaryDir(), "subprojectLogging.gradle");
        {
            OutputStream os = new FileOutputStream(initscript);
            Resources.copy(Resources.getResource(SubprojectCall.class, "subprojectInitScript"), os);
            os.close();
        }
        
        // resolve replacements
        for (Entry<String, Object> entry : replacements.entrySet())
        {
            replacements.put(entry.getKey(), Constants.resolveString(entry.getValue()));
        }
        
        // extract extra initscripts
        List<File> initscripts = Lists.newArrayListWithCapacity(initResources.size());
        for (int i = 0; i < initResources.size(); i++)
        {
            File file = new File(getTemporaryDir(), "initscript"+i);
            String thing = Resources.toString(initResources.get(i), Constants.CHARSET);
            
            for (Entry<String, Object> entry : replacements.entrySet())
            {
                thing = thing.replace(entry.getKey(), (String)entry.getValue());
            }
            
            Files.write(thing, file, Constants.CHARSET);
            initscripts.add(file);
        }
        
        // get current Gradle instance
        Gradle gradle = getProject().getGradle();
        
        // connect to project
        ProjectConnection connection = GradleConnector.newConnector()
                .useGradleUserHomeDir(gradle.getGradleUserHomeDir())
                .useInstallation(gradle.getGradleHomeDir())
                .forProjectDirectory(getProjectDir())
                .connect();
        
        //get args
        ArrayList<String> args = new ArrayList<String>(5);
        args.addAll(Splitter.on(' ').splitToList(getCallLine()));
        args.add("-I" + initscript.getCanonicalPath());
        
        for (File f : initscripts)
        {
            args.add("-I" + f.getCanonicalPath());
        }
        
        // build
        connection.newBuild()
                .setStandardOutput(System.out)
                .setStandardInput(System.in)
                .setStandardError(System.err)
                .withArguments(args.toArray(new String[args.size()]))
                .run();
    }

    public File getProjectDir()
    {
        return getProject().file(projectDir);
    }

    public void setProjectDir(Object projectDir)
    {
        this.projectDir = projectDir;
    }

    public String getCallLine()
    {
        return Constants.resolveString(callLine);
    }

    public void setCallLine(Object callLine)
    {
        this.callLine = callLine;
    }
    
    public void addInitScript(URL url)
    {
        initResources.add(url);
    }
    
    public void addReplacement(String key, Object val)
    {
        replacements.put(key, val);
    }
}
