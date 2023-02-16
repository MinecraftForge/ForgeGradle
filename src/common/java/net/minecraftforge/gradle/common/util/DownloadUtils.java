/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

public class DownloadUtils {
    private static final Map<String, DecompressionStrategy> DECOMPRESSION_STRATEGIES = ImmutableMap.of(
            "identity", stream -> stream,
            "gzip", GZIPInputStream::new,
            "deflate", InflaterInputStream::new
    );
    private static final String ACCEPT_ENCODING = String.join(", ", DECOMPRESSION_STRATEGIES.keySet());

    private DownloadUtils() {} // Prevent instantiation

    public static boolean downloadEtag(URL url, File output, boolean offline) throws IOException {
        if (output.exists() && output.lastModified() > System.currentTimeMillis() - Utils.CACHE_TIMEOUT) {
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
                downloadFileConsideringCompression(con, output);

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
        return downloadFile(url, output, null, deleteOn404);
    }

    public static boolean downloadFile(URL url, File output, @Nullable Map<String, String> headers, boolean deleteOn404) {
        String proto = url.getProtocol().toLowerCase();

        try {
            if ("http".equals(proto) || "https".equals(proto)) {
                HttpURLConnection con = connectHttpWithRedirects(url, urlCon -> {
                    if (headers != null) {
                        for (Map.Entry<String, String> entry : headers.entrySet()) {
                            urlCon.setRequestProperty(entry.getKey(), entry.getValue());
                        }
                    }
                });
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
            downloadFileConsideringCompression(con, output);
            return true;
        } catch (IOException e) {
            if (output.exists())
                output.delete();
            throw e;
        }
    }

    @Nullable
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
        final int len = con.getContentLength();
        final InputStream is = getInputStream(con);
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final int read = IOUtils.copy(is, out);
        if (isEncoded(con) && len != -1 && read != len) {
            throw new IOException("Failed to read all data from " + con.getURL() + "; got " + read + " expected " + len);
        }

        Charset charset = StandardCharsets.UTF_8;
        if (con.getContentType() != null) {
            try {
                charset = MediaType.parse(con.getContentType()).charset().or(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {}
        }
        return new String(out.toByteArray(), charset);
    }

    @Nullable
    public static File downloadWithCache(URL url, File target, boolean changing, boolean bypassLocal) throws IOException {
        return downloadWithCache(url, target, null, changing, bypassLocal);
    }

    @Nullable
    public static File downloadWithCache(URL url, File target, @Nullable Map<String, String> headers, boolean changing, boolean bypassLocal) throws IOException {
        File md5_file = new File(target.getAbsolutePath() + ".md5");
        String actual = target.exists() ? HashFunction.MD5.hash(target) : null;

        if (md5_file.exists() && target.exists() && !(changing || bypassLocal)) {
            String expected = new String(Files.readAllBytes(md5_file.toPath()), StandardCharsets.UTF_8);
            if (expected.equals(actual)) return target; // Skip all downloads if the local MD5 matches the local file.
        }

        String expected = null;
        try {
            expected = downloadString(new URL(url + ".md5"));
        } catch (IOException e) {
            //Eat it, some repos don't have a simple checksum.
        }
        if (expected == null && bypassLocal) return null; // Ignore local file if the remote doesn't have a MD5 checksum.
        if (expected != null && expected.equals(actual)) {
            // Require MD5 checksum to skip the download as it will be calculated after every successful download.
            Files.write(md5_file.toPath(), expected.getBytes(StandardCharsets.UTF_8));
            return target;
        }

        if (target.exists())
            target.delete(); //Invalid checksum, delete and grab new

        if (!downloadFile(url, target, headers, false)) {
            target.delete();
            return null;
        }

        Utils.updateHash(target, HashFunction.MD5);
        return target;
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url) throws IOException {
        return connectHttpWithRedirects(url, (setupCon) -> {});
    }

    public static HttpURLConnection connectHttpWithRedirects(URL url, Consumer<HttpURLConnection> setup) throws IOException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        con.addRequestProperty("Accept-Encoding", ACCEPT_ENCODING);
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

    private static void downloadFileConsideringCompression(URLConnection connection, File output) throws IOException {
        final int len = connection.getContentLength();
        final InputStream in = getInputStream(connection);
        final File parent = output.getParentFile();
        if (parent != null) parent.mkdirs();
        try (final FileOutputStream fos = new FileOutputStream(output)) {
            final int read = IOUtils.copy(in, fos);
            // There will be a discrepancy between the bytes expected and read when we are sent a compressed response
            if (isEncoded(connection) && len != -1 && read != len) {
                throw new IOException("Failed to read all data from " + connection.getURL() + "; got " + read + " expected " + len);
            }
        }
    }

    private static InputStream getInputStream(URLConnection connection) throws IOException {
        final String encoding = connection.getContentEncoding();
        if (encoding == null || encoding.isEmpty()) return connection.getInputStream();
        InputStream is = connection.getInputStream();
        final String[] encodings = encoding.split(",");
        for (final String enc : encodings) {
            final DecompressionStrategy strategy = DECOMPRESSION_STRATEGIES.get(enc.trim());
            if (strategy == null) {
                throw new IOException("Unknown content-encoding \"" + enc + "\"!");
            }
            is = strategy.wrap(is);
        }
        return is;
    }

    private static boolean isEncoded(URLConnection connection) {
        final String enc = connection.getContentEncoding();
        return enc == null || enc.equals("identity");
    }

    @FunctionalInterface
    public interface DecompressionStrategy {
        InputStream wrap(InputStream stream) throws IOException;
    }
}
