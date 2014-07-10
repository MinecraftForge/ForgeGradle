import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;

import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.net.Proxy;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GradleStart
{
    private static final Logger LOGGER = LogManager.getLogger();

    public static final Gson GSON;
    static
    {
        GsonBuilder builder = new GsonBuilder();
        builder.enableComplexMapKeySerialization();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    public static void main(String[] args)
    {
        // set system variables for dev environment
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true");

        if (args.length == 0)
        {
            // empty args? start client with defaults
            LOGGER.info("No arguments specified, assuming client.");
            startClient(args);
        }

        // check the server
        if ("server".equalsIgnoreCase(args[0]) || "--server".equalsIgnoreCase(args[0])) // cant be 0, so it must be atleast 1 right?
        {
            throw new IllegalArgumentException("If you want to run a server, use GradleStartServer as your main class");
        }

        // not server, but has args? its client.
        startClient(args);
    }

    private static void startClient(String[] args)
    {
        GradleStart cArgs = new GradleStart();
        cArgs.parseArgs(args);

        if (!Strings.isNullOrEmpty(cArgs.values.get("password")))
        {
            LOGGER.info("Password found, attempting login");
            attemptLogin(cArgs);
        }

        if (!Strings.isNullOrEmpty(cArgs.values.get("assetIndex")))
        {
            cArgs.setupAssets();
        }

        args = cArgs.getArgs();

        LOGGER.info("Running with arguments: "+Arrays.toString(args));
        bounce("@@BOUNCERCLIENT@@", args);
    }

    private static void bounce(String mainClass, String[] args)
    {
        try {
            System.gc();// why not? it'll clean stuff up before starting MC.
            Class.forName(mainClass).getDeclaredMethod("main", String[].class).invoke(null, new Object[] {args});
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }
    }

    private static void attemptLogin(GradleStart args)
    {
        YggdrasilUserAuthentication auth = (YggdrasilUserAuthentication) new YggdrasilAuthenticationService(Proxy.NO_PROXY, "1").createUserAuthentication(Agent.MINECRAFT);
        auth.setUsername(args.values.get("username"));
        auth.setPassword(args.values.get("password"));
        args.values.put("password", null);

        try {
            auth.logIn();
        }
        catch (AuthenticationException e)
        {
            LOGGER.error("-- Login failed!  " + e.getMessage());
            Throwables.propagate(e);
            return; // dont set other variables
        }

        LOGGER.info("Login Succesful!");
        args.values.put("accessToken", auth.getAuthenticatedToken());
        args.values.put("uuid", auth.getSelectedProfile().getId().toString().replace("-", ""));
        args.values.put("username", auth.getSelectedProfile().getName());
        //@@USERTYPE@@
        args.values.put("userProperties", auth.getUserProperties().toString());
    }


    // THIS HERE IS THE ACTUAL CLASS
    // ----------------------------------------------
    private List<String> extras;
    private Map<String, String> values = new HashMap<String, String>();

    String[] getArgs()
    {
        ArrayList<String> list = new ArrayList<String>(22);

        for (Map.Entry<String, String> e : values.entrySet())
        {
            String val = e.getValue();
            if (!Strings.isNullOrEmpty(val))
            {
                list.add("--" + e.getKey());
                list.add(val);
            }
        }

        if (extras != null)
        {
            list.addAll(extras);
        }

        return list.toArray(new String[0]);
    }

    void parseArgs(String[] args)
    {
        values = new HashMap<String, String>();
        values.put("version",        "@@MCVERSION@@");
        values.put("tweakClass",     "cpw.mods.fml.common.launcher.FMLTweaker");
        values.put("assetIndex",     "@@ASSETINDEX@@");
        values.put("assetsDir",      "@@ASSETSDIR@@");
        values.put("accessToken",    "FML");
        values.put("userProperties", "{}");
        values.put("username",       "ForgeDevName");
        values.put("password",        null);

        final OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        for (String key : values.keySet())
        {
            parser.accepts(key).withRequiredArg().ofType(String.class);
        }

        final NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        final OptionSet options = parser.parse(args);
        for (String key : values.keySet())
        {
            if (options.hasArgument(key))
            {
                String value = (String)options.valueOf(key);
                values.put(key, value);
                LOGGER.info(key + ": " + value);
            }
        }

        extras = nonOption.values(options);
        LOGGER.info("Extra: " + extras);
    }

    private void setupAssets()
    {
        if (Strings.isNullOrEmpty(values.get("assetsDir")))
        {
            throw new RuntimeException("assetsDir is null when assetIndex is not! THIS IS BAD COMMAND LINE ARGUMENTS, fix them");
        }
        File assets = new File(values.get("assetsDir"));
        File objects = new File(assets, "objects");
        File assetIndex = new File(new File(assets, "indexes"), values.get("assetIndex") + ".json");
        try
        {
            AssetIndex index = loadAssetsIndex(assetIndex);
            if (!index.virtual)
                return;

            File assetVirtual = new File(new File(assets, "virtual"), values.get("assetIndex"));
            values.put("assetsDir", assetVirtual.getAbsolutePath());

            LOGGER.info("Setting up virtual assets in: " + assetVirtual.getAbsolutePath());

            Map<String, String> existing = gatherFiles(assetVirtual);

            for (Map.Entry<String, AssetIndex.AssetEntry> e : index.objects.entrySet())
            {
                String key = e.getKey();
                String hash = e.getValue().hash.toLowerCase();
                File virtual = new File(assetVirtual, key);
                File source = new File(new File(objects, hash.substring(0, 2)), hash);

                if (existing.containsKey(key))
                {
                    if (existing.get(key).equals(hash))
                    {
                        existing.remove(key);
                    }
                    else
                    {
                        LOGGER.info("  " + key + ": INVALID HASH");
                        virtual.delete();
                    }
                }
                else
                {
                    if (!source.exists())
                    {
                        LOGGER.info("  " + key + ": NEW MISSING " + hash);
                    }
                    else
                    {
                        LOGGER.info("  " + key + ": NEW ");
                        File parent = virtual.getParentFile();
                        if (!parent.exists())
                            parent.mkdirs();
                        Files.copy(source, virtual);
                    }
                }
            }

            for (String key : existing.keySet())
            {
                LOGGER.info("  " + key + ": REMOVED");
                File virtual = new File(assetVirtual, key);
                virtual.delete();
            }
        }
        catch (Throwable t)
        {
            Throwables.propagate(t);
        }
    }

    private AssetIndex loadAssetsIndex(File json) throws JsonSyntaxException, JsonIOException, IOException
    {
        FileReader reader = new FileReader(json);
        AssetIndex a =  GSON.fromJson(reader, AssetIndex.class);
        reader.close();
        return a;
    }

    public static class AssetIndex
    {
        public boolean            virtual;
        public Map<String, AssetEntry> objects;

        public static class AssetEntry
        {
            public String  hash;
        }
    }

    public static String getDigest(File file)
    {
        DigestInputStream input = null;
        try
        {
            input = new DigestInputStream(new FileInputStream(file), MessageDigest.getInstance("SHA"));
            byte[] buffer = new byte[65536];
            int read;
            do
            {
                read = input.read(buffer);
            } while (read > 0);
        }
        catch (Exception ignored)
        {
            return null;
        }
        finally
        {
            if (input != null)
            {
                try {
                    input.close();
                } catch (Exception e) {}
            }
        }
        return String.format("%1$040x", new BigInteger(1, input.getMessageDigest().digest()));
    }

    private Map<String, String> gatherFiles(File base)
    {
        Map<String, String> ret = new HashMap<String, String>();
        gatherDir(ret, base, base);
        return ret;
    }
    private void gatherDir(Map<String, String> map, File base, File target)
    {
        if (!target.exists() || !target.isDirectory())
            return;
        for (File f : target.listFiles())
        {
            if (f.isDirectory())
            {
                gatherDir(map, base, f);
            }
            else
            {
                String path = base.toURI().relativize(f.toURI()).getPath().replace("\\", "/");
                String checksum = getDigest(f).toLowerCase();
                map.put(path, checksum);
            }
        }
    }
}
