import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.launchwrapper.Launch;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public abstract class GradleStartCommon
{
    protected static Logger LOGGER = LogManager.getLogger("GradleStart");
    private static final String NO_CORE_SEARCH = "noCoreSearch";

    private Map<String, String> argMap = Maps.newHashMap(); 
    private List<String> extras = Lists.newArrayList();

    abstract void setDefaultArguments(Map<String, String> argMap);
    abstract void preLaunch(Map<String, String> argMap, List<String> extras);
    abstract String getBounceClass();
    
    protected void launch(String[] args) throws Throwable
    {
        // set defaults!
        setDefaultArguments(argMap);
        
        // parse stuff
        parseArgs(args);
        
        // now send it back for prelaunch
        preLaunch(argMap, extras);
        
        // because its teh ev env.
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true"); // cant hurt. set it now.
        
        // coremod searching.
        if (argMap.get(NO_CORE_SEARCH) == null)
            searchCoremods();
        else
            LOGGER.info("GradleStart coremod searching disabl;ed!");
        
        // now the actual launch args.
        args = mapToArgs(argMap, extras);
        
        // clear it out
        argMap = null;
        extras = null;

        // launch.
        System.gc();
        String bounce = getBounceClass(); // marginally faster. And we need the launch wrapper anyways.
        if (bounce.endsWith("launchwrapper.Launch"))
            Launch.main(args);
        else
            Class.forName(getBounceClass()).getDeclaredMethod("main", String[].class).invoke(null, new Object[] { args });
    }
    
    private static String[] mapToArgs(Map<String, String> argMap, List<String> extras)
    {
        ArrayList<String> list = new ArrayList<String>(22);

        for (Map.Entry<String, String> e : argMap.entrySet())
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

        String[] out =  list.toArray(new String[0]);
        
        // final logging.
        StringBuilder b = new StringBuilder();
        b.append('[');
        for (int x = 0; x < out.length; x++)
        {
            b.append(out[x]).append(", ");
            if ("--accessToken".equalsIgnoreCase(out[x]))
            {
                b.append("{REDACTED}, ");
                x++;
            }
        }
        b.replace(b.length() - 2, b.length(), "");
        b.append(']');
        GradleStartCommon.LOGGER.info("Running with arguments: " + b.toString());
        
        return out;
    }
    
    private void parseArgs(String[] args)
    {
        final OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        for (String key : argMap.keySet())
        {
            parser.accepts(key).withRequiredArg().ofType(String.class);
        }
        // accept the noCoreSearch thing
        parser.accepts(NO_CORE_SEARCH);

        final NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        final OptionSet options = parser.parse(args);
        for (String key : argMap.keySet())
        {
            if (options.hasArgument(key))
            {
                String value = (String)options.valueOf(key);
                argMap.put(key, value);
                if (!"password".equalsIgnoreCase(key))
                    LOGGER.info(key + ": " + value);
            }
        }
        
        if (options.has(NO_CORE_SEARCH))
            argMap.put(NO_CORE_SEARCH, "");

        extras = nonOption.values(options);
        LOGGER.info("Extra: " + extras);
    }
    
    // coremod hack
    private static final String COREMOD_VAR = "fml.coreMods.load";
    private static final String COREMOD_MF  = "FMLCorePlugin";
    // AT hack
    private static final String AT_LOADER_CLASS    = "cpw.mods.fml.common.asm.transformers.ModAccessTransformer";
    private static final String AT_LOADER_CLASS_18 = "net.minecraftforge.fml.common.asm.transformers.ModAccessTransformer";
    private static final String AT_LOADER_METHOD   = "addJar";

    public static void searchCoremods() throws Exception
    {
        // intialize AT hack Method
        Method atRegistrar = null;
        try{
            atRegistrar = Class.forName("@@MCVERSION@@".startsWith("1.7") ? AT_LOADER_CLASS : AT_LOADER_CLASS_18)
                    .getDeclaredMethod(AT_LOADER_METHOD, JarFile.class);
        }
        catch(Throwable t) { }
        
        Set<String> coremodsSet = Sets.newHashSet();
        
        if (!Strings.isNullOrEmpty(System.getProperty(COREMOD_VAR)))
            coremodsSet.addAll(Splitter.on(',').splitToList(System.getProperty(COREMOD_VAR)));
        
        for (URL url : ((URLClassLoader) GradleStartCommon.class.getClassLoader()).getURLs())
        {
            if (!url.getProtocol().startsWith("file")) // because file urls start with file://
                continue; // this isnt a file
            
            File coreMod = new File(url.getFile());
            Manifest manifest = null;
            
            if (!coreMod.exists())
                continue;
            
            if (coreMod.isDirectory())
            {
                File manifestMF = new File(coreMod, "META-INF/MANIFEST.MF");
                if (manifestMF.exists())
                {
                    FileInputStream stream = new FileInputStream(manifestMF);
                    manifest = new Manifest(stream);
                    stream.close();
                }
            }
            else if (coreMod.getName().endsWith("jar")) // its a jar
            {
                JarFile jar = new JarFile(coreMod);
                manifest = jar.getManifest();
                if (atRegistrar != null && manifest != null) atRegistrar.invoke(null, jar);
                jar.close();
            }
            
            // we got the manifest? use it.
            if (manifest != null)
            {
                String clazz = manifest.getMainAttributes().getValue(COREMOD_MF);
                if (!Strings.isNullOrEmpty(clazz))
                {
                    LOGGER.info("Found and added coremod: "+clazz);
                    coremodsSet.add(clazz);
                }
            }
        }
        
        System.setProperty(COREMOD_VAR, Joiner.on(',').join(coremodsSet));
    }
}