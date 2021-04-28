/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;
import groovy.lang.Closure;
import net.minecraftforge.gradle.patcher.PatcherExtension;
import net.minecraftforge.gradle.util.json.version.OS;

public class Constants
{
    // OS
    public static enum SystemArch
    {
        BIT_32, BIT_64;

        public String toString()
        {
            return lower(name()).replace("bit_", "");
        }
    }

    public static final OS         OPERATING_SYSTEM = OS.CURRENT;
    public static final SystemArch SYSTEM_ARCH      = getArch();
    public static final Charset    CHARSET          = Charsets.UTF_8;
    public static final String     HASH_FUNC        = "MD5";
    public static final String     USER_AGENT       = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.95 Safari/537.11";

    // extension names
    public static final String EXT_NAME_MC = "minecraft";

    public static final String GROUP_FG = "ForgeGradle";

    @SuppressWarnings("serial")
    public static final Closure<Boolean> CALL_FALSE = new Closure<Boolean>(Constants.class) {
        public Boolean call(Object o)
        {
            return false;
        }
    };

    // replacement strings

    /** MC version in form "#.#.#(-appendage)" where the appendage may be -pre# or something. **/
    public static final String REPLACE_MC_VERSION        = "{MC_VERSION}";
    /** the folder where to cache. ~/.gradle/caches/minecraft **/
    public static final String REPLACE_CACHE_DIR         = "{CACHE_DIR}";
    /** the folder where to cache project specific. project/.gradle/ **/
    public static final String REPLACE_PROJECT_CACHE_DIR = "{PROJECT_CACHE_DIR}";
    /** project/build **/
    public static final String REPLACE_BUILD_DIR         = "{BUILD_DIR}";
    /** MCP mapping channel **/
    public static final String REPLACE_MCP_CHANNEL       = "{MAPPING_CHANNEL}";
    /** MCP mapping version **/
    public static final String REPLACE_MCP_VERSION       = "{MAPPING_VERSION}";
    /** MCP mapping MC version **/
    public static final String REPLACE_MCP_MCVERSION     = "{MAPPING_MCVERSION}";
    /** AssetIndex name **/
    public static final String REPLACE_ASSET_INDEX       = "{ASSET_INDEX}";


    // urls
    public static final String URL_MC_MANIFEST     = "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    public static final String URL_ASSETS          = "http://resources.download.minecraft.net";
    public static final String URL_LIBRARY         = "https://libraries.minecraft.net/"; // Mojang's Cloudflare front end
    //public static final String URL_LIBRARY         = "https://minecraft-libraries.s3.amazonaws.com/"; // Mojang's AWS server, as Cloudflare is having issues, TODO: Switch back to above when their servers are fixed.
    public static final String URL_FORGE_MAVEN     = "https://maven.minecraftforge.net";
    public static final List<String> URLS_MCP_JSON = Arrays.asList(
            URL_FORGE_MAVEN + "/de/oceanlabs/mcp/versions.json",
            "http://export.mcpbot.bspk.rs/versions.json"
    );

    // configs
    public static final String CONFIG_MCP_DATA       = "forgeGradleMcpData";
    public static final String CONFIG_MAPPINGS       = "forgeGradleMcpMappings";
    public static final String CONFIG_NATIVES        = "forgeGradleMcNatives";
    public static final String CONFIG_MC_DEPS        = "forgeGradleMcDeps";
    public static final String CONFIG_FFI_DEPS        = "forgeGradleFfiDeps"; // FernFlowerInvoker
    public static final String CONFIG_MC_DEPS_CLIENT = "forgeGradleMcDepsClient";

