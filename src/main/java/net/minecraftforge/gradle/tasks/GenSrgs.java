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
package net.minecraftforge.gradle.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;
import net.minecraftforge.gradle.util.delayed.DelayedFile;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.Type;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class GenSrgs extends CachedTask
{
    //@formatter:off
    @InputFile private DelayedFile inSrg;
    @InputFile private DelayedFile inExc;
    @InputFile private DelayedFile inStatics;
    @InputFile private DelayedFile methodsCsv;
    @InputFile private DelayedFile fieldsCsv;
    @Cached @OutputFile private DelayedFile notchToSrg;
    @Cached @OutputFile private DelayedFile notchToMcp;
    @Cached @OutputFile private DelayedFile mcpToNotch;
    @Cached @OutputFile private DelayedFile SrgToMcp;
    @Cached @OutputFile private DelayedFile mcpToSrg;
    @Cached @OutputFile private DelayedFile srgExc;
    @Cached @OutputFile private DelayedFile mcpExc;
    //@formatter:on

    @InputFiles
    private final LinkedList<File> extraExcs = new LinkedList<File>();
    @InputFiles
    private final LinkedList<File> extraSrgs = new LinkedList<File>();

    @TaskAction
    public void doTask() throws IOException
    {
        // csv data.  SRG -> MCP
        HashMap<String, String> methods = new HashMap<String, String>();
        HashMap<String, String> fields = new HashMap<String, String>();
        readCSVs(getMethodsCsv(), getFieldsCsv(), methods, fields);

        // Do SRG stuff
        SrgContainer inSrg = new SrgContainer().readSrg(getInSrg());
        Map<String, String> excRemap = readExtraSrgs(getExtraSrgs(), inSrg);
        writeOutSrgs(inSrg, methods, fields);

        // do EXC stuff
        writeOutExcs(inSrg, excRemap, methods);

    }

    private static void readCSVs(File methodCsv, File fieldCsv, Map<String, String> methodMap, Map<String, String> fieldMap) throws IOException
    {

        // read methods
        CSVReader csvReader = Constants.getReader(methodCsv);
        for (String[] s : csvReader.readAll())
        {
            methodMap.put(s[0], s[1]);
        }

        // read fields
        csvReader = Constants.getReader(fieldCsv);
        for (String[] s : csvReader.readAll())
        {
            fieldMap.put(s[0], s[1]);
        }
    }

    private Map<String, String> readExtraSrgs(FileCollection extras, SrgContainer inSrg)
    {
        return Maps.newHashMap(); //Nop this out.
        /*
        SrgContainer extraSrg = new SrgContainer().readSrgs(extras);
        // Need to convert these to Notch-SRG names. and add them to the other one.
        // These Extra SRGs are in MCP->SRG names as they are denoting dev time values.
        // So we need to swap the values we get.

        HashMap<String, String> excRemap = new HashMap<String, String>(extraSrg.methodMap.size());

        // SRG -> notch map
        Map<String, String> classMap = inSrg.classMap.inverse();
        Map<MethodData, MethodData> methodMap = inSrg.methodMap.inverse();

        // rename methods
        for (Entry<MethodData, MethodData> e : extraSrg.methodMap.inverse().entrySet())
        {
            String notchSig = remapSig(e.getValue().sig, classMap);
            String notchName = remapMethodName(e.getKey().name, notchSig, classMap, methodMap);
            //getProject().getLogger().lifecycle(e.getKey().name + " " + e.getKey().sig + " " + e.getValue().name + " " + e.getValue().sig);
            //getProject().getLogger().lifecycle(notchName       + " " + notchSig       + " " + e.getValue().name + " " + e.getValue().sig);
            inSrg.methodMap.put(new MethodData(notchName, notchSig), e.getValue());
            excRemap.put(e.getKey().name, e.getValue().name);
        }

        return excRemap;
        */
    }

    private void writeOutSrgs(SrgContainer inSrg, Map<String, String> methods, Map<String, String> fields) throws IOException
    {
        // ensure folders exist
        Files.createParentDirs(getNotchToSrg());
        Files.createParentDirs(getNotchToMcp());
        Files.createParentDirs(getSrgToMcp());
        Files.createParentDirs(getMcpToSrg());
        Files.createParentDirs(getMcpToNotch());

        // create streams
        try (
        BufferedWriter notchToSrg = Files.newWriter(getNotchToSrg(), Charsets.UTF_8);
        BufferedWriter notchToMcp = Files.newWriter(getNotchToMcp(), Charsets.UTF_8);
        BufferedWriter srgToMcp = Files.newWriter(getSrgToMcp(), Charsets.UTF_8);
        BufferedWriter mcpToSrg = Files.newWriter(getMcpToSrg(), Charsets.UTF_8);
        BufferedWriter mcpToNotch = Files.newWriter(getMcpToNotch(), Charsets.UTF_8))
        {

            String line, temp, mcpName;
            // packages
            for (Entry<String, String> e : inSrg.packageMap.entrySet())
            {
                line = "PK: " + e.getKey() + " " + e.getValue();

                // nobody cares about the packages.
                notchToSrg.write(line);
                notchToSrg.newLine();

                notchToMcp.write(line);
                notchToMcp.newLine();

                // No package changes from MCP to SRG names
                //srgToMcp.write(line);
                //srgToMcp.newLine();

                // No package changes from MCP to SRG names
                //mcpToSrg.write(line);
                //mcpToSrg.newLine();

                // reverse!
                mcpToNotch.write("PK: " + e.getValue() + " " + e.getKey());
                mcpToNotch.newLine();
            }

            // classes
            for (Entry<String, String> e : inSrg.classMap.entrySet())
            {
                line = "CL: " + e.getKey() + " " + e.getValue();

                // same...
                notchToSrg.write(line);
                notchToSrg.newLine();

                // SRG and MCP have the same class names
                notchToMcp.write(line);
                notchToMcp.newLine();

                line = "CL: " + e.getValue() + " " + e.getValue();

                // deobf: same classes on both sides.
                srgToMcp.write("CL: " + e.getValue() + " " + e.getValue());
                srgToMcp.newLine();

                // reobf: same classes on both sides.
                mcpToSrg.write("CL: " + e.getValue() + " " + e.getValue());
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write("CL: " + e.getValue() + " " + e.getKey());
                mcpToNotch.newLine();
            }

            // fields
            for (Entry<String, String> e : inSrg.fieldMap.entrySet())
            {
                line = "FD: " + e.getKey() + " " + e.getValue();

                // same...
                notchToSrg.write("FD: " + e.getKey() + " " + e.getValue());
                notchToSrg.newLine();

                temp = e.getValue().substring(e.getValue().lastIndexOf('/') + 1);
                mcpName = e.getValue();
                if (fields.containsKey(temp))
                    mcpName = mcpName.replace(temp, fields.get(temp));

                // SRG and MCP have the same class names
                notchToMcp.write("FD: " + e.getKey() + " " + mcpName);
                notchToMcp.newLine();

                // srg name -> mcp name
                srgToMcp.write("FD: " + e.getValue() + " " + mcpName);
                srgToMcp.newLine();

                // mcp name -> srg name
                mcpToSrg.write("FD: " + mcpName + " " + e.getValue());
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write("FD: " + mcpName + " " + e.getKey());
                mcpToNotch.newLine();
            }

            // methods
            for (Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet())
            {
                line = "MD: " + e.getKey() + " " + e.getValue();

                // same...
                notchToSrg.write("MD: " + e.getKey() + " " + e.getValue());
                notchToSrg.newLine();

                temp = e.getValue().name.substring(e.getValue().name.lastIndexOf('/') + 1);
                mcpName = e.getValue().toString();
                if (methods.containsKey(temp))
                    mcpName = mcpName.replace(temp, methods.get(temp));

                // SRG and MCP have the same class names
                notchToMcp.write("MD: " + e.getKey() + " " + mcpName);
                notchToMcp.newLine();

                // srg name -> mcp name
                srgToMcp.write("MD: " + e.getValue() + " " + mcpName);
                srgToMcp.newLine();

                // mcp name -> srg name
                mcpToSrg.write("MD: " + mcpName + " " + e.getValue());
                mcpToSrg.newLine();

                // output is notch
                mcpToNotch.write("MD: " + mcpName + " " + e.getKey());
                mcpToNotch.newLine();
            }
        }
    }

    private void writeOutExcs(SrgContainer inSrg, Map<String, String> excRemap, Map<String, String> methods) throws IOException
    {
        // ensure folders exist
        Files.createParentDirs(getSrgExc());
        Files.createParentDirs(getMcpExc());

        // create streams
        BufferedWriter srgOut = Files.newWriter(getSrgExc(), Charsets.UTF_8);
        BufferedWriter mcpOut = Files.newWriter(getMcpExc(), Charsets.UTF_8);

        // read and write existing lines
        List<String> excLines = Files.readLines(getInExc(), Charsets.UTF_8);
        Map<String, String> tmp = Maps.newHashMap();
        for (String line : excLines)
        {
            if (line.startsWith("#"))
                tmp.put(line, null);
            else
            {
                String[] pts = line.split("=");
                tmp.put(pts[0], pts[1]);
            }
        }

        // Generate default exc lines from srg
        Joiner comma = Joiner.on(',');
        Set<String> statics = Sets.newHashSet();
        statics.addAll(Files.readLines(getInStatics(), Charsets.UTF_8));
        for (MethodData mtd : inSrg.methodMap.values())
        {
            String cls = mtd.name.substring(0, mtd.name.lastIndexOf('/'));
            String name = mtd.name.substring(cls.length() + 1);
            //getLogger().lifecycle(cls + " " + name);
            if (!name.startsWith("func_"))
                continue;

            String prefix = "p_" + name.split("_")[1];
            List<String> args = Lists.newArrayList();

            int idx = statics.contains(name) ? 0 : 1; // Static methods don't have 'this'
            for (Type arg : Type.getArgumentTypes(mtd.sig))
            {
                args.add(prefix + "_" + idx++ + "_");
                if (arg == Type.DOUBLE_TYPE || arg == Type.LONG_TYPE)
                    idx++;
            }
            if (args.size() > 0)
            {
                String key = cls + "." + name + mtd.sig;
                String info = tmp.get(key);
                if (info == null)
                    info = "";
                else if (info.indexOf('|') != -1)
                    info = info.substring(0, info.indexOf('|'));
                tmp.put(key, info + "|" + comma.join(args));
            }
        }

        excLines.clear();
        List<String> keys = Lists.newArrayList(tmp.keySet());
        Collections.sort(keys);
        for (String key : keys)
        {
            String value = tmp.get(key);
            if (value == null)
                excLines.add(key);
            else
                excLines.add(key + "=" + value);
        }

        String[] split;
        for (String line : excLines)
        {
            // its already in SRG names.
            srgOut.write(line);
            srgOut.newLine();

            // remap MCP.

            // split line up
            split = line.split("=");
            int sigIndex = split[0].indexOf('(');
            int dotIndex = split[0].indexOf('.');

            // not a method? wut?
            if (line.startsWith("#") || sigIndex == -1 || dotIndex == -1)
            {
                mcpOut.write(line);
                mcpOut.newLine();
                continue;
            }

            // get new name
            String name = split[0].substring(dotIndex+1, sigIndex);
            if (methods.containsKey(name))
                name = methods.get(name);

            // write remapped line
            mcpOut.write(split[0].substring(0, dotIndex) + "." + name + split[0].substring(sigIndex) + "=" + split[1]);
            mcpOut.newLine();

        }

        for (File f : getExtraExcs())
        {
            List<String> lines = Files.readLines(f, Charsets.UTF_8);

            for (String line : lines)
            {
                // these are in MCP names
                mcpOut.write(line);
                mcpOut.newLine();

                // remap SRG

                // split line up
                split = line.split("=");
                int sigIndex = split[0].indexOf('(');
                int dotIndex = split[0].indexOf('.');

                // not a method? wut?
                if (sigIndex == -1 || dotIndex == -1)
                {
                    srgOut.write(line);
                    srgOut.newLine();
                    continue;
                }

                // get new name
                String name = split[0].substring(dotIndex+1, sigIndex);
                if (excRemap.containsKey(name))
                    name = excRemap.get(name);

                // write remapped line
                srgOut.write(split[0].substring(0, dotIndex) + name + split[0].substring(sigIndex) + "=" + split[1]);
                srgOut.newLine();
            }
        }

        srgOut.flush();
        srgOut.close();

        mcpOut.flush();
        mcpOut.close();
    }

    public File getInSrg()
    {
        return inSrg.call();
    }

    public void setInSrg(DelayedFile inSrg)
    {
        this.inSrg = inSrg;
    }

    public File getInExc()
    {
        return inExc.call();
    }

    public void setInExc(DelayedFile inSrg)
    {
        this.inExc = inSrg;
    }

    public File getInStatics()
    {
        return inStatics.call();
    }

    public void setInStatics(DelayedFile inStatics)
    {
        this.inStatics = inStatics;
    }

    public File getMethodsCsv()
    {
        return methodsCsv.call();
    }

    public void setMethodsCsv(DelayedFile methodsCsv)
    {
        this.methodsCsv = methodsCsv;
    }

    public File getFieldsCsv()
    {
        return fieldsCsv.call();
    }

    public void setFieldsCsv(DelayedFile fieldsCsv)
    {
        this.fieldsCsv = fieldsCsv;
    }

    public File getNotchToSrg()
    {
        return notchToSrg.call();
    }

    public void setNotchToSrg(DelayedFile deobfSrg)
    {
        this.notchToSrg = deobfSrg;
    }

    public File getNotchToMcp()
    {
        return notchToMcp.call();
    }

    public void setNotchToMcp(DelayedFile deobfSrg)
    {
        this.notchToMcp = deobfSrg;
    }

    public File getSrgToMcp()
    {
        return SrgToMcp.call();
    }

    public void setSrgToMcp(DelayedFile deobfSrg)
    {
        this.SrgToMcp = deobfSrg;
    }

    public File getMcpToSrg()
    {
        return mcpToSrg.call();
    }

    public void setMcpToSrg(DelayedFile reobfSrg)
    {
        this.mcpToSrg = reobfSrg;
    }

    public File getMcpToNotch()
    {
        return mcpToNotch.call();
    }

    public void setMcpToNotch(DelayedFile reobfSrg)
    {
        this.mcpToNotch = reobfSrg;
    }

    public File getSrgExc()
    {
        return srgExc.call();
    }

    public void setSrgExc(DelayedFile inSrg)
    {
        this.srgExc = inSrg;
    }

    public File getMcpExc()
    {
        return mcpExc.call();
    }

    public void setMcpExc(DelayedFile inSrg)
    {
        this.mcpExc = inSrg;
    }

    public FileCollection getExtraExcs()
    {
        return getProject().files(extraExcs);
    }

    public void addExtraExc(File file)
    {
        extraExcs.add(file);
    }

    public FileCollection getExtraSrgs()
    {
        return getProject().files(extraSrgs);
    }

    public void addExtraSrg(File file)
    {
        extraSrgs.add(file);
    }
}
