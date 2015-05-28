import java.util.List;
import java.util.Map;

import com.google.common.base.Strings;

import net.minecraftforge.gradle.GradleStartCommon;

public class GradleStartServer extends GradleStartCommon
{
    public static void main(String[] args) throws Throwable
    {
        (new GradleStartServer()).launch(args);
    }
    
    @Override
    protected String getTweakClass()
    {
        return "@@TWEAKERSERVER@@";
    }
    
    @Override
    protected String getBounceClass()
    {
       String bounce = "@@BOUNCERSERVER@@";
       
       if (Strings.isNullOrEmpty(bounce))
       {
           // default MC server class launch class
           return "net.minecraft.server.MinecraftServer";
       }
       else
       {
           return bounce;
       }
    }

    @Override protected void preLaunch(Map<String, String> argMap, List<String> extras) { }
    @Override protected void setDefaultArguments(Map<String, String> argMap) { }
}
