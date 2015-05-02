package net.minecraftforge.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public abstract class GradleStartCommon
{
    protected static Logger     LOGGER          = LogManager.getLogger("GradleStart");
    private static final String NO_CORE_SEARCH  = "noCoreSearch";

    private Map<String, String> argMap          = Maps.newHashMap();
    private List<String>        extras          = Lists.newArrayList();

    private static final File   SRG_DIR         = new File("@@SRGDIR@@");
    private static final File   SRG_NOTCH_SRG   = new File("@@SRG_NOTCH_SRG@@");
    private static final File   SRG_NOTCH_MCP   = new File("@@SRG_NOTCH_MCP@@");
    private static final File   SRG_SRG_MCP     = new File("@@SRG_SRG_MCP@@");
    private static final File   SRG_MCP_SRG     = new File("@@SRG_MCP_SRG@@");
    private static final File   SRG_MCP_NOTCH   = new File("@@SRG_MCP_NOTCH@@");
    private static final File   CSV_DIR         = new File("@@CSVDIR@@");

    protected abstract void setDefaultArguments(Map<String, String> argMap);

    protected abstract void preLaunch(Map<String, String> argMap, List<String> extras);

    protected abstract String getBounceClass();

    protected abstract String getTweakClass();

    protected void launch(String[] args) throws Throwable
    {
        // DEPRECATED, use the properties below instead!
        System.setProperty("net.minecraftforge.gradle.GradleStart.srgDir", SRG_DIR.getCanonicalPath());
        
        // set system vars for passwords
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-srg", SRG_NOTCH_SRG.getCanonicalPath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.notch-mcp", SRG_NOTCH_MCP.getCanonicalPath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.srg-mcp", SRG_SRG_MCP.getCanonicalPath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-srg", SRG_MCP_SRG.getCanonicalPath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.srg.mcp-notch", SRG_MCP_NOTCH.getCanonicalPath());
        System.setProperty("net.minecraftforge.gradle.GradleStart.csvDir", CSV_DIR.getCanonicalPath());

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

        String[] out = list.toArray(new String[0]);

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
                String value = (String) options.valueOf(key);
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

    /* ----------- REFLECTION HELPER --------- */

    private static final String MC_VERSION   = "@@MCVERSION@@";
    private static final String FML_PACK_OLD = "cpw.mods";
    private static final String FML_PACK_NEW = "net.minecraftforge";

    @SuppressWarnings("rawtypes")
    protected static Class getFmlClass(String classname) throws ClassNotFoundException
    {
        return getFmlClass(classname, GradleStartCommon.class.getClassLoader());
    }

    @SuppressWarnings("rawtypes")
    public static Class getFmlClass(String classname, ClassLoader loader) throws ClassNotFoundException
    {
        if (!classname.startsWith("fml")) // dummy check myself
            throw new IllegalArgumentException("invalid FML classname");

        if (MC_VERSION.startsWith("1.7"))
            classname = FML_PACK_OLD + "." + classname;
        else
            classname = FML_PACK_NEW + "." + classname;

        return Class.forName(classname, true, loader);
    }

    /* ----------- COREMOD AND AT HACK --------- */

    // coremod hack
    private static final String           COREMOD_VAR   = "fml.coreMods.load";
    private static final String           COREMOD_MF    = "FMLCorePlugin";
    // AT hack
    private static final String           MOD_ATD_CLASS = "fml.common.asm.transformers.ModAccessTransformer";
    private static final String           MOD_AT_METHOD = "addJar";

    public static final Map<String, File> coreMap       = Maps.newHashMap();

    @SuppressWarnings("unchecked")
    private void searchCoremods() throws Exception
    {
        // intialize AT hack Method
        Method atRegistrar = null;
        try
        {
            atRegistrar = getFmlClass(MOD_ATD_CLASS).getDeclaredMethod(MOD_AT_METHOD, JarFile.class);
        }
        catch (Throwable t)
        {}

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
                if (atRegistrar != null && manifest != null)
                    atRegistrar.invoke(null, jar);
                jar.close();
            }

            // we got the manifest? use it.
            if (manifest != null)
            {
                String clazz = manifest.getMainAttributes().getValue(COREMOD_MF);
                if (!Strings.isNullOrEmpty(clazz))
                {
                    LOGGER.info("Found and added coremod: " + clazz);
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
            extras.add("net.minecraftforge.gradle.tweakers.CoremodTweaker");
        }
    }

    /* ----------- CUSTOM TWEAKER FOR COREMOD HACK --------- */

    // here and not in the tweaker package because classloader hell
    public static final class AccessTransformerTransformer implements IClassTransformer
    {
        public AccessTransformerTransformer()
        {
            doStuff((LaunchClassLoader) getClass().getClassLoader());
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private void doStuff(LaunchClassLoader classloader)
        {
            // the class and instance of ModAccessTransformer
            Class<? extends IClassTransformer> clazz = null;
            IClassTransformer instance = null;

            // find the instance I want. AND grab the type too, since thats better than Class.forName()
            for (IClassTransformer transformer : classloader.getTransformers())
            {
                if (transformer.getClass().getCanonicalName().endsWith(MOD_ATD_CLASS))
                {
                    clazz = transformer.getClass();
                    instance = transformer;
                }
            }

            // impossible! but i will ignore it.
            if (clazz == null || instance == null)
            {
                LOGGER.log(Level.ERROR, "ModAccessTransformer was somehow not found.");
                return;
            }

            // grab the list of Modifiers I wanna mess with
            Collection<Object> modifiers = null;
            try
            {
                // super class of ModAccessTransformer is AccessTransformer
                Field f = clazz.getSuperclass().getDeclaredFields()[1]; // its the modifiers map. Only non-static field there.
                f.setAccessible(true);

                modifiers = ((com.google.common.collect.Multimap) f.get(instance)).values();
            }
            catch (Throwable t)
            {
                LOGGER.log(Level.ERROR, "AccessTransformer.modifiers field was somehow not found...");
                return;
            }

            if (modifiers.isEmpty())
            {
                return; // hell no am I gonna do stuff if its empty..
            }

            // grab the field I wanna hack
            Field nameField = null;
            try
            {
                // get 1 from the collection
                Object mod = null;
                for (Object val : modifiers)
                {
                    mod = val;
                    break;
                } // i wish this was cleaner

                nameField = mod.getClass().getFields()[0]; // first field. "name"
                nameField.setAccessible(true); // its alreadypublic, but just in case
            }
            catch (Throwable t)
            {
                LOGGER.log(Level.ERROR, "AccessTransformer.Modifier.name field was somehow not found...");
                return;
            }

            // read the field and method CSV files.
            Map<String, String> nameMap = Maps.newHashMap();
            try
            {
                readCsv(new File(CSV_DIR, "fields.csv"), nameMap);
                readCsv(new File(CSV_DIR, "methods.csv"), nameMap);
            }
            catch (IOException e)
            {
                // If I cant find these.. something is terribly wrong.
                LOGGER.log(Level.ERROR, "Could not load CSV files!");
                e.printStackTrace();
                return;
            }

            LOGGER.log(Level.INFO, "Remapping AccessTransformer rules...");

            // finally hit the modifiers
            for (Object modifier : modifiers) // these are instances of AccessTransformer.Modifier
            {
                String name;
                try
                {
                    name = (String) nameField.get(modifier);
                    String newName = nameMap.get(name);
                    if (newName != null)
                    {
                        nameField.set(modifier, newName);
                    }
                }
                catch (Exception e)
                {
                    // impossible. It would have failed earlier if possible.
                }
            }
        }

        private void readCsv(File file, Map<String, String> map) throws IOException
        {
            LOGGER.log(Level.DEBUG, "Reading CSV file: {}", file);
            Splitter split = Splitter.on(',').trimResults().limit(3);
            for (String line : Files.readLines(file, Charsets.UTF_8))
            {
                if (line.startsWith("searge")) // header line
                    continue;

                List<String> splits = split.splitToList(line);
                map.put(splits.get(0), splits.get(1));
            }
        }

        @Override
        public byte[] transform(String name, String transformedName, byte[] basicClass)
        {
            return basicClass; // nothing here
        }
    }
}
