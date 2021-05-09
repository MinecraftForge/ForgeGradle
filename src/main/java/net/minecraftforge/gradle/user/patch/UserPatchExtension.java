package net.minecraftforge.gradle.user.patch;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.gradle.delayed.DelayedObject;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.ProjectConfigurationException;

public class UserPatchExtension extends UserExtension
{    
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
    
    public void setVersion(String str) // magic goes here
    {
        checkAndSetVersion(str);
        
        // now check the mappings from the base plugin
        checkMappings();
    }
    
    private void checkAndSetVersion(String str)
    {
        str = str.trim();
        int idx = str.indexOf('-');
        if (idx == -1)
            throw new IllegalArgumentException("You must specify the full forge version, including MC version in your build.gradle. Example: 1.12.2-14.23.5.2811");
        this.version = str.substring(0, idx); //MC Version
        this.apiVersion = str;

        /*
         * Old FG used to use a horribly outdated MASSIVE json file for trying to be 'smart' when processing the version information.
         * It tried to allow for many 'shortcuts' when specifying the Forge version.
         * All of this are horribly and stupid, and should of never existed in the first place.
         * So I'm gutting them.
         *
         * But will document them here to the best of my understanding, so that if people need them we can re-implement them in less horribly hacky ways.
         *
         * JUST the build number:
         *   Prior to 1.13, Forge used a unique build number to identify all versions. So in theory you could pick an exact build with just the build number.
         *   Example:
         *     Input: 2815
         *     Output: 1.12.2-14.23.5.2815
         *   Solution:
         *     Download maven-metadata.xml, loop through all versions doing:
         *       key = ver.split('-')[0].rsplit('.', 1)[1]
         *       if (!map.containsKey(key))  //This is important because metadata is ordered oldest to newest, and new versions could duplicate the build number
         *         map.put(key, ver)
         *
         *
         * Promotion Name:
         *   We publish 'promoted' builds of Forge. Typically 'latest' and 'recommended'. Simple enough way to make a auto updating version.
         *   Example:
         *     Input: 1.8-recommended
         *     Output: 1.8-11.14.4.1563
         *   Solution:
         *     Again, Abrar downloaded a 2MB MASSIVE json file, when a slim json would do.
         *     https://maven.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json
         *
         *
         * API-Wildcards:
         *   Abrar tried to emulate dynamic versions which would be introduced into gradle far afterwords.
         *   Example:
         *     Input: 14.23.5.1
         *     Output: 1.12.2-14.23.5.2811
         *   Solution:
         *     Again, can be solved using maven-metadata.xml, use Apache's ArtifactVersion library to parse out a easy comparable version for everything in the metadata, and the version the user input.
         *     Set MinVersion = ArtifactVersion(input)
         *     prefix = input.substring(0, input.lastIndexOf('.'))
         *     MaxVersion = ArtifactVersion(prefix.rsplit('.', 1)[0] + '.' + (int(prefix.rsplit('.', 1)[1]) + 1))
         *
         *     Then find the max version that fits: MinVersion <= Version < MaxVersion
         *
         * Full Version:
         *   Example:
         *     Input: 1.12.2-14.23.5.2811
         *     Output: 1.12.2-14.23.5.2811
         *
         *     This was just used to verify the version existed. This can be done via maven-metadata.xml
         */
    }
}
