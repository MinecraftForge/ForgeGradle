import com.google.common.base.Throwables;

public class GradleStartServer
{
    public static void main(String[] args)
    {
        // set system variables for dev environment
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true");
        
        // no defaults here.. so.. uh,... yeah...
        bounce("@@BOUNCERSERVER@@", args);
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
}
