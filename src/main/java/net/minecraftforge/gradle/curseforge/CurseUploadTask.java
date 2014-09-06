package net.minecraftforge.gradle.curseforge;

import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Set;
import java.util.TreeSet;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.curse.CurseMetadata;
import net.minecraftforge.gradle.json.curse.CurseReply;
import net.minecraftforge.gradle.json.curse.CurseVersion;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Charsets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class CurseUploadTask extends DefaultTask
{
    Object               projectId;
    Object               artifact;
    String               apiKey;
    Set<Object>          gameVersions  = new TreeSet<Object>();
    Object               releaseType;
    Object               changelog;
    int                  fileID;

    private final String UPLOAD_URL    = "https://minecraft.curseforge.com/api/projects/%s/upload-file";
    private final String VERSION_URL   = "https://minecraft.curseforge.com/api/game/versions";
    private final File   VERSION_CACHE = new File(getProject().getGradle().getGradleUserHomeDir() + "/caches/minecraft/curseVersions.json");

    @TaskAction
    public void doTask() throws IOException, URISyntaxException
    {
        CurseMetadata meta = new CurseMetadata();
        meta.releaseType = getReleaseType();
        meta.changelog = getChangelog() == null ? "" : getChangelog();
        meta.gameVersions = resolveGameVersion();
        String url = String.format(UPLOAD_URL, getProjectId());

        if (meta.releaseType == null)
            throw new IllegalArgumentException("Release type must be defined!");

        upload(meta, url, getProject().file(getArtifact()));
    }

    private void upload(CurseMetadata meta, String strUrl, File artifact) throws IOException, URISyntaxException
    {

        // stolen from http://stackoverflow.com/a/3003402
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(new URI(strUrl));
        String metaJson = JsonFactory.GSON.toJson(meta);

        httpPost.addHeader("X-Api-Token", getApiKey());
        httpPost.setEntity(
                MultipartEntityBuilder.create()
                        .addTextBody("metadata", metaJson)
                        .addBinaryBody("file", artifact).build()
                );

        getLogger().lifecycle("Uploading {} to {}", artifact, strUrl);
        getLogger().info("Uploading with meta {}", metaJson);

        HttpResponse response = httpclient.execute(httpPost);

        if (response.getStatusLine().getStatusCode() != 200)
        {
            getLogger().error("Error code: {}   {}", response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
            getLogger().error("Maybe your API key or projectID is wrong!");
            return;
        }

        InputStreamReader stream = new InputStreamReader(response.getEntity().getContent());
        int fileID = JsonFactory.GSON.fromJson(stream, CurseReply.class).id;
        this.fileID = fileID;
        stream.close();
        getLogger().lifecycle("File uploaded to CurseForge succcesfully with ID {}", fileID);
    }

    private int[] resolveGameVersion() throws IOException
    {
        String json = getWithEtag(VERSION_URL, VERSION_CACHE);
        CurseVersion[] versions = JsonFactory.GSON.fromJson(json, CurseVersion[].class);
        TObjectIntHashMap vMap = new TObjectIntHashMap();

        for (CurseVersion v : versions)
        {
            if (v.gameDependencyID == 0)
            {
                vMap.put(v.name, v.id);
            }
        }

        Set<String> gameVersions = getGameVersions();
        TIntHashSet out = new TIntHashSet();

        for (String v : gameVersions)
        {
            if (vMap.containsKey(v))
            {
                out.add(vMap.get(v));
            }
            else
            {
                throw new IllegalArgumentException(v + " is not a valid game version for CurseForge!");
            }
        }

        if (out.isEmpty())
            throw new IllegalArgumentException("No valid game version set for CurseForge upload!");

        return out.toArray();
    }

    // getters setters and util

    private String getWithEtag(String strUrl, File cache) throws IOException
    {
        URL url = new URL(strUrl);
        File etagFile = getProject().file(cache.getPath() + ".etag");

        String etag;
        if (etagFile.exists())
        {
            etag = Files.toString(etagFile, Charsets.UTF_8);
        }
        else
        {
            etag = "";
        }

        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        con.addRequestProperty("X-Api-Token", getApiKey());
        con.setRequestProperty("If-None-Match", etag);

        try
        {
            con.connect();
        }
        catch (Throwable e)
        {
            // just in case people dont have internet at the moment.
            throw new RuntimeException(e.getLocalizedMessage());
        }

        String error = null;

        byte[] stuff = new byte[0];

        switch (con.getResponseCode())
            {
                case 404: // file not found.... duh...
                    error = "" + url + "  404'ed!";
                    break;
                case 304: // content is the same.
                    break;
                case 200: // worked
                    InputStream stream = con.getInputStream();
                    stuff = ByteStreams.toByteArray(stream);
                    Files.write(stuff, cache);
                    stream.close();
                    break;
                default: // another code?? uh.. 
                    error = "Unexpected reponse " + con.getResponseCode() + " from " + url;
                    break;
            }

        con.disconnect();

        if (error != null)
        {
            throw new RuntimeException(error);
        }

        return new String(stuff, Charsets.UTF_8);
    }

    @SuppressWarnings("rawtypes")
    private String resolveString(Object obj)
    {
        while (obj instanceof Closure)
        {
            obj = ((Closure) obj).call();
        }

        return obj.toString();
    }

    public String getProjectId()
    {
        return (String) (projectId = resolveString(projectId));
    }

    public void setProjectId(Object projectId)
    {
        this.projectId = projectId;
    }

    public String getApiKey()
    {
        return apiKey;
    }

    public void setApiKey(String api_key)
    {
        this.apiKey = api_key;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Set<String> getGameVersions()
    {
        Set tVersions = new TreeSet<String>();
        for (Object obj : gameVersions)
        {
            tVersions.add(resolveString(obj));
        }

        gameVersions = tVersions;
        return tVersions;
    }

    public void addGameVersion(Object gameVersion)
    {
        this.gameVersions.add(gameVersion);
    }
    
    public void addGameVersion(Object... gameVersions)
    {
        for (Object o : gameVersions)
        {
            this.gameVersions.add(o);
        }
    }

    public String getReleaseType()
    {
        return (String) (releaseType = resolveString(releaseType));
    }

    public void setReleaseType(Object releaseType)
    {
        this.releaseType = StringUtils.lower(resolveString(releaseType));

        if (!"alpha".equals(releaseType) && !"beta".equals(releaseType) && !"release".equals(releaseType))
            throw new IllegalArgumentException("The release type must be either 'alpha', 'beta', or 'release'! '" + releaseType + "' is not a valid type!");
    }

    public String getChangelog()
    {
        return (String) (changelog = resolveString(changelog));
    }

    public void setChangelog(String changeLog)
    {
        this.changelog = changeLog;
    }

    public Object getArtifact()
    {
        return artifact;
    }

    public void setArtifact(Object artifact)
    {
        this.artifact = artifact;
    }

    public int getFileID() {
        return fileID;
    }

    public void setFileID(int fileID) {
        this.fileID = fileID;
    }
}
