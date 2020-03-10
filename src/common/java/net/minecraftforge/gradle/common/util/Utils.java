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
import net.minecraftforge.gradle.common.task.ExtractNatives;
import net.minecraftforge.gradle.common.util.VersionJson.Download;
import net.minecraftforge.gradle.common.util.runs.RunConfigGenerator;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.TaskProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Utils {
    private static final boolean ENABLE_TEST_CERTS = Boolean.parseBoolean(System.getProperty("net.minecraftforge.gradle.test_certs", "true"));
    private static final boolean ENABLE_TEST_JAVA  = Boolean.parseBoolean(System.getProperty("net.minecraftforge.gradle.test_java", "true"));

    public static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(MCPConfigV1.Step.class, new MCPConfigV1.Step.Deserializer())
        .registerTypeAdapter(VersionJson.Argument.class, new VersionJson.Argument.Deserializer())
        .setPrettyPrinting().create();
    private static final int CACHE_TIMEOUT = 1000 * 60 * 60 * 1; //1 hour, Timeout used for version_manifest.json so we dont ping their server every request.
                                                          //manifest doesn't include sha1's so we use this for the per-version json as well.
    public static final String FORGE_MAVEN = "https://files.minecraftforge.net/maven/";
    public static final String MOJANG_MAVEN = "https://libraries.minecraft.net/";
    public static final String BINPATCHER =  "net.minecraftforge:binarypatcher:1.+:fatjar";
    public static final String ACCESSTRANSFORMER = "net.minecraftforge:accesstransformers:1.0.+:fatjar";
    public static final String SPECIALSOURCE = "net.md-5:SpecialSource:1.8.3:shaded";
    public static final String SRG2SOURCE =  "net.minecraftforge:Srg2Source:5.+:fatjar";
    public static final String SIDESTRIPPER = "net.minecraftforge:mergetool:1.0.7:fatjar";
    public static final String INSTALLERTOOLS = "net.minecraftforge:installertools:1.1.7:fatjar";
    public static final long ZIPTIME = 628041600000L;
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");
    public static final String OFFICIAL_MAPPING_USAGE =
            "These mapping files are licensed as All Rights Reserved with permission to use the contents for INTERNAL, "
          + "REFERENCE purposes. Please avoid publishing any source code referencing these mappings. A full copy of "
          + "the license can be found at the top of the mapping file itself and in the 19w36a snapshot article at: "
          + "https://www.minecraft.net/en-us/article/minecraft-snapshot-19w36a.";

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

    public static Path getCacheBase(Project project) {
        File gradleUserHomeDir = project.getGradle().getGradleUserHomeDir();
        return Paths.get(gradleUserHomeDir.getPath(), "caches", "forge_gradle");
    }

    public static File getCache(Project project, String... tail) {
        return Paths.get(getCacheBase(project).toString(), tail).toFile();
    }

    public static void extractZip(File source, File target, boolean overwrite) throws IOException {
        extractZip(source, target, overwrite, false);
    }

    public static void extractZip(File source, File target, boolean overwrite, boolean deleteExtras) throws IOException {
        Set<File> extra = deleteExtras ? Files.walk(target.toPath()).filter(Files::isRegularFile).map(Path::toFile).collect(Collectors.toSet()) : new HashSet<>();

        try (ZipFile zip = new ZipFile(source)) {
            Enumeration<? extends ZipEntry> enu = zip.entries();
            while (enu.hasMoreElements()) {
                ZipEntry e = enu.nextElement();
                if (e.isDirectory()) continue;
                File out = new File(target, e.getName());
                File parent = out.getParentFile();
                if (!parent.exists()) {
                    parent.mkdirs();
                }
                extra.remove(out);

                if (out.exists()) {
                    if (!overwrite)
                        continue;

                    //Reading is fast, and prevents Disc wear, so check if it's equals before writing.
                    try (FileInputStream fis = new FileInputStream(out)){
                        if (IOUtils.contentEquals(zip.getInputStream(e), fis))
                            continue;
                    }
                }

                try (FileOutputStream fos = new FileOutputStream(out)) {
                    IOUtils.copy(zip.getInputStream(e), fos);
                }
            }
        }

        if (deleteExtras) {
            extra.forEach(File::delete);

            //Delete empty directories
            Files.walk(target.toPath())
            .filter(Files::isDirectory)
            .sorted(Comparator.reverseOrder())
            .map(Path::toFile)
            .filter(f -> f.list().length == 0)
            .forEach(File::delete);
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

    public static boolean downloadEtag(URL url, File output, boolean offline) throws IOException {
        if (output.exists() && output.lastModified() > System.currentTimeMillis() - CACHE_TIMEOUT) {
            return true;
        }
        if (output.exists() && offline) {
            return true; //Use offline
        }
        File efile = new File(output.getAbsolutePath() + ".etag");
        String etag = "";
        if (efile.exists())
            etag = new String(Files.readAllBytes(efile.toPath()), StandardCharsets.UTF_8);

        final String initialEtagValue = etag;
        HttpURLConnection con = connectHttpWithRedirects(url, (setupCon) -> {
            if (output.exists())
                setupCon.setIfModifiedSince(output.lastModified());
            if (!initialEtagValue.isEmpty())
                setupCon.setRequestProperty("If-None-Match", initialEtagValue);
        });

        if (con.getResponseCode() == HttpURLConnection.HTTP_NOT_MODIFIED) {
            output.setLastModified(new Date().getTime());
            return true;
        } else if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
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

    public static boolean downloadFile(URL url, File output, boolean deleteOn404) {
        String proto = url.getProtocol().toLowerCase();

        try {
            if ("http".equals(proto) || "https".equals(proto)) {
                HttpURLConnection con = connectHttpWithRedirects(url);
                int responseCode = con.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    return downloadFile(con, output);
                } else if (responseCode == HttpURLConnection.HTTP_NOT_FOUND && deleteOn404 && output.exists()) {
                    output.delete();
                }
            } else {
                URLConnection con = url.openConnection();
                con.connect();
                return downloadFile(con, output);
            }
        } catch (FileNotFoundException e) {
            if (deleteOn404 && output.exists())
                output.delete();
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
            HttpURLConnection con = connectHttpWithRedirects(url);
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
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

    public static File downloadWithCache(URL url, File target, boolean changing, boolean bypassLocal) throws IOException {
        File md5_file = new File(target.getAbsolutePath() + ".md5");
        String actual = target.exists() ? HashFunction.MD5.hash(target) : null;

        if (target.exists() && !(changing || bypassLocal)) {
            String expected = md5_file.exists() ? new String(Files.readAllBytes(md5_file.toPath()), StandardCharsets.UTF_8) : null;
            if (expected == null || expected.equals(actual))
                return target;
            target.delete();
        }

        String expected = null;
        try {
            expected = downloadString(new URL(url.toString() + ".md5"));
        } catch (IOException e) {
            //Eat it, some repos don't have a simple checksum.
        }
        if (expected == null && bypassLocal) return null; // Ignore local file if the remote doesn't have it.
        if (expected == null && target.exists()) return target; //Assume we're good cuz they didn't have a MD5 on the server.
        if (expected != null && expected.equals(actual)) return target;

        if (target.exists())
            target.delete(); //Invalid checksum, delete and grab new

        if (!downloadFile(url, target, false)) {
            target.delete();
            return null;
        }

        updateHash(target, HashFunction.MD5);
        return target;
    }

    @Nonnull
    public static final String capitalize(@Nonnull final String toCapitalize) {
        return toCapitalize.length() > 1 ? toCapitalize.substring(0, 1).toUpperCase() + toCapitalize.substring(1) : toCapitalize;
    }

    public static void checkJavaRange( @Nullable JavaVersionParser.JavaVersion minVersionInclusive, @Nullable JavaVersionParser.JavaVersion maxVersionExclusive) {
        JavaVersionParser.JavaVersion currentJavaVersion = JavaVersionParser.getCurrentJavaVersion();
        if (minVersionInclusive != null && currentJavaVersion.compareTo(minVersionInclusive) < 0)
            throw new RuntimeException(String.format("Found java version %s. Minimum required is %s.", currentJavaVersion, minVersionInclusive));
        if (maxVersionExclusive != null && currentJavaVersion.compareTo(maxVersionExclusive) >= 0)
            throw new RuntimeException(String.format("Found java version %s. Versions %s and newer are not supported yet.", currentJavaVersion, maxVersionExclusive));
    }

    public static void checkJavaVersion() {
        if (ENABLE_TEST_JAVA) {
            checkJavaRange(
                // Mininum must be update 101 as it's the first one to include Let's Encrypt certificates.
                JavaVersionParser.parseJavaVersion("1.8.0_101"),
                null //TODO: Add JDK range check to MCPConfig?
            );
        }

        if (ENABLE_TEST_CERTS) {
            testServerConnection(FORGE_MAVEN);
            testServerConnection(MOJANG_MAVEN);
        }
    }

    private static void testServerConnection(String url) {
        try {
            HttpsURLConnection conn = (HttpsURLConnection)new URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.connect();
            conn.getResponseCode();
        } catch (SSLException e) {
            throw new RuntimeException(String.format("Failed to validate certificate for %s, Most likely cause is an outdated JDK. Try updating at https://adoptopenjdk.net/ " +
                    "To disable this check re-run with -Dnet.minecraftforge.gradle.test_certs=false", url), e);
        } catch (IOException e) {
            //Normal connection failed, not the point of this test so ignore
        }
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url) throws IOException {
        return connectHttpWithRedirects(url, (setupCon) -> {});
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url, Consumer<HttpURLConnection> setup) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        setup.accept(con);
        con.connect();
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            int responseCode = con.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_MOVED_TEMP:
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_SEE_OTHER:
                    String newLocation = con.getHeaderField("Location");
                    URL newUrl = new URL(newLocation);
                    if ("https".equalsIgnoreCase(newUrl.getProtocol())) {
                        // Escalate from http to https.
                        // This is not done automatically by HttpURLConnection.setInstanceFollowRedirects
                        // See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4959149
                        return connectHttpWithRedirects(newUrl, setup);
                    }
                    break;
            }
        }
        return con;
    }

    public static Stream<String> lines(InputStream input) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
        return reader.lines().onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static ZipEntry getStableEntry(String name) {
        return getStableEntry(name, Utils.ZIPTIME);
    }

    public static ZipEntry getStableEntry(String name, long time) {
        TimeZone _default = TimeZone.getDefault();
        TimeZone.setDefault(GMT);
        ZipEntry ret = new ZipEntry(name);
        ret.setTime(time);
        TimeZone.setDefault(_default);
        return ret;
    }

    public static void createRunConfigTasks(final MinecraftExtension extension, final ExtractNatives extractNatives, final Task... setupTasks) {
        List<Task> setupTasksLst = new ArrayList<>();
        for (Task t : setupTasks)
            setupTasksLst.add(t);

        final TaskProvider<Task> prepareRuns = extension.getProject().getTasks().register("prepareRuns", Task.class, task -> {
            task.setGroup(RunConfig.RUNS_GROUP);
            task.dependsOn(extractNatives);
            setupTasksLst.forEach(task::dependsOn);
        });

        final TaskProvider<Task> makeSrcDirs = extension.getProject().getTasks().register("makeSrcDirs", Task.class, task -> {
            task.doFirst(t -> {
                final JavaPluginConvention java = task.getProject().getConvention().getPlugin(JavaPluginConvention.class);

                java.getSourceSets().forEach(s -> s.getAllSource()
                        .getSrcDirs().stream().filter(f -> !f.exists()).forEach(File::mkdirs));
            });
        });
        setupTasksLst.add(makeSrcDirs.get());

        extension.getRuns().forEach(RunConfig::mergeParents);

        // Create run configurations _AFTER_ all projects have evaluated so that _ALL_ run configs exist and have been configured
        extension.getProject().getGradle().projectsEvaluated(gradle -> {
            VersionJson json = null;

            try {
                json = Utils.loadJson(extractNatives.getMeta(), VersionJson.class);
            } catch (IOException ignored) {
            }

            List<String> additionalClientArgs = json != null ? json.getPlatformJvmArgs() : Collections.emptyList();

            extension.getRuns().forEach(RunConfig::mergeChildren);
            extension.getRuns().forEach(run -> RunConfigGenerator.createRunTask(run, extension.getProject(), prepareRuns, additionalClientArgs));

            EclipseHacks.doEclipseFixes(extension, extractNatives, setupTasksLst);

            RunConfigGenerator.createIDEGenRunsTasks(extension, prepareRuns, makeSrcDirs, additionalClientArgs);
        });
    }
}
