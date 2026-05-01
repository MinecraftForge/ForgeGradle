package net.minecraftforge.gradle.tasks.abstractutil;

import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import groovy.lang.Closure;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.common.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class EtagDownloadTask extends DefaultTask {
    Object uri;
    Object file;
    boolean dieWithError;

    @TaskAction
    public void doTask() throws IOException, URISyntaxException {
        URI uri = getUri();
        File outFile = getFile();
        File etagFile = getProject().file(getFile().getPath() + ".etag");

        // ensure folder exists
        outFile.getParentFile().mkdirs();

        String etag;
        if (etagFile.exists()) {
            etag = FileUtils.readString(etagFile);
        } else {
            etag = "";
        }

        try {
            HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", Constants.USER_AGENT);
            con.setRequestProperty("If-None-Match", etag);

            con.connect();

            switch (con.getResponseCode()) {
                case 404: // file not found.... duh...
                    error(uri + "  404'ed!");
                    break;
                case 304: // content is the same.
                    this.setDidWork(false);
                    break;
                case 200: // worked

                    // write file
                    InputStream stream = con.getInputStream();
                    Files.write(outFile.toPath(), ByteStreams.toByteArray(stream));
                    stream.close();

                    // write etag
                    etag = con.getHeaderField("ETag");
                    if (!Strings.isNullOrEmpty(etag)) {
                        Files.write(etagFile.toPath(), etag.getBytes(StandardCharsets.UTF_8));
                    }

                    break;
                default: // another code?? uh..
                    error("Unexpected reponse " + con.getResponseCode() + " from " + uri);
                    break;
            }

            con.disconnect();
        } catch (Throwable e) {
            // just in case people dont have internet at the moment.
            error(e.getLocalizedMessage());
        }
    }

    private void error(String error) {
        if (dieWithError) {
            throw new RuntimeException(error);
        } else {
            getLogger().error(error);
        }
    }

    @Deprecated
    @Internal
    public URL getUrl() throws MalformedURLException {
        try {
            return getUri().toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setUrl(Object url) {
        this.setUri(url);
    }

    @Input
    public URI getUri() throws URISyntaxException {
        while (uri instanceof Closure<?>) {
            uri = ((Closure<?>) uri).call();
        }

        return new URI(uri.toString());
    }

    public void setUri(Object url) {
        this.uri = url;
    }

    @OutputFile
    public File getFile() {
        return getProject().file(file);
    }

    public void setFile(Object file) {
        this.file = file;
    }

    @Input
    public boolean isDieWithError() {
        return dieWithError;
    }

    public void setDieWithError(boolean dieWithError) {
        this.dieWithError = dieWithError;
    }
}

