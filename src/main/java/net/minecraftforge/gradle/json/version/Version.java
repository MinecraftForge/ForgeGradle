package net.minecraftforge.gradle.json.version;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Version
{
    public String id;
    public Date time;
    public Date releaseTime;
    public String type;
    public String minecraftArguments;
    public String inheritsFrom;
    private List<Library> libraries;
    public String mainClass;
    public int minimumLauncherVersion;
    public String incompatibilityReason;
    private String assets;
    public List<OSRule> rules;
    
    private List<Library> _libraries;

    public List<Library> getLibraries()
    {
        if (_libraries == null)
        {
            _libraries = new ArrayList<Library>();
            if (libraries == null) return _libraries;
            for (Library lib : libraries)
            {
                if (lib.applies())
                {
                    _libraries.add(lib);
                }
            }
        }
        return _libraries;
    }
    
    public String getAssets()
    {
        return assets == null ? "legacy" : assets;
    }
    
    /**
     * Populates this instance with information from another version json.
     * @param version Version json to extend from
     */
    public void extendFrom(Version version)
    {
        // strings. repalce if null.
        if (minecraftArguments == null)
            minecraftArguments = version.minecraftArguments;
        if (mainClass == null)
            mainClass = version.mainClass;
        if (incompatibilityReason == null)
            incompatibilityReason = version.incompatibilityReason;
        if (assets == null)
            assets = version.assets;

        // lists.  repalce if null, add if not.
        if (libraries == null)
            libraries = version.libraries;
        else if (version.libraries != null)
            libraries.addAll(0, version.libraries);

        if (rules == null)
            rules = version.rules;
        else if (version.rules != null)
            rules.addAll(0, version.rules);
    }
}
