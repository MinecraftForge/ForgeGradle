package net.minecraftforge.gradle.tasks.patcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.tasks.TaskAction;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;

import com.google.common.base.Splitter;
import com.google.common.io.Resources;

public class SubprojectCall extends DefaultTask
{
    Object projectDir;
    Object callLine;
    
    @TaskAction
    public void doTask() throws IOException
    {
        // extract custom initscript
        File initscript = new File(getTemporaryDir(), "subprojectCallInitScript.gradle");
        {
            OutputStream os = new FileOutputStream(initscript);
            Resources.copy(Resources.getResource(SubprojectCall.class, "subprojectInitScript"), os);
            os.close();
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
        args.add("-I"+initscript.getCanonicalPath());
        
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
}
