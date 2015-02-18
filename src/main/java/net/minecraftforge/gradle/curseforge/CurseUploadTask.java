package net.minecraftforge.gradle.curseforge;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntHashSet;
import gnu.trove.TObjectIntHashMap;
import groovy.lang.Closure;
import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.curse.CurseError;
import net.minecraftforge.gradle.json.curse.CurseMetadata;
import net.minecraftforge.gradle.json.curse.CurseMetadataChild;
import net.minecraftforge.gradle.json.curse.CurseProjectDep;
import net.minecraftforge.gradle.json.curse.CurseRelations;
import net.minecraftforge.gradle.json.curse.CurseReply;
import net.minecraftforge.gradle.json.curse.CurseVersion;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClientBuilder;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class CurseUploadTask extends DefaultTask
{

    public static final Collection<String> validReleaseTypes  = ImmutableList.of("alpha", "beta", "release");
    public static final Collection<String> validRelationTypes = ImmutableList.of("embeddedLibrary", "optionalLibrary", "requiredLibrary", "tool", "incompatible");

    Object projectId;
    Object artifact;
    Object displayName;
    Collection<Object>  additionalArtifacts = new ArrayList<Object>();
    Map<Object, Object> curseProjectDeps    = new HashMap<Object, Object>();
    String apiKey;
    Set<Object> gameVersions = new TreeSet<Object>();
    Object releaseType;
    Object changelog;

    TIntArrayList fileIds;

    private static final String UPLOAD_URL    = "https://minecraft.curseforge.com/api/projects/%s/upload-file";
    private static final String VERSION_URL   = "https://minecraft.curseforge.com/api/game/versions";
    private final        File   VERSION_CACHE = new File(getProject().getGradle().getGradleUserHomeDir() + "/caches/minecraft/curseVersions.json");

    @TaskAction
    public void doTask() throws IOException, URISyntaxException
    {
        CurseMetadata meta = new CurseMetadata();
        meta.releaseType = getReleaseType();
        meta.changelog = getChangelog();
        meta.gameVersions = resolveGameVersion();
        meta.displayName = getDisplayName();
        if (!curseProjectDeps.isEmpty())
        {
            meta.relations = new CurseRelations();
            for (Map.Entry<Object, Object> entry : curseProjectDeps.entrySet())
            {
                CurseProjectDep dep = new CurseProjectDep(
                        resolveString(entry.getKey()),
                        resolveString(entry.getValue())
                );
                meta.relations.projects.add(dep);
            }
        }

        checkNotNull(meta.releaseType, "Curse releaseType cannot be null. Valid types are: " + validReleaseTypes);
        checkArgument(validReleaseTypes.contains(meta.releaseType),
                meta.releaseType + " is not a valid Curse relase type. Valid types are: " + validReleaseTypes);

        if (meta.relations != null)
        {
            for (CurseProjectDep projectDep : meta.relations.projects)
            {
                checkNotNull(projectDep.slug, "Curse project relation slug cannot be null");
                checkNotNull(projectDep.type, "Curse project relation type cannot be null");
                checkArgument(validRelationTypes.contains(projectDep.type),
                        projectDep.type + " is not a valid Curse project relation type. Valid types are: " + validRelationTypes);
            }
        }

        String url = String.format(UPLOAD_URL, getProjectId());
        String metaJson = JsonFactory.GSON.toJson(meta);

        int parentId = uploadFile(metaJson, url, resolveFile(getArtifact()));

        fileIds = new TIntArrayList(additionalArtifacts.size() + 1);
        fileIds.add(parentId);

        if (!additionalArtifacts.isEmpty())
        {
            CurseMetadataChild childMeta = new CurseMetadataChild();
            childMeta.releaseType = meta.releaseType;
            childMeta.changelog = meta.changelog;
            childMeta.parentFileID = parentId;
            String childMetaJson = JsonFactory.GSON.toJson(childMeta);
            uploadFiles(childMetaJson, url, additionalArtifacts);
        }
    }

    private void uploadFiles(String jsonMetadata, String url, Collection<Object> files) throws IOException, URISyntaxException
    {
        for (Object obj : files)
        {
            File file = resolveFile(obj);
            int id = uploadFile(jsonMetadata, url, file);
            fileIds.add(id);
        }
    }

    private int uploadFile(String jsonMetadata, String url, File file) throws IOException, URISyntaxException
    {
        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(new URI(url));

        httpPost.addHeader("X-Api-Token", getApiKey());
        httpPost.setHeader("User-Agent", Constants.USER_AGENT);
        httpPost.setEntity(
                MultipartEntityBuilder.create()
                        .addTextBody("metadata", jsonMetadata)
                        .addBinaryBody("file", file).build()
        );
        getLogger().lifecycle("Uploading {} to {}", file, url);
        getLogger().info("Using with metadata: {}", jsonMetadata);

        HttpResponse response = httpclient.execute(httpPost);

        if (response.getStatusLine().getStatusCode() == 200)
        {
            InputStreamReader stream = new InputStreamReader(response.getEntity().getContent());
            int fileId = JsonFactory.GSON.fromJson(stream, CurseReply.class).id;
            stream.close();
            getLogger().lifecycle("File uploaded to CurseForge succesfully with ID: {}", fileId);
            return fileId;
        }
        else
        {
            if (response.getFirstHeader("content-type").getValue().contains("json"))
            {
                InputStreamReader stream = new InputStreamReader(response.getEntity().getContent());
                CurseError curseError = JsonFactory.GSON.fromJson(stream, CurseError.class);
                stream.close();
                throw new RuntimeException("Error code: " + curseError.errorCode + " " + curseError.errorMessage);
            }
            else
            {
                throw new RuntimeException("Error code: " + response.getStatusLine().getStatusCode() + " " + response.getStatusLine().getReasonPhrase());
            }
        }
    }

    private int[] resolveGameVersion() throws IOException, URISyntaxException
    {
        String json = getWithEtag(VERSION_URL, VERSION_CACHE);
        CurseVersion[] versions = JsonFactory.GSON.fromJson(json, CurseVersion[].class);
        TObjectIntHashMap<String> vMap = new TObjectIntHashMap<String>();

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
        {
            throw new IllegalArgumentException("No valid game version set for CurseForge upload!");
        }

        return out.toArray();
    }

    // getters setters and util

    private String getWithEtag(String strUrl, File cache) throws IOException, URISyntaxException
    {
        //URL url = new URL(strUrl);
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

        HttpClient httpclient = HttpClientBuilder.create().build();
        HttpGet httpGet = new HttpGet(new URI(strUrl));

        httpGet.setHeader("X-Api-Token", getApiKey());
        httpGet.setHeader("If-None-Match", etag);
        httpGet.setHeader("User-Agent", Constants.USER_AGENT);

        HttpResponse response = httpclient.execute(httpGet);

        String error = null;
        String out = null;

        int statusCode = response.getStatusLine().getStatusCode();

        if (statusCode == 304) // cached
        {
            out = Files.toString(cache, Charsets.UTF_8);
        }
        else if (statusCode == 200)
        {
            InputStream stream = response.getEntity().getContent();
            byte[] data = ByteStreams.toByteArray(stream);
            Files.write(data, cache);
            stream.close();
            out = new String(data, Charsets.UTF_8);

            Header etagHeader = response.getFirstHeader("ETag");
            if (etagHeader != null)
            {
                Files.write(etagHeader.getValue(), etagFile, Charsets.UTF_8);
            }
        }
        else if (response.getEntity().getContentType().getValue().contains("json"))
        {
            InputStreamReader stream = new InputStreamReader(response.getEntity().getContent());
            CurseError cError = JsonFactory.GSON.fromJson(stream, CurseError.class);
            stream.close();

            error = "Curse Error " + cError.errorCode + ": " + cError.errorMessage;
        }
        else
        {
            error = "Error " + statusCode + ": " + response.getStatusLine().getReasonPhrase();
        }

        if (error != null)
        {
            throw new RuntimeException(error);
        }

        return out;
    }

    @SuppressWarnings("rawtypes")
    private String resolveString(Object obj)
    {
        if (obj instanceof Closure)
        {
            obj = resolveString(((Closure) obj).call());
        }

        if (obj == null)
        {
            return null;
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
        Collections.addAll(this.gameVersions, gameVersions);
    }

    public String getReleaseType()
    {
        if (releaseType == null)
        {
            return null;
        }
        this.releaseType = StringUtils.lower(resolveString(releaseType));
        return (String) releaseType;
    }

    public void setReleaseType(Object releaseType)
    {
        this.releaseType = releaseType;
    }

    public String getChangelog()
    {
        if (changelog == null)
        {
            return "";
        }
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
        if (artifact instanceof AbstractArchiveTask)
        {
            dependsOn(artifact);
        }

        this.artifact = artifact;
    }

    public String getDisplayName()
    {
        if (displayName == null)
        {
            return null;
        }
        return (String) (displayName = resolveString(displayName));
    }

    public void setDisplayName(Object displayName)
    {
        this.displayName = displayName;
    }

    public Collection<Object> getAdditionalArtifacts()
    {
        return additionalArtifacts;
    }

    public void additionalArtifact(Object obj)
    {
        if (obj instanceof AbstractArchiveTask)
        {
            dependsOn(obj);
        }

        additionalArtifacts.add(obj);
    }

    public void additionalArtifact(Object... obj)
    {
        for (Object o : obj)
        {
            additionalArtifact(o);
        }
    }

    public int getFileId()
    {
        return fileIds.get(0);
    }

    public int[] getFileIds()
    {
        return fileIds.toNativeArray();
    }

    /**
     * Add a related project with the default relation type of {@code requiredLibrary}
     *
     * @param projectSlug The project slug
     */
    public void relatedProject(Object projectSlug)
    {
        curseProjectDeps.put(projectSlug, "requiredLibrary");
    }

    /**
     * Add a related project
     *
     * @param map A map where each entry key is the project slug, and the entry value is the relation type.
     */
    public void relatedProject(Map<Object, Object> map)
    {
        for (Map.Entry<Object, Object> entry : map.entrySet())
        {
            curseProjectDeps.put(entry.getKey(), entry.getValue());
        }
    }

    private File resolveFile(Object object)
    {
        checkNotNull(object, "Configured a null artifact!");

        if (object instanceof File)
        {
            return (File) object;
        }
        else if (object instanceof AbstractArchiveTask)
        {
            return ((AbstractArchiveTask) object).getArchivePath();
        }
        else if (object instanceof DelayedFile)
        {
            return ((DelayedFile) object).call();
        }
        else
        {
            return getProject().file(object);
        }
    }
}
