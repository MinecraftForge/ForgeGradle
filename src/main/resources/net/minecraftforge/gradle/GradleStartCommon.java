package net.minecraftforge.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
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
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

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
    
    private static final File SRG_DIR = new File("@@SRGDIR@@");
    private static final File CSV_DIR = new File("@@CSVDIR@@");

    protected abstract void setDefaultArguments(Map<String, String> argMap);
    protected abstract void preLaunch(Map<String, String> argMap, List<String> extras);
    protected abstract String getBounceClass();
    protected abstract String getTweakClass();
    
    protected void launch(String[] args) throws Throwable
    {
        // set system vars for passwords
        System.setProperty("net.minecraftforge.gradle.GradleStart.srgDir", SRG_DIR.getAbsolutePath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.csvDir", CSV_DIR.getAbsolutePath());
        
        // set defaults!
        setDefaultArguments(argMap);
        
        // parse stuff
        parseArgs(args);
        
        // now send it back for prelaunch
        preLaunch(argMap, extras);
        
        // because its the dev env.
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true"); // cant hurt. set it now.
        
        // coremod searching.
        if (argMap.get(NO_CORE_SEARCH) == null)
            searchCoremods();
        else
            LOGGER.info("GradleStart coremod searching disabled!");
        
        // now the actual launch args.
        args = getArgs();
        
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
    
    private String[] getArgs()
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
        
        // grab tweakClass
        if (!Strings.isNullOrEmpty(getTweakClass()))
        {
            list.add("--tweakClass");
            list.add(getTweakClass());
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

        extras = Lists.newArrayList(nonOption.values(options));
        LOGGER.info("Extra: " + extras);
    }
    
    /* -----------  REFLECTION HELPER  --------- */
    
    private static final String MC_VERSION = "@@MCVERSION@@";
    private static final String FML_PACK_OLD = "cpw.mods";
    private static final String FML_PACK_NEW = "net.minecraftforge";
    @SuppressWarnings("rawtypes")
    protected static Class getFmlClass(String classname) throws ClassNotFoundException
    {
        if (!classname.startsWith("fml")) // dummy check myself
            throw new IllegalArgumentException("invalid FML classname");
        
        if (MC_VERSION.startsWith("1.7"))
            classname = FML_PACK_OLD + "." + classname;
        else
            classname = FML_PACK_NEW + "." + classname;
        
        return Class.forName(classname);
    }
    
    /* -----------  COREMOD AND AT HACK  --------- */
    
    // coremod hack
    private static final String COREMOD_VAR = "fml.coreMods.load";
    private static final String COREMOD_MF  = "FMLCorePlugin";
    // AT hack
    private static final String AT_LOADER_CLASS    = "fml.common.asm.transformers.ModAccessTransformer";
    private static final String AT_LOADER_METHOD   = "addJar";
    
    private static final Map<String, File> coreMap = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    private void searchCoremods() throws Exception
    {
        // intialize AT hack Method
        Method atRegistrar = null;
        try{
            atRegistrar = getFmlClass(AT_LOADER_CLASS).getDeclaredMethod(AT_LOADER_METHOD, JarFile.class);
        }
        catch(Throwable t) { }
        
        for (URL url : ((URLClassLoader) GradleStartCommon.class.getClassLoader()).getURLs())
        {
            if (!url.getProtocol().startsWith("file")) // because file urls start with file://
                continue; //         this isnt a file
            
            File coreMod = new File(url.toURI().getPath());
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
                    coreMap.put(clazz, coreMod);
                }
            }
        }
        
        // set property.
        Set<String> coremodsSet = Sets.newHashSet();
        if (!Strings.isNullOrEmpty(System.getProperty(COREMOD_VAR)))
            coremodsSet.addAll(Splitter.on(',').splitToList(System.getProperty(COREMOD_VAR)));
        coremodsSet.addAll(coreMap.keySet());
        System.setProperty(COREMOD_VAR, Joiner.on(',').join(coremodsSet));
        
        // ok.. tweaker hack now.
        if (!Strings.isNullOrEmpty(getTweakClass()))
        {
            extras.add("--tweakClass");
            extras.add(GradleStartTweaker.class.getName());
        }
    }
    
    /* -----------  CUSTOM TWEAKER FOR COREMOD HACK  --------- */
    
    public static final class GradleStartTweaker implements ITweaker
    {

        @Override
        public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) { }

        @SuppressWarnings("unchecked")
        @Override
        public void injectIntoClassLoader(LaunchClassLoader classLoader)
        {
            try
            {
                Field coreModList = getFmlClass("fml.relauncher.CoreModManager").getDeclaredField("loadPlugins");
                coreModList.setAccessible(true);
                
                // grab constructor.
                Class<ITweaker> clazz = (Class<ITweaker>) getFmlClass("fml.relauncher.CoreModManager$FMLPluginWrapper");
                Constructor<ITweaker> construct = (Constructor<ITweaker>) clazz.getConstructors()[0];
                construct.setAccessible(true);
                
                Field[] fields = clazz.getDeclaredFields();
                Field pluginField = fields[1];  // 1
                Field fileField = fields[3];  // 3
                Field listField = fields[2];  // 2
                
                Field.setAccessible(clazz.getConstructors(), true);
                Field.setAccessible(fields, true);
                
                List<ITweaker> oldList = (List<ITweaker>) coreModList.get(null);
                
                for (int i = 0; i < oldList.size(); i++)
                {
                    ITweaker tweaker = oldList.get(i);
                    
                    if (clazz.isInstance(tweaker))
                    {
                        Object coreMod = pluginField.get(tweaker);
                        Object oldFile = fileField.get(tweaker);
                        File newFile = coreMap.get(coreMod.getClass().getCanonicalName());
                        
                        LOGGER.info("Injecting location in coremod {}", coreMod.getClass().getCanonicalName());
                        
                        if (newFile != null && oldFile == null)
                        {
                            // build new tweaker.
                            oldList.set(i, construct.newInstance(new Object[] {
                                    (String)fields[0].get(tweaker), // name
                                    coreMod, // coremod
                                    newFile, // location
                                    fields[4].getInt(tweaker), // sort index?
                                    ((List<String>)listField.get(tweaker)).toArray(new String[0])
                            }));
                        }
                    }
                }
            }
            catch (Throwable t)
            {
                LOGGER.error("Something went wrong with the claslaoding. No biggy.");
                t.printStackTrace();
            }
        }

        @Override
        public String getLaunchTarget()
        {
            // if it gets here... something went terribly wrong..
            return null;
        }

        @Override
        public String[] getLaunchArguments()
        {
            // if it gets here... something went terribly wrong.
            return new String[0];
        }
        
    }
}