    // things in the cache dir.
    public static final String DIR_LOCAL_CACHE  = REPLACE_PROJECT_CACHE_DIR + "/minecraft";
    public static final String DIR_MCP_DATA     = REPLACE_CACHE_DIR + "/de/oceanlabs/mcp/mcp/" + REPLACE_MC_VERSION;
    public static final String DIR_MCP_MAPPINGS = REPLACE_CACHE_DIR + "/de/oceanlabs/mcp/mcp_" + REPLACE_MCP_CHANNEL + "/" + REPLACE_MCP_VERSION;
    public static final String JAR_CLIENT_FRESH = REPLACE_CACHE_DIR + "/net/minecraft/minecraft/" + REPLACE_MC_VERSION + "/minecraft-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_SERVER_FRESH = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_MERGED       = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_merged/" + REPLACE_MC_VERSION + "/minecraft_merged-" + REPLACE_MC_VERSION + ".jar";
    public static final String JAR_SERVER_PURE  = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + "-pure.jar";
    public static final String JAR_SERVER_DEPS  = REPLACE_CACHE_DIR + "/net/minecraft/minecraft_server/" + REPLACE_MC_VERSION + "/minecraft_server-" + REPLACE_MC_VERSION + "-deps.jar";
    public static final String DIR_NATIVES      = REPLACE_CACHE_DIR + "/net/minecraft/natives/" + REPLACE_MC_VERSION + "/";
    public static final String JAR_FERNFLOWER   = REPLACE_CACHE_DIR + "/fernflower-fixed.jar";
    public static final String DIR_ASSETS       = REPLACE_CACHE_DIR + "/assets";
    public static final String JSON_ASSET_INDEX = DIR_ASSETS + "/indexes/" + REPLACE_ASSET_INDEX + ".json";
    public static final String DIR_JSONS        = REPLACE_CACHE_DIR + "/versionJsons";
    public static final String JSON_VERSION     = DIR_JSONS + "/" + REPLACE_MC_VERSION + ".json";

    public static final String GRADLE_START_CLIENT = "GradleStart";
    public static final String GRADLE_START_SERVER = "GradleStartServer";

    public static final String[] GRADLE_START_RESOURCES = new String[] {
            "GradleStart.java",
            "GradleStartServer.java",
            "net/minecraftforge/gradle/GradleStartCommon.java",

            // 1.7.10 only
            //makeStart.addResource("net/minecraftforge/gradle/OldPropertyMapSerializer.java");
    };

    public static final String[] GRADLE_START_FML_RES = new String[] {
            "net/minecraftforge/gradle/GradleForgeHacks.java",
            "net/minecraftforge/gradle/tweakers/CoremodTweaker.java",
            "net/minecraftforge/gradle/tweakers/AccessTransformerTweaker.java"
    };

    // mcp data constants
    public static final String MCP_DATA_SRG       = DIR_MCP_DATA + "/joined.srg";
    public static final String MCP_DATA_EXC       = DIR_MCP_DATA + "/joined.exc";
    public static final String MCP_DATA_EXC_JSON  = DIR_MCP_DATA + "/exceptor.json";
    public static final String MCP_DATA_STYLE     = DIR_MCP_DATA + "/astyle.cfg";
    public static final String MCP_DATA_STATICS   = DIR_MCP_DATA + "/static_methods.txt";
    public static final String MCP_PATCHES_CLIENT = DIR_MCP_DATA + "/patches/minecraft_ff";
    public static final String MCP_PATCHES_SERVER = DIR_MCP_DATA + "/patches/minecraft_server_ff";
    public static final String MCP_PATCHES_MERGED = DIR_MCP_DATA + "/patches/minecraft_merged_ff";
    public static final String MCP_INJECT         = DIR_MCP_DATA + "/patches/inject";

    // generated off of MCP data constants
    public static final String CSV_METHOD       = DIR_MCP_MAPPINGS + "/methods.csv";
    public static final String CSV_FIELD        = DIR_MCP_MAPPINGS + "/fields.csv";
    public static final String CSV_PARAM        = DIR_MCP_MAPPINGS + "/params.csv";
    public static final String SRG_NOTCH_TO_SRG = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/notch-srg.srg";
    public static final String SRG_NOTCH_TO_MCP = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/notch-mcp.srg";
    public static final String SRG_SRG_TO_MCP   = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/srg-mcp.srg";
    public static final String SRG_MCP_TO_SRG   = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/mcp-srg.srg";
    public static final String SRG_MCP_TO_NOTCH = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/mcp-notch.srg";
    public static final String EXC_SRG          = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/srg.exc";
    public static final String EXC_MCP          = DIR_MCP_MAPPINGS + "/" + REPLACE_MC_VERSION + "/srgs/mcp.exc";

