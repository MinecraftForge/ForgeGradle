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
package net.minecraftforge.gradle.tasks;

import groovy.util.MapEntry;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.bind.DatatypeConverter;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class JenkinsChangelog extends DefaultTask
{
    //@formatter:off
    @Input Object serverRoot;
    @Input Object jobName;
    @Input Object authName;
    @Input Object authPassword;
    @Input Object targetBuild;
    //@formatter:on

    @OutputFile
    Object         output;

    private int    targetBuildResolved = -1;
    private String auth                = null;

    @SuppressWarnings("unchecked")
    @TaskAction
    public void doTask() throws IOException
    {
        if (getAuthName() != null && getAuthPassword() != null)
        {
            String raw = getAuthName() + ":" + getAuthPassword();
            auth = "Basic " + DatatypeConverter.printBase64Binary(raw.getBytes());
        }

        List<Map<String, Object>> builds = getBuildInfo();
        getLatestBuild(builds);

        StringBuilder out = new StringBuilder();
        out.append("Changelog:\r\n");
        for (Map<String, Object> build : builds)
        {
            int number = ((Double) build.get("number")).intValue();
            List<MapEntry> items = (List<MapEntry>) build.get("items");

            if (getTargetBuild() > 0 && number > getTargetBuild())
                continue;

            out.append("Build ");
            out.append(build.get("version") == null ? number : build.get("version"));
            out.append(':').append('\n');

            for (MapEntry entry : items)
            {
                String[] lines = ((String) entry.getValue()).trim().split("\n");
                if (lines.length == 1)
                {
                    out.append('\t').append(entry.getKey()).append(": ").append(lines[0]).append('\n');
                }
                else
                {
                    out.append('\t').append(entry.getKey()).append(':').append('\n');
                    for (String line : lines)
                    {
                        out.append('\t').append('\t').append(line).append('\n');
                    }
                }
            }
            out.append('\n');
        }

        File outFile = getOutput();
        outFile.getParentFile().mkdirs();
        Files.write(out.toString().getBytes(), outFile);
    }

    private String read(String url) throws MalformedURLException, IOException
    {
        return read(new URL(getServerRoot() + "job/" + getJobName() + url));
    }

    private String read(URL url) throws IOException
    {
        URLConnection con = null;
        con = url.openConnection();
        con.setRequestProperty("User-Agent", Constants.USER_AGENT);
        if (auth != null)
        {
            getProject().getLogger().debug(auth);
            con.addRequestProperty("Authorization", auth);
        }
        return new String(ByteStreams.toByteArray(con.getInputStream()));
    }

    private String cleanJson(String data, String part)
    {
        data = data.replace("," + part + ",", ",");
        data = data.replace(part + ",", "");
        data = data.replace("," + part, "");
        data = data.replace("{" + part + "}", "{}");
        data = data.replace("[" + part + "]", "[]");
        return data;
    }

    private static final Gson GSON_FORMATTER = new GsonBuilder().setPrettyPrinting().create();

    @SuppressWarnings("unused")
    private void prettyPrint(Object json)
    {
        getLogger().lifecycle(GSON_FORMATTER.toJson(json));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getBuildInfo()
    {
        String data = null;
        try
        {
            boolean versioned = false;
            data = read("/api/python?tree=allBuilds[result,number,actions[text],changeSet[items[author[fullName],comment]]]");//&pretty=true");
            data = data.replace("\"result\":None", "\"result\":\"\"");
            data = cleanJson(data, "None"); //This kills Gson for some reason
            data = cleanJson(data, "{}"); //Empty entries, just for sanities sake

            List<Map<String, Object>> json = (List<Map<String, Object>>) new Gson().fromJson(data, Map.class).get("allBuilds");
            Collections.sort(json, new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2)
                {
                    return (int) ((Double) o1.get("number") - (Double) o2.get("number"));
                }

            });

            List<Entry<String, String>> items = new ArrayList<Entry<String, String>>();
            Iterator<Map<String, Object>> bitr = json.iterator();
            while (bitr.hasNext())
            {
                Map<String, Object> build = bitr.next();

                List<Map<String, String>> actions = (List<Map<String, String>>) build.get("actions");
                Iterator<Map<String, String>> itr = actions.iterator();
                while (itr.hasNext())
                {
                    Map<String, String> map = itr.next();
                    if (!map.containsKey("text") ||
                        map.get("text").contains("http") ||
                        map.get("text").contains("href="))
                    {
                        itr.remove();
                    }
                }

                if (actions.size() == 0)
                {
                    build.put("version", versioned ? ((Double) build.get("number")).intValue() : getProject().getVersion());
                    versioned = true;
                }
                else
                {
                    build.put("version", actions.get(0).get("text"));
                }

                for (Map<String, Object> e : (List<Map<String, Object>>) ((Map<String, Object>) build.get("changeSet")).get("items"))
                {
                    items.add(new MapEntry(((Map<String, String>) e.get("author")).get("fullName"), e.get("comment")));
                }
                build.put("items", items);

                if (build.get("result").equals("SUCCESS"))
                {
                    if (items.size() == 0)
                        bitr.remove();
                    items = new ArrayList<Entry<String, String>>();
                }
                else
                {
                    bitr.remove();
                }

                build.remove("result");
                build.remove("changeSet");
                build.remove("actions");
            }
            //prettyPrint(json);
            Collections.sort(json, new Comparator<Map<String, Object>>()
            {
                @Override
                public int compare(Map<String, Object> o1, Map<String, Object> o2)
                {
                    return (int) ((Double) o2.get("number") - (Double) o1.get("number"));
                }

            });
            return json;
        }
        catch (Exception e)
        {
            e.printStackTrace();
            getLogger().lifecycle(data);
        }
        return new ArrayList<Map<String, Object>>();
    }

    @SuppressWarnings("unchecked")
    private void getLatestBuild(List<Map<String, Object>> builds)
    {
        String data = null;
        try
        {
            Object ver = "";
            if (builds.size() > 0)
            {
                ver = builds.get(0).get("number");
            }
            boolean versioned = false;
            data = read("/lastBuild/api/python?tree=number,changeSet[items[author[fullName],comment]]");//&pretty=true");
            data = cleanJson(data, "None"); //This kills Gson for some reason
            data = cleanJson(data, "{}"); //Empty entries, just for sanities sake

            Map<String, Object> build = (Map<String, Object>) new Gson().fromJson(data, Map.class);
            if (build.get("number").equals(ver))
            {
                return;
            }
            build.put("version", versioned ? "Build " + ((Double) build.get("number")).intValue() : getProject().getVersion());

            List<Entry<String, String>> items = new ArrayList<Entry<String, String>>();
            for (Map<String, Object> e : (List<Map<String, Object>>) ((Map<String, Object>) build.get("changeSet")).get("items"))
            {
                items.add(new MapEntry(((Map<String, String>) e.get("author")).get("fullName"), e.get("comment")));
            }
            build.put("items", items);

            build.remove("result");
            build.remove("changeSet");
            build.remove("actions");

            builds.add(0, build);
            //prettyPrint(build);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            getLogger().lifecycle(data);
        }
    }

    public String getServerRoot()
    {
        return Constants.resolveString(serverRoot);
    }

    public void setServerRoot(Object serverRoot)
    {
        this.serverRoot = serverRoot;
    }

    public String getJobName()
    {
        return Constants.resolveString(jobName);
    }

    public void setJobName(Object jobName)
    {
        this.jobName = jobName;
    }

    public String getAuthName()
    {
        return Constants.resolveString(authName);
    }

    public void setAuthName(Object authName)
    {
        this.authName = authName;
    }

    public String getAuthPassword()
    {
        return Constants.resolveString(authPassword);
    }

    public void setAuthPassword(Object authPassword)
    {
        this.authPassword = authPassword;
    }

    public int getTargetBuild()
    {
        if (targetBuildResolved != -1)
        {
            return targetBuildResolved;
        }

        targetBuildResolved = Integer.MAX_VALUE;
        if (targetBuild != null)
        {
            try
            {
                targetBuildResolved = Integer.parseInt(Constants.resolveString(targetBuild));
                if (targetBuildResolved <= 0)
                {
                    targetBuildResolved = Integer.MAX_VALUE;
                }
            }
            catch (NumberFormatException e)
            {
                getProject().getLogger().debug("Error reading target build: " + e.getMessage());
            }
        }

        return targetBuildResolved;
    }

    public void setTargetBuild(Object targetBuild)
    {
        this.targetBuild = targetBuild;
    }

    public File getOutput()
    {
        return getProject().file(output);
    }

    public void setOutput(Object output)
    {
        this.output = output;
    }
}
