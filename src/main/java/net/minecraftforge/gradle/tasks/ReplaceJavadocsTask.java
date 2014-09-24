package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.extrastuff.JavadocAdder;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
//import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public class ReplaceJavadocsTask extends DefaultTask
{
    @InputFiles
    private LinkedList<DelayedFile>                     inFiles = new LinkedList<DelayedFile>();

    //@OutputFile
    private DelayedFile                            outFile;

    @InputFile
    private DelayedFile                            methodsCsv;

    @InputFile
    private DelayedFile                            fieldsCsv;
    private final Map<String, Map<String, String>> methods = new HashMap<String, Map<String, String>>();
    private final Map<String, Map<String, String>> fields  = new HashMap<String, Map<String, String>>();

    private static final Pattern                   METHOD  = Pattern.compile("^([ \t]+)// JAVADOC METHOD \\$\\$ (func.+?)$");
    private static final Pattern                   FIELD   = Pattern.compile("^([ \t]+)// JAVADOC FIELD \\$\\$ (field.+?)$");

    @TaskAction
    public void doStuffBefore() throws Throwable
    {
        readCsvs();

        // check directory and stuff...
        FileCollection in = getInFiles();
        File out = getOutFile();

        if (in.getFiles().size() == 1 && in.getSingleFile().getName().endsWith("jar"))
            jarMode(in.getSingleFile(), out);
        else
            dirMode(in.getFiles(), out);
    }

    private void readCsvs() throws IOException
    {
        // read the CSVs
        CSVReader reader = RemapSourcesTask.getReader(getMethodsCsv());
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            methods.put(s[0], temp);
        }

        reader = RemapSourcesTask.getReader(getFieldsCsv());
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            fields.put(s[0], temp);
        }
    }

    private void jarMode(File inJar, File outJar) throws IOException
    {
        if (!outJar.exists())
        {
            outJar.getParentFile().mkdirs();
            outJar.createNewFile();
        }

        final ZipInputStream zin = new ZipInputStream(new FileInputStream(inJar));
        ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(outJar));
        ZipEntry entry = null;
        String fileStr;

        while ((entry = zin.getNextEntry()) != null)
        {
            // no META or dirs. wel take care of dirs later.
            if (entry.getName().contains("META-INF"))
            {
                continue;
            }

            // resources or directories.
            if (entry.isDirectory() || !entry.getName().endsWith(".java"))
            {
                zout.putNextEntry(entry);
                ByteStreams.copy(zin, zout);
                zout.closeEntry();
            }
            else
            {
                // source!
                fileStr = new String(ByteStreams.toByteArray(zin), Charset.defaultCharset());
                fileStr = processFile(fileStr);

                zout.putNextEntry(entry);
                zout.write(fileStr.getBytes());
                zout.closeEntry();
            }
        }

        zin.close();
        zout.flush();
        zout.close();
    }

    private void dirMode(Set<File> inDirs, File outDir) throws IOException
    {
        if (!outDir.exists())
            outDir.mkdirs();

        for (File inDir : inDirs)
        {
            FileTree tree = getProject().fileTree(inDir);

            for (File file : tree)
            {
                String text = Files.toString(file, Charsets.UTF_8);
                text = processFile(text);

                File dest = getDest(file, inDir, outDir);
                dest.getParentFile().mkdirs();
                dest.createNewFile();
                Files.write(text, dest, Charsets.UTF_8);
            }
        }
    }

    private File getDest(File in, File base, File baseOut) throws IOException
    {
        String relative = in.getCanonicalPath().replace(base.getCanonicalPath(), "");
        return new File(baseOut, relative);
    }

    private String processFile(String text)
    {
        Matcher matcher;

        String prevLine = null;
        ArrayList<String> newLines = new ArrayList<String>();
        //ImmutableList<String> lines = StringUtils.lines(text);
        for (String line : StringUtils.lines(text))
        {
            //String line = lines.get(i);

            // check method
            matcher = METHOD.matcher(line);

            if (matcher.find())
            {
                String name = matcher.group(2);

                if (methods.containsKey(name) && methods.get(name).containsKey("name"))
                {
                    // get javadoc
                    String javadoc = methods.get(name).get("javadoc");

                    if (Strings.isNullOrEmpty(javadoc))
                    {
                        line = ""; // just delete the marker
                    }
                    else
                    {
                        // replace the marker
                        line = JavadocAdder.buildJavadoc(matcher.group(1), javadoc, true);

                        if (!Strings.isNullOrEmpty(prevLine) && !prevLine.endsWith("{"))
                        {
                            line = Constants.NEWLINE + line;
                        }
                    }
                }
            }

            // check field
            matcher = FIELD.matcher(line);

            if (matcher.find())
            {
                String name = matcher.group(2);

                if (fields.containsKey(name))
                {
                    // get javadoc
                    String javadoc = fields.get(name).get("javadoc");

                    if (Strings.isNullOrEmpty(javadoc))
                    {
                        line = ""; // just delete the marker
                    }
                    else
                    {
                        // replace the marker
                        line = JavadocAdder.buildJavadoc(matcher.group(1), javadoc, false);

                        if (!Strings.isNullOrEmpty(prevLine) && !prevLine.endsWith("{"))
                        {
                            line = Constants.NEWLINE + line;
                        }
                    }
                }
            }

            prevLine = line;
            newLines.add(line);
        }

        return Joiner.on(Constants.NEWLINE).join(newLines);
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

    public FileCollection getInFiles()
    {
        FileCollection collection = getProject().files(new Object[] {});
        
        for (DelayedFile file : inFiles)
            collection = collection.plus(getProject().files(file));
                
        return collection;
    }

    public void from(DelayedFile file)
    {
        inFiles.add(file);
    }

    public File getOutFile()
    {
        return outFile.call();
    }

    public void setOutFile(DelayedFile outFile)
    {
        this.outFile = outFile;
    }
}
