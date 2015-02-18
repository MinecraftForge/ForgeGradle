import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.Proxy;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.GradleStartCommon;

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

public class GradleStart extends GradleStartCommon
{
    private static final Gson GSON;
    static
    {
        GsonBuilder builder = new GsonBuilder();
        builder.enableComplexMapKeySerialization();
        builder.setPrettyPrinting();
        GSON = builder.create();
    }

    public static void main(String[] args) throws Throwable
    {
        // hack natives.
        hackNatives();
        
        // launch
        (new GradleStart()).launch(args);
    }
    
    @Override
    protected String getBounceClass()
    {
        return "@@BOUNCERCLIENT@@";
    }
    
    @Override
    protected String getTweakClass()
    {
        return "@@CLIENTTWEAKER@@";
    }
    
    @Override
    protected void setDefaultArguments(Map<String, String> argMap)
    {
        argMap.put("version",        "@@MCVERSION@@");
        argMap.put("assetIndex",     "@@ASSETINDEX@@");
        argMap.put("assetsDir",      "@@ASSETSDIR@@");
        argMap.put("accessToken",    "FML");
        argMap.put("userProperties", "{}");
        argMap.put("username",        null);
        argMap.put("password",        null);
    }

    @Override
    protected void preLaunch(Map<String, String> argMap, List<String> extras)
    {
        if (!Strings.isNullOrEmpty(argMap.get("password")))
        {
            GradleStartCommon.LOGGER.info("Password found, attempting login");
            attemptLogin(argMap);
        }

        if (!Strings.isNullOrEmpty(argMap.get("assetIndex")))
        {
            setupAssets(argMap);
        }
    }

    private static void hackNatives()
    {
        String paths = System.getProperty("java.library.path");
        String nativesDir = "@@NATIVESDIR@@";
        
        if (Strings.isNullOrEmpty(paths))
            paths = nativesDir;
        else
            paths += File.pathSeparator + nativesDir;
        
        System.setProperty("java.library.path", paths);
        
        // hack the classloader now.
        try
        {
            final Field sysPathsField = ClassLoader.class.getDeclaredField("sys_paths");
            sysPathsField.setAccessible(true);
            sysPathsField.set(null, null);
        }
        catch(Throwable t) {};
    }

    private void attemptLogin(Map<String, String> argMap)
    {
        YggdrasilUserAuthentication auth = (YggdrasilUserAuthentication) new YggdrasilAuthenticationService(Proxy.NO_PROXY, "1").createUserAuthentication(Agent.MINECRAFT);
        auth.setUsername(argMap.get("username"));
        auth.setPassword(argMap.get("password"));
        argMap.put("password", null);

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
        argMap.put("accessToken", auth.getAuthenticatedToken());
        argMap.put("uuid", auth.getSelectedProfile().getId().toString().replace("-", ""));
        argMap.put("username", auth.getSelectedProfile().getName());
        //@@USERTYPE@@
        //@@USERPROP@@
    }
    

    private void setupAssets(Map<String, String> argMap)
    {
        if (Strings.isNullOrEmpty(argMap.get("assetsDir")))
        {
            throw new IllegalArgumentException("assetsDir is null when assetIndex is not! THIS IS BAD COMMAND LINE ARGUMENTS, fix them");
        }
        File assets = new File(argMap.get("assetsDir"));
        File objects = new File(assets, "objects");
        File assetIndex = new File(new File(assets, "indexes"), argMap.get("assetIndex") + ".json");
        try
        {
            AssetIndex index = loadAssetsIndex(assetIndex);
            if (!index.virtual)
                return;

            File assetVirtual = new File(new File(assets, "virtual"), argMap.get("assetIndex"));
            argMap.put("assetsDir", assetVirtual.getAbsolutePath());

            GradleStartCommon.LOGGER.info("Setting up virtual assets in: " + assetVirtual.getAbsolutePath());

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
                        GradleStartCommon.LOGGER.info("  " + key + ": INVALID HASH");
                        virtual.delete();
                    }
                }
                else
                {
                    if (!source.exists())
                    {
                        GradleStartCommon.LOGGER.info("  " + key + ": NEW MISSING " + hash);
                    }
                    else
                    {
                        GradleStartCommon.LOGGER.info("  " + key + ": NEW ");
                        File parent = virtual.getParentFile();
                        if (!parent.exists())
                            parent.mkdirs();
                        Files.copy(source, virtual);
                    }
                }
            }

            for (String key : existing.keySet())
            {
                GradleStartCommon.LOGGER.info("  " + key + ": REMOVED");
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

    private static class AssetIndex
    {
        public boolean            virtual;
        public Map<String, AssetEntry> objects;

        public static class AssetEntry
        {
            public String  hash;
        }
    }

    private String getDigest(File file)
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
