package net.minecraftforge.gradle.tasks.user;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;

import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.sourcemanip.SourceRemapper;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import au.com.bytecode.opencsv.CSVReader;

public class GenSrgTask extends DefaultTask
{

    @InputFile
    private DelayedFile inSrg;

    @InputFile
    private DelayedFile methodsCsv;

    @InputFile
    private DelayedFile fieldsCsv;

    @OutputFile
    private DelayedFile deobfSrg;

    @OutputFile
    private DelayedFile reobfSrg;
    
    @TaskAction
    public void doTask() throws IOException
    {
        HashMap<String, String> methods = new HashMap<String, String>();
        HashMap<String, String> fields = new HashMap<String, String>();
        
        // read methods
        CSVReader csvReader = SourceRemapper.getReader(getMethodsCsv());
        for (String[] s : csvReader.readAll())
        {
            methods.put(s[0], s[1]);
        }

        // read fields
        csvReader = SourceRemapper.getReader(getFieldsCsv());
        for (String[] s : csvReader.readAll())
        {
            fields.put(s[0], s[1]);
        }
        
        
        File deobfFile = getDeobfSrg();
        File reobfFile = getReobfSrg();

        // verify files...
        if (!deobfFile.exists())
        {
            deobfFile.getParentFile().mkdirs();
            deobfFile.createNewFile();
        }
        if (!reobfFile.exists())
        {
            reobfFile.getParentFile().mkdirs();
            reobfFile.createNewFile();
        }
        
        // create streams
        BufferedReader srgIn = Files.newReader(getInSrg(), Charset.defaultCharset());
        BufferedWriter deobf = Files.newWriter(getDeobfSrg(), Charset.defaultCharset());
        BufferedWriter reobf = Files.newWriter(getReobfSrg(), Charset.defaultCharset());
        
        // IN
        // notch -> srg
        
        // deobf
        // notch -> deobf
        
        // reobf
        // mcp -> srg 
        
        String line, temp, in, out;
        String[] split;
        while ((line = srgIn.readLine()) != null)
        {
            if (line.startsWith("PK:"))
            {
                // nobody cares about the packages.
                deobf.write(line);
                deobf.newLine();
                
                reobf.write(line);
                reobf.newLine();
            }
            else if (line.startsWith("CL:"))
            {
                // deobf:  no change here...
                deobf.write(line);
                deobf.newLine();
                
                // reobf: same classes on both sides.
                split = line.split(" "); // 0=type  1=notch  2=srg=mcp
                reobf.write("CL: "+split[2]+" "+split[2]);
                reobf.newLine();
                
            }
            else if (line.startsWith("FD:"))
            {
                // deobf: need to rename that method.
                split = line.split(" "); // 0=type  1=notch  2=srg
                
                temp = split[2].substring(split[2].lastIndexOf('/'));
                out = split[2];
                
                if (fields.containsKey(temp))
                    out = split[2].replace(temp, fields.get(temp));
                
                deobf.write("FD: "+split[1]+" "+out);
                deobf.newLine();
                
                // reobf: reverse too
                reobf.write("FD: "+temp+" "+split[2]);
                reobf.newLine();
            }
            else if (line.startsWith("MD:"))
            {
                // deobf: rename that method.
                split = line.split(" "); // 0=type  1-2=notch  3-4=srg
                temp = split[3].substring(split[3].lastIndexOf('/'));
                
                in = split[1] + " " + split[2]; // notch
                out = split[3] + " " + split[4]; // srg
                
                if (methods.containsKey(temp))
                    out = out.replace(temp, methods.get(temp)); // now MCP
                
                deobf.write("MD: "+in+" "+out);
                deobf.newLine();
                
                // reobf reverse too
                reobf.write("MD: "+out+" "+split[3]+" "+split[4]);
                deobf.newLine();
                
            }
        }
        
        srgIn.close();
        deobf.flush();
        deobf.close();
        reobf.flush();
        reobf.close();
    }

    public File getInSrg()
    {
        return inSrg.call();
    }

    public void setInSrg(DelayedFile inSrg)
    {
        this.inSrg = inSrg;
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

    public File getDeobfSrg()
    {
        return deobfSrg.call();
    }

    public void setDeobfSrg(DelayedFile deobfSrg)
    {
        this.deobfSrg = deobfSrg;
    }

    public File getReobfSrg()
    {
        return reobfSrg.call();
    }

    public void setReobfSrg(DelayedFile reobfSrg)
    {
        this.reobfSrg = reobfSrg;
    }
}
