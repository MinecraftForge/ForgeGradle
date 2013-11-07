package net.minecraftforge.gradle.user;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.BaseExtension;

import org.gradle.api.Project;

public class UserExtension extends BaseExtension
{
    // groups:  mcVersion  forgeVersion
    private static final Pattern VERSION_CHECK = Pattern.compile("(?:[\\w\\d.-]+):(?:[\\w\\d-]+):([\\d.]+)-([\\d.]+)-(?:[\\w\\d.]+)");
    
    private String apiVersion;
    private String notation;
    
    public UserExtension(Project project)
    {
        super(project);
    }
    
    public void setVersion(String str)
    {
        Matcher matcher = VERSION_CHECK.matcher(str);
        
        if (!matcher.matches())
            throw new IllegalArgumentException(str + " is not in the form 'group:artifact:artifact-classifier-MCVersion-apiVersion-branch'!");
        
        version = matcher.group(1);
        apiVersion = matcher.group(2);
        notation = matcher.group(0); // entire string
    }

    public String getApiVersion()
    {
        return apiVersion;
    }

    public String getNotation()
    {
        return notation;
    }

}
