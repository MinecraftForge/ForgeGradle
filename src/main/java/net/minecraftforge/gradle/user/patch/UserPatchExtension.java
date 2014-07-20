package net.minecraftforge.gradle.user.patch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.delayed.DelayedObject;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.ProjectConfigurationException;

public class UserPatchExtension extends UserExtension
{
    // groups:  mcVersion  forgeVersion
    //private static final Pattern VERSION_CHECK = Pattern.compile("(?:[\\w\\d.-]+):(?:[\\w\\d-]+):([\\d.]+)-([\\d.]+)-(?:[\\w\\d.]+)");
    private static final Pattern VERSION_CHECK = Pattern.compile("([\\d._pre]+)-([\\w\\d.]+)(?:-[\\w\\d.]+)?");
    
    private int maxFuzz = 0;

    private String apiVersion;
    private ArrayList<Object> ats = new ArrayList<Object>();

    public UserPatchExtension(UserPatchBasePlugin plugin)
    {
        super(plugin);
    }
    
    public void accessT(Object obj) { at(obj); }
    public void accessTs(Object... obj) { ats(obj); }
    public void accessTransformer(Object obj) { at(obj); }
    public void accessTransformers(Object... obj) { ats(obj); }

    public void at(Object obj)
    {
        ats.add(obj);
    }

    public void ats(Object... obj)
    {
        for (Object object : obj)
            ats.add(new DelayedObject(object, project));
    }

    public List<Object> getAccessTransformers()
    {
        return ats;
    }
    
    public void setVersion(String str)
    {
        Matcher matcher = VERSION_CHECK.matcher(str);
        
        if (!matcher.matches())
            throw new IllegalArgumentException(str + " is not in the form 'MCVersion-apiVersion-branch'!");
        
        version = matcher.group(1).replace('_', '-');
        apiVersion = matcher.group(0);
    }

    public String getApiVersion()
    {
        if (apiVersion == null)
            throw new ProjectConfigurationException("You must set the Minecraft Version!", new NullPointerException());
        
        return apiVersion;
    }
    
    public int getMaxFuzz()
    {
        return maxFuzz;
    }

    public void setMaxFuzz(int fuzz)
    {
        this.maxFuzz = fuzz;
    }
}
