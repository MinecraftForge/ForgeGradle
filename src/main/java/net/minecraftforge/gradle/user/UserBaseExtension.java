package net.minecraftforge.gradle.user;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.delayed.TokenReplacer;

import com.google.common.collect.Lists;

public class UserBaseExtension extends BaseExtension
{
    private HashMap<String, Object> replacements     = new HashMap<String, Object>();
    private ArrayList<String>       includes         = new ArrayList<String>();
    private ArrayList<Object>       ats              = new ArrayList<Object>();
    private String                  runDir           = "run";
    private boolean                 makeObfSourceJar = true;
    private List<Object>            clientJvmArgs    = Lists.newArrayList();
    private List<Object>            clientRunArgs    = Lists.newArrayList();
    private List<Object>            serverJvmArgs    = Lists.newArrayList();
    private List<Object>            serverRunArgs    = Lists.newArrayList();

    public UserBaseExtension(UserBasePlugin<? extends UserBaseExtension> plugin)
    {
        super(plugin);
    }

    public void replace(Object token, Object replacement)
    {
        replacements.put(token.toString(), replacement);
    }

    public void replace(Map<Object, Object> map)
    {
        for (Entry<Object, Object> e : map.entrySet())
        {
            replace(e.getKey(), e.getValue());
        }
    }

    public Map<String, Object> getReplacements()
    {
        return replacements;
    }

    public List<String> getIncludes()
    {
        return includes;
    }

    public void replaceIn(String path)
    {
        includes.add(path);
    }

    //@formatter:off
    public void accessT(Object obj) { at(obj); }
    public void accessTs(Object... obj) { ats(obj); }
    public void accessTransformer(Object obj) { at(obj); }
    public void accessTransformers(Object... obj) { ats(obj); }
    //@formatter:on

    public void at(Object obj)
    {
        ats.add(obj);
    }

    public void ats(Object... obj)
    {
        for (Object object : obj)
            ats.add(object);
    }

    public List<Object> getAccessTransformers()
    {
        return ats;
    }

    public void setRunDir(String value)
    {
        this.runDir = value;
        TokenReplacer.putReplacement(UserConstants.REPLACE_RUN_DIR, runDir);
    }

    public String getRunDir()
    {
        return this.runDir;
    }

    public boolean getMakeObfSourceJar()
    {
        return makeObfSourceJar;
    }

    public void setMakeObfSourceJar(boolean makeObfSourceJar)
    {
        this.makeObfSourceJar = makeObfSourceJar;
    }

    public List<Object> getClientJvmArgs()
    {
        return clientJvmArgs;
    }

    public List<String> getResolvedClientJvmArgs()
    {
        return resolve(getClientJvmArgs());
    }

    public void setClientJvmArgs(List<Object> clientJvmArgs)
    {
        this.clientJvmArgs = clientJvmArgs;
    }

    public List<Object> getClientRunArgs()
    {
        return clientRunArgs;
    }

    public List<String> getResolvedClientRunArgs()
    {
        return resolve(getClientRunArgs());
    }

    public void setClientRunArgs(List<Object> clientRunArgs)
    {
        this.clientRunArgs = clientRunArgs;
    }

    public List<Object> getServerJvmArgs()
    {
        return serverJvmArgs;
    }

    public List<String> getResolvedServerJvmArgs()
    {
        return resolve(getServerJvmArgs());
    }

    public void setServerJvmArgs(List<Object> serverJvmArgs)
    {
        this.serverJvmArgs = serverJvmArgs;
    }

    public List<Object> getServerRunArgs()
    {
        return serverRunArgs;
    }

    public List<String> getResolvedServerRunArgs()
    {
        return resolve(getServerRunArgs());
    }

    public void setServerRunArgs(List<Object> serverRunArgs)
    {
        this.serverRunArgs = serverRunArgs;
    }

    private List<String> resolve(List<Object> list)
    {
        List<String> out = Lists.newArrayListWithCapacity(list.size());
        for (Object o : list)
        {
            out.add(Constants.resolveString(o));
        }
        return out;
    }
}
