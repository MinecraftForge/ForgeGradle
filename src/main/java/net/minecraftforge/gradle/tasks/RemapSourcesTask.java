package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
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

    private final Map<String, Map<String, String>> methods    = new HashMap<String, Map<String, String>>();
    private final Map<String, Map<String, String>> fields     = new HashMap<String, Map<String, String>>();
    private final Map<String, String>              params     = new HashMap<String, String>();

    private static final Pattern                   SRG_FINDER = Pattern.compile("func_[0-9]+_[a-zA-Z_]+|field_[0-9]+_[a-zA-Z_]+|p_[\\w]+_\\d+_");
    private static final Pattern                   METHOD     = Pattern.compile("^( {4}|\\t)(?:[\\w$.\\[\\]]+ )*(func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern                   FIELD      = Pattern.compile("^( {4}|\\t)(?:[\\w$.\\[\\]]+ )*(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");

    @Override
    public void doStuffBefore() throws Throwable
    {
        readCsvFiles();
    }

    @Override
    public String asRead(String text)
    {
        Matcher matcher;

        String prevLine = null;
        ArrayList<String> newLines = new ArrayList<String>();
        for (String line : StringUtils.lines(text))
        {

            // check method
            matcher = METHOD.matcher(line);

            if (matcher.find())
            {
                String name = matcher.group(2);

                if (methods.containsKey(name) && methods.get(name).containsKey("name"))
                {
                    line = line.replace(name, methods.get(name).get("name"));

                    // get javadoc
                    String javadoc = methods.get(name).get("javadoc");
                    if (!Strings.isNullOrEmpty(javadoc))
                    {
                        line = buildJavadoc(matcher.group(1), javadoc, true) + line;

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
                    line = line.replace(name, fields.get(name).get("name"));

                    // get javadoc
                    String javadoc = fields.get(name).get("javadoc");
                    if (!Strings.isNullOrEmpty(javadoc))
                    {
                        line = buildJavadoc(matcher.group(1), javadoc, false) + line;

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

        text = Joiner.on(Constants.NEWLINE).join(newLines) + Constants.NEWLINE;

        // FAR all methods
        StringBuffer buf = new StringBuffer();
        matcher = SRG_FINDER.matcher(text);
        while (matcher.find())
        {
            String find = matcher.group();
            
            if (find.startsWith("p_"))
                find = params.get(find);
            else if (find.startsWith("func_"))
                find = stupidMacro(methods, find);
            else if (find.startsWith("field_"))
                find = stupidMacro(fields, find);
            
            if (find == null)
                find = matcher.group();
            
            matcher.appendReplacement(buf, find);
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
        return new CSVReader(Files.newReader(file, Charset.defaultCharset()), CSVParser.DEFAULT_SEPARATOR, CSVParser.DEFAULT_QUOTE_CHARACTER, CSVParser.DEFAULT_ESCAPE_CHARACTER, 1, false);
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
            builder.append(Constants.NEWLINE);

        }
        // one line
        else
        {
            builder.append(indent);
            builder.append("/** ");
            builder.append(javadoc);
            builder.append(" */");
            builder.append(Constants.NEWLINE);
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

    public File getParamsCsv()
    {
        return paramsCsv.call();
    }

    public void setParamsCsv(DelayedFile paramsCsv)
    {
        this.paramsCsv = paramsCsv;
    }

    @Override
    public void doStuffAfter() throws Throwable
    {
    }

    @Override
    public void doStuffMiddle() throws Throwable
    {
    }
}
