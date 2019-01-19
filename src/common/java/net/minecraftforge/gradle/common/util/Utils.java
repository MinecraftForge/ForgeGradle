/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.config.MCPConfigV1;
import net.minecraftforge.gradle.common.util.VersionJson.Download;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.internal.impldep.org.apache.commons.lang.text.StrBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MCPConfigV1.Step.class, new MCPConfigV1.Step.Deserializer())
        .registerTypeAdapter(VersionJson.Argument.class, new VersionJson.Argument.Deserializer())
        .setPrettyPrinting().create();
    private static int CACHE_TIMEOUT = 1000 * 60 * 60 * 1; //1 hour, Timeout used for version_manifest.json so we dont ping their server every request.
                                                          //manifest doesn't include sha1's so we use this for the per-version json as well.
    public static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/";

    public static void extractFile(ZipFile zip, String name, File output) throws IOException {
        extractFile(zip, zip.getEntry(name), output);
    }

    public static void extractFile(ZipFile zip, ZipEntry entry, File output) throws IOException {
        File parent = output.getParentFile();
        if (!parent.exists())
            parent.mkdirs();

        try (InputStream stream = zip.getInputStream(entry)) {
            Files.copy(stream, output.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void extractDirectory(Function<String, File> fileLocator, ZipFile zip, String directory) throws IOException {
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry e = entries.nextElement();
            if (e.isDirectory()) continue;
            if (!e.getName().startsWith(directory)) continue;
            extractFile(zip, e, fileLocator.apply(e.getName()));
        }
    }

    public static byte[] base64DecodeStringList(List<String> strings) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        for (String string : strings) {
            bos.write(Base64.getDecoder().decode(string));
        }
        return bos.toByteArray();
    }

    public static File delete(File file) {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        if (file.exists()) file.delete();
        return file;
    }

    public static File createEmpty(File file) throws IOException {
        file = delete(file);
        file.createNewFile();
        return file;
    }

    public static File getCacheBase(Project project) {
        return new File(project.getGradle().getGradleUserHomeDir(), "caches/forge_gradle");
    }
    public static File getCache(Project project, String... tail) {
        return new File(getCacheBase(project), String.join(File.separator, tail));
    }

    public static void extractZip(File source, File target, boolean overwrite) throws IOException {
        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry e = enu.nextElement();
                if (e.isDirectory()) continue;
                File out = new File(target, e.getName());
                if (out.exists() && !overwrite) continue;
                File parent = out.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    IOUtils.copy(zip.getInputStream(e), fos);
                }
            }
        }
    }

    public static File updateDownload(Project project, File target, Download dl) throws IOException {
        if (!target.exists() || !HashFunction.SHA1.hash(target).equals(dl.sha1)) {
            project.getLogger().lifecycle("Downloading: " + dl.url);

            if (!target.getParentFile().exists()) {
                target.getParentFile().mkdirs();
            }

            FileUtils.copyURLToFile(dl.url, target);
        }
        return target;
    }

    public static <T> T loadJson(File target, Class<T> clz) throws IOException {
        try (InputStream in = new FileInputStream(target)) {
            return GSON.fromJson(new InputStreamReader(in), clz);
        }
    }
    public static <T> T loadJson(InputStream in, Class<T> clz) throws IOException {
        return GSON.fromJson(new InputStreamReader(in), clz);
    }

    public static void updateHash(File target) throws IOException {
        updateHash(target, HashFunction.values());
    }
    public static void updateHash(File target, HashFunction... functions) throws IOException {
        for (HashFunction function : functions) {
            File cache = new File(target.getAbsolutePath() + "." + function.getExtension());
            if (target.exists()) {
                String hash = function.hash(target);
                Files.write(cache.toPath(), hash.getBytes());
            } else if (cache.exists()) {
                cache.delete();
            }
        }
    }

    public static void forZip(ZipFile zip, IOConsumer<ZipEntry> consumer) throws IOException {
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            consumer.accept(entries.nextElement());
        }
    }
    @FunctionalInterface
    public static interface IOConsumer<T> {
        public void accept(T value) throws IOException;
    }

    /**
     * Resolves the supplied object to a string.
     * If the input is null, this will return null.
     * Closures and Callables are called with no arguments.
     * Arrays use Arrays.toString().
     * File objects return their absolute paths.
     * All other objects have their toString run.
     * @param obj Object to resolve
     * @return resolved string
     */
    @SuppressWarnings("rawtypes")
    public static String resolveString(Object obj) {
        if (obj == null)
            return null;
        else if (obj instanceof String) // stop early if its the right type. no need to do more expensive checks
            return (String)obj;
        else if (obj instanceof Closure)
            return resolveString(((Closure)obj).call());// yes recursive.
        else if (obj instanceof Callable) {
            try {
                return resolveString(((Callable)obj).call());
            } catch (Exception e) {
                return null;
            }
        } else if (obj instanceof File)
            return ((File) obj).getAbsolutePath();
        else if (obj.getClass().isArray()) { // arrays
            if (obj instanceof Object[])
                return Arrays.toString(((Object[]) obj));
            else if (obj instanceof byte[])
                return Arrays.toString(((byte[]) obj));
            else if (obj instanceof char[])
                return Arrays.toString(((char[]) obj));
            else if (obj instanceof int[])
                return Arrays.toString(((int[]) obj));
            else if (obj instanceof float[])
                return Arrays.toString(((float[]) obj));
            else if (obj instanceof double[])
                return Arrays.toString(((double[]) obj));
            else if (obj instanceof long[])
                return Arrays.toString(((long[]) obj));
            else
                return obj.getClass().getSimpleName();
        }
        return obj.toString();
    }

    public static <T> T[] toArray(JsonArray array, Function<JsonElement, T> adapter, IntFunction<T[]> arrayFactory) {
        return StreamSupport.stream(array.spliterator(), false).map(adapter).toArray(arrayFactory);
    }

    public static byte[] getZipData(File file, String name) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null)
                throw new IOException("Zip Missing Entry: " + name + " File: " + file);

            return IOUtils.toByteArray(zip.getInputStream(entry));
        }
    }


    public static <T> T fromJson(InputStream stream, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(new InputStreamReader(stream), classOfT);
    }
    public static <T> T fromJson(byte[] data, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), classOfT);
    }

    public static boolean downloadEtag(URL url, File output) throws IOException {
        if (output.exists() && output.lastModified() > System.currentTimeMillis() - CACHE_TIMEOUT) {
            return true;
        }
        File efile = new File(output.getAbsolutePath() + ".etag");
        String etag = "";
        if (efile.exists())
            etag = new String(Files.readAllBytes(efile.toPath()), StandardCharsets.UTF_8);

        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        con.setInstanceFollowRedirects(true);
        if (output.exists())
            con.setIfModifiedSince(output.lastModified());
        if (etag != null && !etag.isEmpty())
            con.setRequestProperty("If-None-Match", etag);
        con.connect();

        if (con.getResponseCode() == 304) {
            output.setLastModified(new Date().getTime());
            return true;
        } else if (con.getResponseCode() == 200) {
            try {
                InputStream stream = con.getInputStream();
                int len = con.getContentLength();
                int read = -1;
                output.getParentFile().mkdirs();
                try (FileOutputStream out = new FileOutputStream(output)) {
                    read = IOUtils.copy(stream, out);
                }

                if (read != len) {
                    output.delete();
                    throw new IOException("Failed to read all of data from " + url + " got " + read + " expected " + len);
                }

                etag = con.getHeaderField("ETag");
                if (etag == null || etag.isEmpty())
                    Files.write(efile.toPath(), new byte[0]);
                else
                    Files.write(efile.toPath(), etag.getBytes(StandardCharsets.UTF_8));
                return true;
            } catch (IOException e) {
                output.delete();
                throw e;
            }
        }
        return false;
    }

    public static boolean downloadFile(URL url, File output) {
        String proto = url.getProtocol().toLowerCase();

        try {
            if ("http".equals(proto) || "https".equals(proto)) {
                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.setInstanceFollowRedirects(true);
                con.connect();

                if (con.getResponseCode() == 200) {
                    return downloadFile(con, output);
                }
            } else {
                URLConnection con = url.openConnection();
                con.connect();
                return downloadFile(con, output);
            }
        } catch (IOException e) {
            //Invalid URLs/File paths will cause FileNotFound or 404 errors.
            //As well as any errors during download.
            //So delete the output if it exists as it's invalid, and return false
            if (output.exists())
                output.delete();
        }

        return false;
    }

    private static boolean downloadFile(URLConnection con, File output) throws IOException {
        try {
            InputStream stream = con.getInputStream();
            int len = con.getContentLength();
            int read = -1;

            output.getParentFile().mkdirs();

            try (FileOutputStream out = new FileOutputStream(output)) {
                read = IOUtils.copy(stream, out);
            }

            if (read != len) {
                output.delete();
                throw new IOException("Failed to read all of data from " + con.getURL() + " got " + read + " expected " + len);
            }

            return true;
        } catch (IOException e) {
            output.delete();
            throw e;
        }
    }

    public static String downloadString(URL url) throws IOException {
        String proto = url.getProtocol().toLowerCase();

        if ("http".equals(proto) || "https".equals(proto)) {
            HttpURLConnection con = (HttpURLConnection)url.openConnection();
            con.setInstanceFollowRedirects(true);
            con.connect();

            if (con.getResponseCode() == 200) {
                return downloadString(con);
            }
        } else {
            URLConnection con = url.openConnection();
            con.connect();
            return downloadString(con);
        }
        return null;
    }
    private static String downloadString(URLConnection con) throws IOException {
        InputStream stream = con.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int len = con.getContentLength();
        int read = IOUtils.copy(stream, out);
        if (read != len)
            throw new IOException("Failed to read all of data from " + con.getURL() + " got " + read + " expected " + len);
        return new String(out.toByteArray(), StandardCharsets.UTF_8); //Read encoding from header?
    }

    public static File downloadMaven(Project project, Artifact artifact, boolean changing) {
        List<MavenArtifactRepository> repos = project.getRepositories().stream()
                .filter(e -> e instanceof MavenArtifactRepository)
                .map(e -> (MavenArtifactRepository)e)
                .collect(Collectors.toList());

        for (MavenArtifactRepository repo : repos) {
            try {
                //project.getLogger().lifecycle("Downloading: " + repo.getUrl() + " " + artifact.getPath());
                File ret = _downloadMaven(project, repo.getUrl().toURL(), artifact, changing);
                if (ret != null)
                    return ret;
            } catch (IOException e) {
                // Eat it.
                e.printStackTrace();
            }
        }
        return null;
    }

    private static File _downloadMaven(Project project, URL maven, Artifact artifact, boolean changing) throws IOException {
        if (artifact.getVersion().contains("+") || artifact.getVersion().contains("-SNAPSHOT"))
            throw new IllegalArgumentException("Dynamic versions are not supported, yet...");

        String base = maven.toString();
        if (!base.endsWith("/") && !base.endsWith("\\"))
            base += "/";

        File cache = getCache(project, "maven_downloader");
        File target = new File(cache, artifact.getPath());
        File md5_file = new File(cache, artifact.getPath() + ".md5");
        String actual = target.exists() ? HashFunction.MD5.hash(target) : null;

        if (target.exists() && !changing) {
            String expected = md5_file.exists() ? new String(Files.readAllBytes(md5_file.toPath()), StandardCharsets.UTF_8) : null;
            if (expected == null || expected.equals(actual))
                return target;
            target.delete();
        }

        String expected = null;
        try {
            expected = downloadString(new URL(maven + artifact.getPath() + ".md5"));
        } catch (IOException e) {
            //Eat it, some repos don't have a simple checksum.
        }
        if (expected == null && target.exists()) return target; //Assume we're good cuz they didn't have a MD5 on the server.
        if (expected != null && expected.equals(actual)) return target;

        if (target.exists())
            target.delete(); //Invalid checksum, delete and grab new

        if (!downloadFile(new URL(maven + artifact.getPath()), target)) {
            target.delete();
            return null;
        }

        updateHash(target, HashFunction.MD5);
        return target;
    }

    /**
     * Uncapitalizes a string.
     * Required due to both StringUtils classes not being on the classpath in certain gradle constructions.
     *
     * @param str The string.
     * @return A uncapitalized version: CAT -> cAT, cat -> cat, Cat -> cat.
     */
    public static String uncapitalizeString(String str) {
        return str != null && str.length() != 0 ? Character.toLowerCase(str.charAt(0)) + str.substring(1) : str;
    }

}
