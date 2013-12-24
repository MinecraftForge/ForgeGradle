package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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
                        line = buildJavadoc(matcher.group(1), javadoc, true);

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
                        line = buildJavadoc(matcher.group(1), javadoc, false);

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

    private String buildJavadoc(String indent, String javadoc, boolean isMethod)
    {
        StringBuilder builder = new StringBuilder();

        if (javadoc.length() >= 70 || isMethod)
        {
            List<String> list = wrapText(javadoc, 120 - (indent.length() + 3));

            builder.append(indent);
            builder.append("/**");
            builder.append(Constants.NEWLINE);

            for (String line : list)
            {
                builder.append(indent);
                builder.append(" * ");
                builder.append(line);
                builder.append(Constants.NEWLINE);
            }

            builder.append(indent);
            builder.append(" */");

        }
        // one line
        else
        {
            builder.append(indent);
            builder.append("/** ");
            builder.append(javadoc);
            builder.append(" */");
        }

        return builder.toString().replace(indent, indent);
    }

    private static List<String> wrapText(String text, int len)
    {
        // return empty array for null text
        if (text == null)
        {
            return new ArrayList<String>();
        }

        // return text if len is zero or less
        if (len <= 0)
        {
            return new ArrayList<String>(Arrays.asList(text));
        }

        // return text if less than length
        if (text.length() <= len)
        {
            return new ArrayList<String>(Arrays.asList(text));
        }

        List<String> lines = new ArrayList<String>();
        StringBuilder line = new StringBuilder();
        StringBuilder word = new StringBuilder();
        int tempNum;

        // each char in array
        for (char c : text.toCharArray())
        {
            // its a wordBreaking character.
            if (c == ' ' || c == ',' || c == '-')
            {
                // add the character to the word
                word.append(c);

                // its a space. set TempNum to 1, otherwise leave it as a wrappable char
                tempNum = Character.isWhitespace(c) ? 1 : 0;

                // subtract tempNum from the length of the word
                if ((line.length() + word.length() - tempNum) > len)
                {
                    lines.add(line.toString());
                    line.delete(0, line.length());
                }

                // new word, add it to the next line and clear the word
                line.append(word);
                word.delete(0, word.length());

            }
            // not a linebreak char
            else
            {
                // add it to the word and move on
                word.append(c);
            }
        }

        // handle any extra chars in current word
        if (word.length() > 0)
        {
            if ((line.length() + word.length()) > len)
            {
                lines.add(line.toString());
                line.delete(0, line.length());
            }
            line.append(word);
        }

        // handle extra line
        if (line.length() > 0)
        {
            lines.add(line.toString());
        }

        List<String> temp = new ArrayList<String>(lines.size());
        for (String s : lines)
        {
            temp.add(s.trim());
        }
        return temp;
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
