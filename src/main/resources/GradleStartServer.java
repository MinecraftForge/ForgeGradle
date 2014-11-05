import java.util.List;
import java.util.Map;

public class GradleStartServer extends GradleStartCommon
{
    public static void main(String[] args) throws Throwable
    {
        (new GradleStartServer()).launch(args);
    }

    @Override
    void setDefaultArguments(Map<String, String> argMap)
    {
        argMap.put("tweakClass", "@@SERVERTWEAKER@@");
    }

    @Override
    void preLaunch(Map<String, String> argMap, List<String> extras)
    {
        // umm... nothing?
    }

    @Override
    String getBounceClass()
    {
        return "@@BOUNCERSERVER@@";
    }
}
