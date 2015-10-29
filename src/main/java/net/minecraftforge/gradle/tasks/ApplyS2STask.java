package net.minecraftforge.gradle.tasks;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import net.minecraftforge.gradle.SequencedInputSupplier;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.srg2source.rangeapplier.RangeApplier;
import net.minecraftforge.srg2source.util.io.FolderSupplier;
import net.minecraftforge.srg2source.util.io.InputSupplier;
import net.minecraftforge.srg2source.util.io.OutputSupplier;
import net.minecraftforge.srg2source.util.io.ZipInputSupplier;
import net.minecraftforge.srg2source.util.io.ZipOutputSupplier;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ApplyS2STask extends DefaultTask
{
    @InputFiles
    private final List<Object> srg = new LinkedList<Object>();

    @Optional
    @InputFiles
    private final List<Object> exc = new LinkedList<Object>();

    @InputFile
    private DelayedFile rangeMap;

    @Optional
    @InputFile
    private DelayedFile excModifiers;

    // stuff defined on the tasks..
    private final List<DelayedFile> in = new LinkedList<DelayedFile>();
    private DelayedFile out;

    @TaskAction
    public void doTask() throws IOException
    {
        List<File> ins = getIn();
        File out = getOut();
        File rangemap = getRangeMap();
        File rangelog = File.createTempFile("rangelog", ".txt", this.getTemporaryDir());
        FileCollection srg = getSrgs();
        FileCollection exc = getExcs();

        InputSupplier inSup;

        if (ins.size() == 0)
            return; // no input.
        else if (ins.size() == 1)
        {
            // just 1 supplier.
            inSup = getInput(ins.get(0));
        }
        else
        {
            // multinput
            inSup = new SequencedInputSupplier();
            for (File f : ins)
                ((SequencedInputSupplier) inSup).add(getInput(f));
        }

        OutputSupplier outSup;
        if (ins.size() == 1 && ins.get(0).equals(out) && ins instanceof FolderSupplier)
            outSup = (OutputSupplier) inSup;
        else
            outSup = getOutput(out);

        if (getExcModifiers() != null)
        {
            getLogger().lifecycle("creating default param names");
            exc = generateDefaultExc(getExcModifiers(), exc, srg);
        }

        getLogger().lifecycle("remapping source...");
        applyRangeMap(inSup, outSup, srg, exc, rangemap, rangelog);


        inSup.close();
        outSup.close();
    }

    private InputSupplier getInput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            ZipInputSupplier supp = new ZipInputSupplier();
            supp.readZip(f);
            return supp;
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
    }

    private OutputSupplier getOutput(File f) throws IOException
    {
        if (f.isDirectory())
            return new FolderSupplier(f);
        else if (f.getPath().endsWith(".jar") || f.getPath().endsWith(".zip"))
        {
            return new ZipOutputSupplier(f);
        }
        else
            throw new IllegalArgumentException("Can only make suppliers out of directories and zips right now!");
    }

    private void applyRangeMap(InputSupplier inSup, OutputSupplier outSup, FileCollection srg, FileCollection exc, File rangeMap, File rangeLog) throws IOException
    {
        RangeApplier app = new RangeApplier().readSrg(srg.getFiles());

        app.setOutLogger(Constants.getTaskLogStream(getProject(), this.getName() + ".log"));

        if (!exc.isEmpty())
        {
            app.readParamMap(exc);
        }

        // for debugging.
        app.dumpRenameMap();

        app.remapSources(inSup, outSup, rangeMap, false);
    }


    private FileCollection generateDefaultExc(File modifiers, FileCollection currentExcs, FileCollection srgs)
    {
        if (modifiers == null || !modifiers.exists())
            return currentExcs;

        Map<String, Boolean> statics = Maps.newHashMap();

        try
        {
            getLogger().debug("  Reading Modifiers:");
            for (String line : Files.readLines(modifiers, Charset.defaultCharset()))
            {
                if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                    continue;
                String[] args = line.split("=");
                statics.put(args[0], "static".equals(args[1]));
            }

            File temp = new File(this.getTemporaryDir(), "generated.exc");
            if (temp.exists())
                temp.delete();

            temp.getParentFile().mkdirs();
            temp.createNewFile();

            BufferedWriter writer = Files.newWriter(temp, Charsets.UTF_8);
            for (File f : srgs)
            {
                getLogger().debug("  Reading SRG: " + f);
                for (String line : Files.readLines(f, Charset.defaultCharset()))
                {
                    if (Strings.isNullOrEmpty(line) || line.startsWith("#"))
                        continue;

                    String type = line.substring(0, 2);
                    line = line.substring(4);
                    String[] pts = line.split(" ");

                    if (type.equals("MD"))
                    {
                        String name = pts[2].substring(pts[2].lastIndexOf('/') + 1);
                        if (name.startsWith("func_"))
                        {
                            Boolean isStatic = statics.get(pts[0] + pts[1]);
                            getLogger().debug("    MD: " + line);
                            name = name.substring(5, name.indexOf('_', 5));

                            List<String> params = Lists.newArrayList();
                            int idx = isStatic == null || !isStatic.booleanValue() ? 1 : 0;
                            getLogger().debug("      Name: " + name + " Idx: " + idx);

                            int i = 0;
                            boolean inArray = false;
                            while (i < pts[1].length())
                            {
                                char c = pts[1].charAt(i);

                                switch (c)
                                {
                                    case '(': //Start
                                        break;
                                    case ')': //End
                                        i = pts[1].length();
                                        break;
                                    case '[': //Array
                                        inArray = true;
                                        break;
                                    case 'L': //Class
                                        String right = pts[1].substring(i);
                                        String className = right.substring(1, right.indexOf(';'));
                                        i += className.length() + 1;
                                        params.add("p_" + name + "_" + idx++ + "_");
                                        inArray = false;
                                        break;
                                    case 'B':
                                    case 'C':
                                    case 'D':
                                    case 'F':
                                    case 'I':
                                    case 'J':
                                    case 'S':
                                    case 'Z':
                                        params.add("p_" + name + "_" + idx++ + "_");
                                        if ((c == 'D' || c == 'J') && !inArray) idx++;
                                        inArray = false;
                                        break;
                                    default:
                                        throw new IllegalArgumentException("Unrecognized type in method descriptor: " + c);
                                }
                                i++;
                            }

                            if (params.size() > 0)
                            {
                                writer.write(pts[2].substring(0, pts[2].lastIndexOf('/')));
                                writer.write('.');
                                writer.write(pts[2].substring(pts[2].lastIndexOf('/') + 1));
                                writer.write(pts[3]);
                                writer.write("=|");
                                writer.write(Joiner.on(",").join(params));
                                writer.newLine();
                            }
                        }
                    }
                }
            }
            writer.close();

            List<File> files = Lists.newArrayList();
            files.add(temp);//Make sure the new one is first to allow others to override
            for (File f : currentExcs)
                files.add(f);

            return getProject().files(files.toArray());
        }
        catch (IOException e)
        {
            Throwables.propagate(e);
        }

        return null;
    }

    @InputFiles
    public FileCollection getIns()
    {
        return getProject().files(in);
    }

    public List<File> getIn()
    {
        List<File> files = new LinkedList<File>();
        for (DelayedFile f : in)
            files.add(f.call());
        return files;
    }

    public void addIn(DelayedFile in)
    {
        this.in.add(in);
    }

    @OutputFiles
    public FileCollection getOuts()
    {
        File outFile = getOut();
        if (outFile.isDirectory())
            return getProject().fileTree(outFile);
        else
            return getProject().files(outFile);
    }

    public File getOut()
    {
        return out.call();
    }

    public void setOut(DelayedFile out)
    {
        this.out = out;
    }

    public FileCollection getSrgs()
    {
        return getProject().files(srg);
    }

    public void addSrg(DelayedFile srg)
    {
        this.srg.add(srg);
    }

    public void addSrg(String srg)
    {
        this.srg.add(srg);
    }

    public void addSrg(File srg)
    {
        this.srg.add(srg);
    }

    public FileCollection getExcs()
    {
        return getProject().files(exc);
    }

    public void addExc(DelayedFile exc)
    {
        this.exc.add(exc);
    }

    public void addExc(String exc)
    {
        this.exc.add(exc);
    }

    public void addExc(File exc)
    {
        this.exc.add(exc);
    }

    public File getRangeMap()
    {
        return rangeMap.call();
    }

    public void setRangeMap(DelayedFile rangeMap)
    {
        this.rangeMap = rangeMap;
    }

    public void setExcModifiers(DelayedFile value)
    {
        this.excModifiers = value;
    }

    public File getExcModifiers()
    {
        return this.excModifiers == null ? null : this.excModifiers.call();
    }
}
