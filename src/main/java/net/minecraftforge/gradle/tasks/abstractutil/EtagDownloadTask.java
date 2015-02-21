package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class EtagDownloadTask extends DefaultTask
{
    Object  url;
    Object  file;
    boolean dieWithError;

    @TaskAction
    public void doTask() throws IOException
    {
        URL url = getUrl();
        File outFile = getFile();
        File etagFile = getProject().file(getFile().getPath() + ".etag");

        // ensure folder exists
        outFile.getParentFile().mkdirs();

        String etag;
        if (etagFile.exists())
        {
            etag = Files.toString(etagFile, Charsets.UTF_8);
        }
        else
        {
            etag = "";
        }

        try
        {
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", Constants.USER_AGENT);
            con.setRequestProperty("If-None-Match", etag);

            con.connect();

            switch (con.getResponseCode())
                {
                    case 404: // file not found.... duh...
                        error("" + url + "  404'ed!");
                        break;
                    case 304: // content is the same.
                        this.setDidWork(false);
                        break;
                    case 200: // worked
                        
                        // write file
                        InputStream stream = con.getInputStream();
                        Files.write(ByteStreams.toByteArray(stream), outFile);
                        stream.close();
                        
                        // write etag
                        etag = con.getHeaderField("ETag");
                        if (!Strings.isNullOrEmpty(etag))
                        {
                            Files.write(etag, etagFile, Charsets.UTF_8);
                        }
                        
                        break;
                    default: // another code?? uh.. 
                        error("Unexpected reponse " + con.getResponseCode() + " from " + url);
                        break;
                }

            con.disconnect();
        }
        catch (Throwable e)
        {
            // just in case people dont have internet at the moment.
            error(e.getLocalizedMessage());
        }
    }

    private void error(String error)
    {
        if (dieWithError)
        {
            throw new RuntimeException(error);
        }
        else
        {
            getLogger().error(error);
        }
    }

    @SuppressWarnings("rawtypes")
    public URL getUrl() throws MalformedURLException
    {
        while (url instanceof Closure)
        {
            url = ((Closure) url).call();
        }

        return new URL(url.toString());
    }

    public void setUrl(Object url)
    {
        this.url = url;
    }

    public File getFile()
    {
        return getProject().file(file);
    }

    public void setFile(Object file)
    {
        this.file = file;
    }

    public boolean isDieWithError()
    {
        return dieWithError;
    }

    public void setDieWithError(boolean dieWithError)
    {
        this.dieWithError = dieWithError;
    }
}
