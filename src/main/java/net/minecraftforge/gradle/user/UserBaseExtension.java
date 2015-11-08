/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.user;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.google.common.collect.Lists;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.common.Constants;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.tasks.SourceSet;

public class UserBaseExtension extends BaseExtension
{
    private HashMap<String, Object> replacements     = new HashMap<String, Object>();
    private ArrayList<String>       includes         = new ArrayList<String>();
    private ArrayList<Object>       ats              = new ArrayList<Object>();
    private ArrayList<Object>       atSources        = new ArrayList<Object>();
    private boolean                 useDepAts        = false;
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
        Collections.addAll(ats, obj);
    }

    public List<Object> getAccessTransformers()
    {
        return ats;
    }

    //@formatter:off
    public void accessTransformerSource(Object obj) { atSource(obj); }
    public void accessTransformerSources(Object... obj) { atSources(obj); }
    //@formatter:on

    public void atSource(Object obj)
    {
        atSources.add(obj);
    }

    public void atSources(Object... obj)
    {
        Collections.addAll(atSources, obj);
    }

    public List<Object> getAccessTransformerSources()
    {
        return atSources;
    }

    public FileCollection getResolvedAccessTransformerSources()
    {
        return resolveFiles(atSources);
    }

    /**
     * Whether or not to grab Access Transformers from dependencies
     */
    public boolean isUseDepAts()
    {
        return useDepAts;
    }

    /**
     *
     * @param useDepAts If TRUE, then
     */
    public void setUseDepAts(boolean useDepAts)
    {
        this.useDepAts = useDepAts;
    }

    public void setRunDir(String value)
    {
        this.runDir = value;
        replacer.putReplacement(UserConstants.REPLACE_RUN_DIR, runDir);
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

    private Object resolveFile(Object obj)
    {
        while (obj instanceof Closure)
            obj = ((Closure<?>) obj).call();

        SourceSet set = null;
        if (obj instanceof SourceSet)
            set = (SourceSet) obj;
        else if (obj instanceof String)
        {
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            set = javaConv.getSourceSets().findByName((String) obj);
        }

        return (set != null) ? set.getResources() : obj;
    }

    protected FileCollection resolveFiles(List<Object> objects)
    {
        Object[] files = new Object[objects.size()];
        int i = 0;
        for (Object obj : objects)
            files[i++] = resolveFile(obj);
        return project.files(files).getAsFileTree();
    }
}
