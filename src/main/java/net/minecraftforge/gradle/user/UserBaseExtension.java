/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
    private Object                  runSourceSet     = "main";

    public UserBaseExtension(UserBasePlugin<? extends UserBaseExtension> plugin)
    {
        super(plugin);
    }

    /**
     * Add a source replacement mapping
     *
     * @param token       The token to replace
     * @param replacement The value to replace with
     */
    public void replace(Object token, Object replacement)
    {
        replacements.put(token.toString(), replacement);
    }

    /**
     * Add a map of source replacement mappings
     *
     * @param map A map of tokens -&gt; replacements
     */
    public void replace(Map<Object, Object> map)
    {
        for (Entry<Object, Object> e : map.entrySet())
        {
            replace(e.getKey(), e.getValue());
        }
    }

    /**
     * Get all of the source replacement tokens and values
     *
     * @return A map of tokens -&gt; replacements
     */
    public Map<String, Object> getReplacements()
    {
        return replacements;
    }

    /**
     * Get a list of file patterns that will be used to determine included source replacement files.
     *
     * @return A list of classes
     */
    public List<String> getIncludes()
    {
        return includes;
    }

    /**
     * Add a file pattern to be used in source replacement. {@code file.getPath().endsWith(pattern)} is used to determine included files.<br>
     * This is an addative operation, so multiple calls are allowed
     *
     * @param pattern The pattern
     */
    public void replaceIn(String pattern)
    {
        includes.add(pattern);
    }

    //@formatter:off

    /**
     * Add an access transformer
     *
     * @param obj The access transformer file
     */
    public void accessT(Object obj) { at(obj); }

    /**
     * Add access transformers
     *
     * @param obj The access transformer files
     */
    public void accessTs(Object... obj) { ats(obj); }

    /**
     * Add an access transformer
     *
     * @param obj The access transformer file
     */
    public void accessTransformer(Object obj) { at(obj); }

    /**
     * Add access transformer
     *
     * @param obj The access transformer files
     */
    public void accessTransformers(Object... obj) { ats(obj); }
    //@formatter:on

    /**
     * Add an access transformer
     *
     * @param obj The access transformer file
     */
    public void at(Object obj)
    {
        ats.add(obj);
    }

    /**
     * Add access transformers
     *
     * @param obj The access transformer files
     */
    public void ats(Object... obj)
    {
        Collections.addAll(ats, obj);
    }

    /**
     * Get all of the access transformers
     *
     * @return A list of access transformers
     */
    public List<Object> getAccessTransformers()
    {
        return ats;
    }

    //@formatter:off
    /**
     * Add a location where access transformers can be found
     *
     * @param obj A location
     */
    public void accessTransformerSource(Object obj) { atSource(obj); }

    /**
     * Add locations where access transformers can be found
     *
     * @param obj Locations
     */
    public void accessTransformerSources(Object... obj) { atSources(obj); }
    //@formatter:on

    /**
     * Add a location where access transformers can be found
     *
     * @param obj A location
     */
    public void atSource(Object obj)
    {
        atSources.add(obj);
    }

    /**
     * Add locations where access transformers can be found
     *
     * @param obj Locations
     */
    public void atSources(Object... obj)
    {
        Collections.addAll(atSources, obj);
    }

    /**
     * Get a list of <em>unresolved</em> access transformer source locations
     *
     * @return A list of AT source locations
     */
    public List<Object> getAccessTransformerSources()
    {
        return atSources;
    }

    /**
     * Get a list of <em>resolved</em> access transformer source locations
     *
     * @return A list of AT source locations
     */
    public FileCollection getResolvedAccessTransformerSources()
    {
        return resolveFiles(atSources);
    }

    /**
     * @return Whether or not to grab Access Transformers from dependencies
     */
    public boolean isUseDepAts()
    {
        return useDepAts;
    }

    /**
     * Set if dependencies should be searched for access transformers
     *
     * @param useDepAts {@code true} if dependencies should be searched
     */
    public void setUseDepAts(boolean useDepAts)
    {
        this.useDepAts = useDepAts;
    }

    /**
     * Set the run location for Minecraft
     *
     * @param value The run location
     */
    public void setRunDir(String value)
    {
        this.runDir = value;
        replacer.putReplacement(UserConstants.REPLACE_RUN_DIR, runDir);
    }

    /**
     * Get the run location for Minecraft
     *
     * @return The run location
     */
    public String getRunDir()
    {
        return this.runDir;
    }

    /**
     * @return {@code true} if a srg-named sources jar will be created
     */
    public boolean getMakeObfSourceJar()
    {
        return makeObfSourceJar;
    }

    /**
     * Set if a srg-named sources jar should be created
     *
     * @param makeObfSourceJar if a srg-named sources jar should be created
     */
    public void setMakeObfSourceJar(boolean makeObfSourceJar)
    {
        this.makeObfSourceJar = makeObfSourceJar;
    }

    /**
     * Get the VM arguments for the client run config
     *
     * @return The client JVM args
     */
    public List<Object> getClientJvmArgs()
    {
        return clientJvmArgs;
    }

    public List<String> getResolvedClientJvmArgs()
    {
        return resolve(getClientJvmArgs());
    }

    /**
     * Set the VM arguments for the client run config
     *
     * @param clientJvmArgs The client JVM args
     */
    public void setClientJvmArgs(List<Object> clientJvmArgs)
    {
        this.clientJvmArgs = clientJvmArgs;
    }

    /**
     * Get the run arguments for the client run config
     *
     * @return The client run args
     */
    public List<Object> getClientRunArgs()
    {
        return clientRunArgs;
    }

    public List<String> getResolvedClientRunArgs()
    {
        return resolve(getClientRunArgs());
    }

    /**
     * Set the run arguments for the client run config
     *
     * @param clientRunArgs The client run args
     */
    public void setClientRunArgs(List<Object> clientRunArgs)
    {
        this.clientRunArgs = clientRunArgs;
    }

    /**
     * Get the VM arguments for the server run config
     *
     * @return The server JVM args
     */
    public List<Object> getServerJvmArgs()
    {
        return serverJvmArgs;
    }

    public List<String> getResolvedServerJvmArgs()
    {
        return resolve(getServerJvmArgs());
    }

    /**
     * Set the VM arguments for the server run config
     *
     * @param serverJvmArgs The server JVM args
     */
    public void setServerJvmArgs(List<Object> serverJvmArgs)
    {
        this.serverJvmArgs = serverJvmArgs;
    }

    /**
     * Get the run arguments for the server run config
     *
     * @return The server run args
     */
    public List<Object> getServerRunArgs()
    {
        return serverRunArgs;
    }

    public List<String> getResolvedServerRunArgs()
    {
        return resolve(getServerRunArgs());
    }

    public SourceSet getRunSourceSet() {
        return resolveSourceSet(this.runSourceSet);
    }

    public void setRunSourceSet(Object runSourceSet) {
        this.runSourceSet = runSourceSet;
    }

    /**
     * Set the run arguments for the server run config
     *
     * @param serverRunArgs The server run args
     */
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

    private SourceSet resolveSourceSet(Object obj) {
        while (obj instanceof Closure)
            obj = ((Closure<?>) obj).call();

        if (obj instanceof SourceSet)
            return (SourceSet) obj;
        else {
            String name = obj.toString();
            JavaPluginConvention javaConv = (JavaPluginConvention) project.getConvention().getPlugins().get("java");
            return javaConv.getSourceSets().getByName(name);
        }
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