    // task names
    public static final String TASK_DL_CLIENT        = "downloadClient";
    public static final String TASK_DL_SERVER        = "downloadServer";
    public static final String TASK_SPLIT_SERVER     = "splitServerJar";
    public static final String TASK_MERGE_JARS       = "mergeJars";
    public static final String TASK_EXTRACT_NATIVES  = "extractNatives";
    public static final String TASK_DL_VERSION_JSON  = "getVersionJson";
    public static final String TASK_DL_ASSET_INDEX   = "getAssetIndex";
    public static final String TASK_DL_ASSETS        = "getAssets";
    public static final String TASK_EXTRACT_MCP      = "extractMcpData";
    public static final String TASK_EXTRACT_MAPPINGS = "extractMcpMappings";
    public static final String TASK_GENERATE_SRGS    = "genSrgs";
    public static final String TASK_CLEAN_CACHE      = "cleanCache";

    // util
    public static final String NEWLINE = System.getProperty("line.separator");

    // helper methods
    public static List<String> getClassPath()
    {
        URL[] urls = ((URLClassLoader) PatcherExtension.class.getClassLoader()).getURLs();

        ArrayList<String> list = new ArrayList<String>();
        for (URL url : urls)
        {
            list.add(url.getPath());
        }
        return list;
    }

    public static URL[] toUrls(FileCollection collection) throws MalformedURLException
    {
        ArrayList<URL> urls = new ArrayList<URL>();

        for (File file : collection.getFiles())
            urls.add(file.toURI().toURL());

        return urls.toArray(new URL[urls.size()]);
    }

    public static File getMinecraftDirectory()
    {
        String userDir = System.getProperty("user.home");

        switch (OPERATING_SYSTEM)
            {
                case LINUX:
                    return new File(userDir, ".minecraft");
                case WINDOWS:
                    String appData = System.getenv("APPDATA");
                    String folder = appData != null ? appData : userDir;
                    return new File(folder, ".minecraft");
                case OSX:
                    return new File(userDir, "Library/Application Support/minecraft");
                default:
                    return new File(userDir, "minecraft");
            }
    }

    private static SystemArch getArch()
    {
        String name = lower(System.getProperty("os.arch"));
        if (name.contains("64"))
        {
            return SystemArch.BIT_64;
        }
        else
        {
            return SystemArch.BIT_32;
        }
    }

    public static String lower(String string)
    {
        return string.toLowerCase(Locale.ENGLISH);
    }

    public static List<String> lines(final String text)
    {
        try
        {
            return ImmutableList.copyOf(CharStreams.readLines(new StringReader(text)));
        }
        catch (IOException e)
        {
            // IMPOSSIBRU
            return ImmutableList.of();
        }
    }

