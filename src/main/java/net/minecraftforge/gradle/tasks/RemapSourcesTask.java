package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.extrastuff.JavadocAdder;
import net.minecraftforge.gradle.tasks.abstractutil.EditJarTask;

import org.gradle.api.tasks.InputFile;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;

public class RemapSourcesTask extends EditJarTask
{
    @InputFile
    private DelayedFile                            methodsCsv;

    @InputFile
    private DelayedFile                            fieldsCsv;

    @InputFile
    private DelayedFile                            paramsCsv;
    
    private boolean doesJavadocs = false;
    private boolean noJavadocs = false;

    private final Map<String, Map<String, String>> methods    = new HashMap<String, Map<String, String>>();
    private final Map<String, Map<String, String>> fields     = new HashMap<String, Map<String, String>>();
    private final Map<String, String>              params     = new HashMap<String, String>();

    private static final Pattern                   SRG_FINDER = Pattern.compile("(func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_)([^\\w\\$])");
    private static final Pattern                   METHOD     = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+(func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern                   FIELD      = Pattern.compile("^((?: {4})+|\\t+)(?:[\\w$.\\[\\]]+ )+(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");

    @Override
    public void doStuffBefore() throws Exception
    {
        readCsvFiles();
    }

    @Override
    public String asRead(String text)
    {
        Matcher matcher;
        ArrayList<String> newLines = new ArrayList<String>();
        for (String line : StringUtils.lines(text))
        {
            if (noJavadocs) // noajavadocs? dont bothe with the rest of this crap...
            {
                newLines.add(replaceInLine(line));
                continue;
            }
            
            
            matcher = METHOD.matcher(line);
            if (matcher.find())
            {
                String name = matcher.group(2);

                if (methods.containsKey(name) && methods.get(name).containsKey("name"))
                {
                    String javadoc = methods.get(name).get("javadoc");
                    if (!Strings.isNullOrEmpty(javadoc))
                    {
                        if (doesJavadocs)
                            javadoc = JavadocAdder.buildJavadoc(matcher.group(1), javadoc, true);
                        else
                            javadoc = matcher.group(1) + "// JAVADOC METHOD $$ " + name;
                        insetAboveAnnotations(newLines, javadoc);
                    }
                }
            }
            else if (line.trim().startsWith("// JAVADOC "))
            {
                Matcher match = SRG_FINDER.matcher(line);
                if (match.find())
                {
                    String indent = line.substring(0, line.indexOf("// JAVADOC"));
                    String name = match.group();
                    if (name.startsWith("func_"))
                    {
                        Map<String, String> mtd = methods.get(name);
                        if (mtd != null && !Strings.isNullOrEmpty(mtd.get("javadoc")))
                        {
                            line = JavadocAdder.buildJavadoc(indent, mtd.get("javadoc"), true);
                        }
                    }
                    else if (name.startsWith("field_"))
                    {
                        Map<String, String> fld = fields.get(name);
                        if (fld != null && !Strings.isNullOrEmpty(fld.get("javadoc")))
                        {
                            line = JavadocAdder.buildJavadoc(indent, fld.get("javadoc"), true);
                        }
                    }

                    if (line.endsWith(Constants.NEWLINE))
                    {
                        line = line.substring(0, line.length() - Constants.NEWLINE.length());
                    }
                }
            }
            else
            {
                matcher = FIELD.matcher(line);
                if (matcher.find())
                {
                    String name = matcher.group(2);
                    if (fields.containsKey(name))
                    {
                        String javadoc = fields.get(name).get("javadoc");
                        if (!Strings.isNullOrEmpty(javadoc))
                        {
                            if (doesJavadocs)
                                javadoc = JavadocAdder.buildJavadoc(matcher.group(1), javadoc, false);
                            else
                                javadoc = matcher.group(1) + "// JAVADOC FIELD $$ " + name;
                            insetAboveAnnotations(newLines, javadoc);
                        }
                    }
                }
            }
            newLines.add(replaceInLine(line));
        }

        return Joiner.on(Constants.NEWLINE).join(newLines);
    }
    
    private void insetAboveAnnotations(List<String> list, String line)
    {
        int back = 0;
        while (list.get(list.size() - 1 - back).trim().startsWith("@"))
        {
            back++;
        }
        list.add(list.size() - back, line);
    }
    
    private String replaceInLine(String line)
    {
        // FAR all methods
        StringBuffer buf = new StringBuffer();
        Matcher matcher = SRG_FINDER.matcher(line);
        while (matcher.find())
        {
            String find = matcher.group(1);
            
            if (find.startsWith("p_"))
                find = params.get(find);
            else if (find.startsWith("func_"))
                find = stupidMacro(methods, find);
            else if (find.startsWith("field_"))
                find = stupidMacro(fields, find);
            
            if (find == null)
                find = matcher.group(1);
            
            matcher.appendReplacement(buf, find);
            buf.append(matcher.group(2));
        }
        matcher.appendTail(buf);
        return buf.toString();
    }

    private String stupidMacro(Map<String, Map<String, String>> map, String key)
    {
        Map<String, String> s = map.get(key);
        return s == null ? null : s.get("name");
    }

    private void readCsvFiles() throws IOException
    {
        CSVReader reader = getReader(getMethodsCsv());
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            methods.put(s[0], temp);
        }

        reader = getReader(getFieldsCsv());
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            fields.put(s[0], temp);
        }

        reader = getReader(getParamsCsv());
        for (String[] s : reader.readAll())
        {
            params.put(s[0], s[1]);
        }
    }

    public static CSVReader getReader(File file) throws IOException
    {
        return new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.NULL_CHARACTER, 1, false);
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

    public File getParamsCsv()
    {
        return paramsCsv.call();
    }

    public void setParamsCsv(DelayedFile paramsCsv)
    {
        this.paramsCsv = paramsCsv;
    }
    
    public boolean doesJavadocs()
    {
        return doesJavadocs;
    }

    public void setDoesJavadocs(boolean javadoc)
    {
        this.doesJavadocs = javadoc;
    }
    
    public void setNoJavadocs()
    {
        noJavadocs = true;
    }

    @Override
    public void doStuffAfter() throws Exception
    {
    }

    @Override
    public void doStuffMiddle() throws Exception
    {
    }
}
