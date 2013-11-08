package net.minecraftforge.gradle.user;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.Project;

public class UserExtension extends BaseExtension
{
    // groups:  mcVersion  forgeVersion
    //private static final Pattern VERSION_CHECK = Pattern.compile("(?:[\\w\\d.-]+):(?:[\\w\\d-]+):([\\d.]+)-([\\d.]+)-(?:[\\w\\d.]+)");
    private static final Pattern VERSION_CHECK = Pattern.compile("([\\d.]+)-([\\d.]+)-(?:[\\w\\d.]+)");
    
    private String apiVersion;
    
    public UserExtension(Project project)
    {
        super(project);
    }
    
    public void setVersion(String str)
    {
        Matcher matcher = VERSION_CHECK.matcher(str);
        
        if (!matcher.matches())
            throw new IllegalArgumentException(str + " is not in the form 'MCVersion-apiVersion-branch'!");
        
        version = matcher.group(1);
        apiVersion = matcher.group(0);
    }

    public String getApiVersion()
    {
        return apiVersion;
    }
}
