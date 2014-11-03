public class GradleStartServer
{
    public static void main(String[] args)
    {
        // set system variables for dev environment
        System.setProperty("fml.ignoreInvalidMinecraftCertificates", "true");
        
        // no defaults here.. so.. uh,... yeah...
        GradleStartCommon.launch("@@BOUNCERSERVER@@", args);
    }
}
