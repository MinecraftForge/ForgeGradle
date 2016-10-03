package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import joptsimple.internal.Strings;

import org.gradle.api.DefaultTask;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class CrowdinDownloadTask extends DefaultTask
{
    @Input
    private Object projectId;
    @Input
    private Object apiKey;
    @OutputDirectory
    private Object outputDir;
    
    // format these with the projectId and apiKey
    private static final String EXPORT_URL = "https://api.crowdin.net/api/project/%s/export?key=%s";
    private static final String DOWNLOAD_URL = "https://api.crowdin.net/api/project/%s/download/all.zip?key=%s";
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public CrowdinDownloadTask()
    {
        super();
        
        this.onlyIf(new Spec() {

            @Override
            public boolean isSatisfiedBy(Object arg0)
            {
                CrowdinDownloadTask task = (CrowdinDownloadTask) arg0;
                
                // no API key? skip
                if (Strings.isNullOrEmpty(task.getApiKey()))
                {
                    getLogger().lifecycle("Crowdin api key is null, skipping task.");
                    return false;
                }
                
                // offline? skip.
                if (getProject().getGradle().getStartParameter().isOffline())
                {
                    getLogger().lifecycle("Crowdin api key is null, skipping task.");
                    return false;
                }
                
                return true;
            }
            
        });
    }
    
    
    @TaskAction
    public void doTask() throws IOException
    {
        String project = getProjectId();
        String key = getApiKey();
        
        exportLocalizations(project, key);
        getLocalizations(project, key, getOutputDir());
    }
    
    private void exportLocalizations(String projectId, String key) throws IOException
    {
        getLogger().debug("Exporting crowdin localizations.");
        URL url = new URL(String.format(EXPORT_URL, projectId, key));
        
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        
        try
        {
            con.connect();
        }
        catch (Throwable e)
        {
            // just in case people dont have internet at the moment.
            throw new RuntimeException(e.getLocalizedMessage());
        }
        
        int reponse = con.getResponseCode();
        con.disconnect();
        
        if (reponse == 401)
            throw new RuntimeException("Invalid Crowdin API-Key");
    }
    
    private void getLocalizations(String projectId, String key, File output) throws IOException
    {
        getLogger().info("Downlaoding crowdin localizations.");
        URL url = new URL(String.format(DOWNLOAD_URL, projectId, key));
        
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);

        ZipInputStream stream = new ZipInputStream(con.getInputStream());
        
        ZipEntry entry;
        while ((entry = stream.getNextEntry()) != null)
        {
            if (entry.isDirectory() || entry.getSize() == 0)
            {
                continue;
            }
        
            getLogger().info("Extracting file: " + entry.getName());
            File out = new File(output, entry.getName());     
            Files.createParentDirs(out);
            Files.touch(out);
            Files.write(ByteStreams.toByteArray(stream), out);
            stream.closeEntry();
        }
        
        stream.close();
        con.disconnect();
    }
    
    
    @SuppressWarnings("rawtypes")
    public String getProjectId()
    {
        while (projectId instanceof Closure)
            projectId = ((Closure) projectId).call();
        
        return projectId.toString();
    }

    public void setProjectId(Object projectId)
    {
        this.projectId = projectId;
    }

    @SuppressWarnings("rawtypes")
    public String getApiKey()
    {
        while (apiKey instanceof Closure)
            apiKey = ((Closure) apiKey).call();
        
        return apiKey.toString();
    }

    public void setApiKey(Object apiKey)
    {
        this.apiKey = apiKey;
    }

    public File getOutputDir()
    {
        return getProject().file(outputDir);
    }

    public void setOutputDir(Object outputDir)
    {
        this.outputDir = outputDir;
    }
    
}