    /**
     * This method constructs,, configures and returns a CSV reader instance to be used to read MCP CSV files.
     * @param file File to read
     * @return a configured CSVReader
     * @throws IOException Propogated from openning the file
     */
    public static CSVReader getReader(File file) throws IOException
    {
        return new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER, 1, false);
    }

    public static Element addXml(Node parent, String name, Map<String, String> values)
    {
        Document doc = parent.getOwnerDocument();
        if (doc == null)
            doc = (Document) parent;

        Element e = doc.createElement(name);
        for (Entry<String, String> entry : values.entrySet())
        {
            e.setAttribute(entry.getKey(), entry.getValue());
        }
        parent.appendChild(e);
        return e;
    }

    /**
     * This method uses the channels API which uses direct filesystem copies instead of loading it into
     * ram and then outputting it.
     * @param in file to copy
     * @param out created with directories if needed
     * @throws IOException In case anything goes wrong with the file IO
     */
    public static void copyFile(File in, File out) throws IOException
    {
        // make dirs just in case
        out.getParentFile().mkdirs();

        try (FileInputStream fis = new FileInputStream(in);
             FileOutputStream fout = new FileOutputStream(out);
             FileChannel source = fis.getChannel();
             FileChannel dest = fout.getChannel())
        {
            long size = source.size();
            source.transferTo(0, size, dest);
        }
    }

    /**
     * This method uses the channels API which uses direct filesystem copies instead of loading it into
     * ram and then outputting it.
     * @param in file to copy
     * @param out created with directories if needed
     * @param size If you have it earlier
     * @throws IOException In case anything goes wrong with the file IO
     */
    public static void copyFile(File in, File out, long size) throws IOException
    {
        // make dirs just in case
        out.getParentFile().mkdirs();

        try (FileInputStream fis = new FileInputStream(in);
             FileOutputStream fout = new FileOutputStream(out);
             FileChannel source = fis.getChannel();
             FileChannel dest = fout.getChannel())
        {
            source.transferTo(0, size, dest);
        }
    }

    public static String hash(File file)
    {
        if (file.getPath().endsWith(".zip") || file.getPath().endsWith(".jar"))
            return hashZip(file, HASH_FUNC);
        else
            return hash(file, HASH_FUNC);
    }

    public static List<String> hashAll(File file)
    {
        LinkedList<String> list = new LinkedList<String>();

        if (file.isDirectory())
        {
            for (File f : file.listFiles())
                list.addAll(hashAll(f));
        }
        else if (!file.getName().equals(".cache"))
            list.add(hash(file));

        return list;
    }

    public static String hash(File file, String function)
    {
        try
        {
            InputStream fis = new FileInputStream(file);
            byte[] array = ByteStreams.toByteArray(fis);
            fis.close();

            return hash(array, function);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static String hashZip(File file, String function)
    {
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(file)))
        {
            MessageDigest hasher = MessageDigest.getInstance(function);
            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null)
            {
                hasher.update(entry.getName().getBytes());
                hasher.update(ByteStreams.toByteArray(zin));
            }
            zin.close();

            byte[] hash = hasher.digest();

            // convert to string
            String result = "";

            for (int i = 0; i < hash.length; i++)
            {
                result += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
            }
            return result;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static String hash(String str)
    {
        return hash(str.getBytes());
    }

    public static String hash(byte[] bytes)
    {
        return hash(bytes, HASH_FUNC);
    }

    public static String hash(byte[] bytes, String function)
    {
        try
        {
            MessageDigest complete = MessageDigest.getInstance(function);
            byte[] hash = complete.digest(bytes);

            String result = "";

            for (int i = 0; i < hash.length; i++)
            {
                result += Integer.toString((hash[i] & 0xff) + 0x100, 16).substring(1);
            }
            return result;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static File getTaskLogFile(Project project, String name)
    {
        final File taskLogs = new File(project.getBuildDir(), "taskLogs");
        taskLogs.mkdirs();
        final File logFile = new File(taskLogs, name);
        logFile.delete();//Delete the old log
        return logFile;
    }

    public static PrintStream getTaskLogStream(Project project, String name)
    {
        final File logFile = getTaskLogFile(project, name);
        try
        {
            return new PrintStream(logFile);
        }
        catch (FileNotFoundException ignored)
        {}
        return null;// Should never get to here
    }

    /**
     * Throws a null runtime exception if the resource isnt found.
     * @param resource String name of the resource your looking for
     * @return URL
     */
    public static URL getResource(String resource)
    {
        ClassLoader loader = BaseExtension.class.getClassLoader();

        if (loader == null)
            throw new RuntimeException("ClassLoader is null! IMPOSSIBRU");

        URL url = loader.getResource(resource);

        if (url == null)
            throw new RuntimeException("Resource " + resource + " not found");

        return url;
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
    public static String resolveString(Object obj)
    {
        if (obj == null)
            return null;

        // stop early if its the right type. no need to do more expensive checks
        if (obj instanceof String)
            return (String) obj;

        if (obj instanceof Closure)
            return resolveString(((Closure) obj).call());// yes recursive.
        if (obj instanceof Callable)
        {
            try
            {
                return resolveString(((Callable) obj).call());
            }
            catch (Exception e)
            {
                return null;
            }
        }
        else if (obj instanceof File)
            return ((File) obj).getAbsolutePath();

        // arrays
        else if (obj.getClass().isArray())
        {
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

        else
            return obj.toString();
    }
}
