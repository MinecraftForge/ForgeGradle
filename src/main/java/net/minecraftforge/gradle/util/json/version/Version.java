package net.minecraftforge.gradle.util.json.version;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

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
    public AssetIndexRef assetIndex;
    private Map<String, Download> downloads;
    public List<OSRule> rules;

    private List<Library> _libraries;

    public List<Library> getLibraries()
    {
        if (_libraries == null)
        {
            _libraries = new ArrayList<Library>();
            Library jsr305 = new Library();
            jsr305.name = "com.google.code.findbugs:jsr305:3.0.1";
            _libraries.add(jsr305);
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

    public String getClientUrl()
    {
        return downloads.get("client").url;
    }

    public String getServerUrl()
    {
        return downloads.get("server").url;
    }

    /**
     * Populates this instance with information from another version json.
     * @param version Version json to extend from
     */
    public void extendFrom(Version version)
    {
        // strings. replace if null.
        if (minecraftArguments == null)
            minecraftArguments = version.minecraftArguments;
        if (mainClass == null)
            mainClass = version.mainClass;
        if (incompatibilityReason == null)
            incompatibilityReason = version.incompatibilityReason;
        if (assetIndex == null)
            assetIndex = version.assetIndex;
        if (downloads == null)
            downloads = version.downloads;

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
