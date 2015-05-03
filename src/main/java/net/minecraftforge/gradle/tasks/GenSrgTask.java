package net.minecraftforge.gradle.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;
import net.minecraftforge.srg2source.rangeapplier.MethodData;
import net.minecraftforge.srg2source.rangeapplier.SrgContainer;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Charsets;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class GenSrgTask extends CachedTask
{
    @InputFile
    private DelayedFile inSrg;
    @InputFile
    private DelayedFile inExc;

    @InputFiles
    private final LinkedList<File> extraExcs = new LinkedList<File>();
    @InputFiles
    private final LinkedList<File> extraSrgs = new LinkedList<File>();

    @InputFile
    private DelayedFile methodsCsv;
    @InputFile
    private DelayedFile fieldsCsv;

    @Cached
    @OutputFile
    private DelayedFile notchToSrg;

    @Cached
    @OutputFile
    private DelayedFile notchToMcp;

    @Cached
    @OutputFile
    private DelayedFile mcpToNotch;
    
    @Cached
    @OutputFile
    private DelayedFile SrgToMcp;

    @Cached
    @OutputFile
    private DelayedFile mcpToSrg;

    @Cached
    @OutputFile
    private DelayedFile srgExc;

    @Cached
    @OutputFile
    private DelayedFile mcpExc;

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
        writeOutExcs(excRemap, methods);

    }

    private static void readCSVs(File methodCsv, File fieldCsv, Map<String, String> methodMap, Map<String, String> fieldMap) throws IOException
    {

        // read methods
        CSVReader csvReader = RemapSourcesTask.getReader(methodCsv);
        for (String[] s : csvReader.readAll())
        {
            methodMap.put(s[0], s[1]);
        }

        // read fields
        csvReader = RemapSourcesTask.getReader(fieldCsv);
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
        BufferedWriter notchToSrg = Files.newWriter(getNotchToSrg(), Charsets.UTF_8);
        BufferedWriter notchToMcp = Files.newWriter(getNotchToMcp(), Charsets.UTF_8);
        BufferedWriter srgToMcp = Files.newWriter(getSrgToMcp(), Charsets.UTF_8);
        BufferedWriter mcpToSrg = Files.newWriter(getMcpToSrg(), Charsets.UTF_8);
        BufferedWriter mcpToNotch = Files.newWriter(getMcpToNotch(), Charsets.UTF_8);

        String line, temp, mcpName;
        // packages
        for (Entry<String, String> e : inSrg.packageMap.entrySet())
        {
            line = "PK: "+e.getKey()+" "+e.getValue();

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
            mcpToNotch.write("PK: "+e.getValue()+" "+e.getKey());
            mcpToNotch.newLine();
        }

        // classes
        for (Entry<String, String> e : inSrg.classMap.entrySet())
        {
            line = "CL: "+e.getKey()+" "+e.getValue();

            // same...
            notchToSrg.write(line);
            notchToSrg.newLine();

            // SRG and MCP have the same class names
            notchToMcp.write(line);
            notchToMcp.newLine();

            line = "CL: "+e.getValue()+" "+e.getValue();

            // deobf: same classes on both sides.
            srgToMcp.write("CL: "+e.getValue()+" "+e.getValue());
            srgToMcp.newLine();

            // reobf: same classes on both sides.
            mcpToSrg.write("CL: "+e.getValue()+" "+e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("CL: "+e.getValue()+" "+e.getKey());
            mcpToNotch.newLine();
        }

        // fields
        for (Entry<String, String> e : inSrg.fieldMap.entrySet())
        {
            line = "FD: "+e.getKey()+" "+e.getValue();

            // same...
            notchToSrg.write("FD: "+e.getKey()+" "+e.getValue());
            notchToSrg.newLine();

            temp = e.getValue().substring(e.getValue().lastIndexOf('/')+1);
            mcpName = e.getValue();
            if (fields.containsKey(temp))
                mcpName = mcpName.replace(temp, fields.get(temp));

            // SRG and MCP have the same class names
            notchToMcp.write("FD: "+e.getKey()+" "+mcpName);
            notchToMcp.newLine();

            // srg name -> mcp name
            srgToMcp.write("FD: "+e.getValue()+" "+mcpName);
            srgToMcp.newLine();

            // mcp name -> srg name
            mcpToSrg.write("FD: "+mcpName+" "+e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("FD: "+mcpName+" "+e.getKey());
            mcpToNotch.newLine();
        }

        // methods
        for (Entry<MethodData, MethodData> e : inSrg.methodMap.entrySet())
        {
            line = "MD: "+e.getKey()+" "+e.getValue();

            // same...
            notchToSrg.write("MD: "+e.getKey()+" "+e.getValue());
            notchToSrg.newLine();

            temp = e.getValue().name.substring(e.getValue().name.lastIndexOf('/')+1);
            mcpName = e.getValue().toString();
            if (methods.containsKey(temp))
                mcpName = mcpName.replace(temp, methods.get(temp));

            // SRG and MCP have the same class names
            notchToMcp.write("MD: "+e.getKey()+" "+mcpName);
            notchToMcp.newLine();

            // srg name -> mcp name
            srgToMcp.write("MD: "+e.getValue()+" "+mcpName);
            srgToMcp.newLine();

            // mcp name -> srg name
            mcpToSrg.write("MD: "+mcpName+" "+e.getValue());
            mcpToSrg.newLine();

            // output is notch
            mcpToNotch.write("MD: "+mcpName+" "+e.getKey());
            mcpToNotch.newLine();
        }

        notchToSrg.flush();
        notchToSrg.close();

        notchToMcp.flush();
        notchToMcp.close();

        srgToMcp.flush();
        srgToMcp.close();

        mcpToSrg.flush();
        mcpToSrg.close();

        mcpToNotch.flush();
        mcpToNotch.close();
    }

    private void writeOutExcs(Map<String, String> excRemap, Map<String, String> methods) throws IOException
    {
        // ensure folders exist
        Files.createParentDirs(getSrgExc());
        Files.createParentDirs(getMcpExc());

        // create streams
        BufferedWriter srgOut = Files.newWriter(getSrgExc(), Charsets.UTF_8);
        BufferedWriter mcpOut = Files.newWriter(getMcpExc(), Charsets.UTF_8);

        // read and write existing lines
        List<String> excLines = Files.readLines(getInExc(), Charsets.UTF_8);
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
            if (sigIndex == -1 || dotIndex == -1)
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
/*
    private String remapMethodName(String qualified, String notchSig, Map<String, String> classMap, Map<MethodData, MethodData> methodMap)
    {

        for (MethodData data : methodMap.keySet())
        {
            if (data.name.equals(qualified))
                return methodMap.get(data).name;
        }

        String cls = qualified.substring(0, qualified.lastIndexOf('/'));
        String name = qualified.substring(cls.length() + 1);
        getProject().getLogger().lifecycle(qualified);
        getProject().getLogger().lifecycle(cls + " " + name);

        String ret = classMap.get(cls);
        if (ret != null)
            cls = ret;

        return cls + '/' + name;
    }

    private String remapSig(String sig, Map<String, String> classMap)
    {
        StringBuilder newSig = new StringBuilder(sig.length());

        int last = 0;
        int start = sig.indexOf('L');
        while(start != -1)
        {
            newSig.append(sig.substring(last, start));
            int next = sig.indexOf(';', start);
            newSig.append('L').append(remap(sig.substring(start + 1, next), classMap)).append(';');
            last = next + 1;
            start = sig.indexOf('L', next);
        }
        newSig.append(sig.substring(last));

        return newSig.toString();
    }

    private static String remap(String thing, Map<String, String> map)
    {
        if (map.containsKey(thing))
            return map.get(thing);
        else
            return thing;
    }
*/
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
