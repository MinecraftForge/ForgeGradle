import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.mojang.authlib.Agent;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import com.mojang.authlib.yggdrasil.YggdrasilUserAuthentication;
import joptsimple.NonOptionArgumentSpec;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GradleStart
{
    private static final Logger LOGGER =LogManager.getLogger();

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

        if (!Strings.isNullOrEmpty(cArgs.password))
        {
            LOGGER.info("Password found, attempting login");
            attemptLogin(cArgs);
        }

        args = cArgs.getArgs();

        System.gc(); // why not? itl clean stuff up before starting MC.
        LOGGER.info("Running with arguments: "+Arrays.toString(args));
        bounce("@@BOUNCERCLIENT@@", args);
    }

    private static void bounce(String mainClass, String[] args)
    {
        try {
            System.gc();
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
        auth.setUsername(args.username);
        auth.setPassword(args.password);

        try {
            auth.logIn();
        }
        catch (AuthenticationException e)
        {
            LOGGER.error("-- Login failed!  " + e.getMessage());
            return; // dont set other variables
        }

        LOGGER.info("Login Succesful!");
        args.accessToken = auth.getAuthenticatedToken();
        args.uuid = auth.getUserID();
        @@USERTYPE@@
        args.userProperties = auth.getUserProperties().toString();
    }


    // THIS EHRE ACTUAL CLASS
    String version = "@@MCVERSION@@";
    String tweakClass = "cpw.mods.fml.common.launcher.FMLTweaker";
    String assetIndex = "@@ASSETINDEX@@";
    String assetsDir = "@@ASSETSDIR@@";
    String accessToken = "FML";
    String userProperties = "{}";
    String username = "ForgeDevName";
    String password, uuid, gameDir, userType;
    List<String> extras;

    String[] getArgs()
    {
        ArrayList<String> list = new ArrayList<String>(22);

        try
        {
            String val;
            for (Field f : GradleStart.class.getDeclaredFields())
            {
                // dont use that one
                if (f.getName().equalsIgnoreCase("extras") || f.getName().equalsIgnoreCase("LOGGER"))
                    continue;

                val = (String) f.get(this);
                if (!Strings.isNullOrEmpty(val)) {
                    list.add("--" + f.getName());
                    list.add(val);
                }
            }
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }

        if (extras != null)
        {
            list.addAll(extras);
        }

        return list.toArray(new String[0]);
    }

    void parseArgs(String[] args)
    {
        final OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();

        final OptionSpec<String> usernameOpt = parser.accepts("username", "the username").withRequiredArg().ofType(String.class).defaultsTo(username);
        final OptionSpec<String> passwordOpt = parser.accepts("password", "the password").withRequiredArg().ofType(String.class);
        final NonOptionArgumentSpec<String> nonOption = parser.nonOptions();

        final OptionSet options = parser.parse(args);

        username = usernameOpt.value(options);
        password = passwordOpt.value(options);
        extras = nonOption.values(options);
    }
}
