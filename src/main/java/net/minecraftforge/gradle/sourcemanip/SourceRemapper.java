package net.minecraftforge.gradle.sourcemanip;

import au.com.bytecode.opencsv.CSVParser;
import au.com.bytecode.opencsv.CSVReader;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.io.Files;

import net.minecraftforge.gradle.common.Constants;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class SourceRemapper
{
    Map<String, Map<String, String>> methods = new HashMap<String, Map<String, String>>();
    Map<String, Map<String, String>> fields = new HashMap<String, Map<String, String>>();
    Map<String, String> params = new HashMap<String, String>();

    private static final Pattern METHOD_SMALL = Pattern.compile("func_[0-9]+_[a-zA-Z_]+");
    private static final Pattern FIELD_SMALL = Pattern.compile("field_[0-9]+_[a-zA-Z_]+");
    private static final Pattern PARAM = Pattern.compile("p_[\\w]+_\\d+_");
    private static final Pattern METHOD = Pattern.compile("^( {4}|\\t)(?:[\\w$.\\[\\]]+ )*(func_[0-9]+_[a-zA-Z_]+)\\(");
    private static final Pattern FIELD = Pattern.compile("^( {4}|\\t)(?:[\\w$.\\[\\]]+ )*(field_[0-9]+_[a-zA-Z_]+) *(?:=|;)");

    SourceRemapper(Map<String, File> files) throws IOException
    {
        CSVReader reader = getReader(files.get("methods"));
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            methods.put(s[0], temp);
        }


        reader = getReader(files.get("fields"));
        for (String[] s : reader.readAll())
        {
            Map<String, String> temp = new HashMap<String, String>();
            temp.put("name", s[1]);
            temp.put("javadoc", s[3]);
            fields.put(s[0], temp);
        }


        reader = getReader(files.get("params"));
        for (String[] s : reader.readAll())
        {
            params.put(s[0], s[1]);
        }
    }

    private CSVReader getReader(File file) throws IOException
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

    static List<String> wrapText(String text, int len)
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

    public void remapFile(File file) throws IOException
    {
        String text = Files.toString(file, Charset.defaultCharset());
        Matcher matcher;

        String prevLine = null;
        ArrayList<String> newLines = new ArrayList<String>();
        for (String line : Files.readLines(file, Charset.defaultCharset()))
        {

            // check method
            matcher = METHOD.matcher(line);

            if (matcher.find())
            {
                String name = matcher.group(2);

                if (methods.containsKey(name) && methods.get(name).containsKey("name"))
                {
                    ;
                }
                {
                    line = line.replace(name, methods.get(name).get("name"));

                    // get javadoc
                    if (methods.get(name).containsKey("javadoc"))
                    {
                        line = buildJavadoc(matcher.group(1), methods.get(name).get("javadoc"), true) + line;

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
                    if (fields.get(name).get("javadoc") != null)
                    {
                        line = buildJavadoc(matcher.group(1), fields.get(name).get("javadoc"), false) + line;

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

        // FAR all parameters
        matcher = PARAM.matcher(text);
        while (matcher.find())
        {
            if (params.containsKey(matcher.group()))
            {
                matcher.replaceFirst(params.get(matcher.group()));
            }
        }

        // FAR all methods
        matcher = METHOD_SMALL.matcher(text);
        while (matcher.find())
        {
            if (methods.containsKey(matcher.group()))
            {
                matcher.replaceFirst(methods.get(matcher.group()).get("name"));
            }
        }

        // FAR all fields
        matcher = FIELD_SMALL.matcher(text);
        while (matcher.find())
        {
            if (fields.containsKey(matcher.group()))
            {
                matcher.replaceFirst(fields.get(matcher.group()).get("name"));
            }
        }

        // write file
        Files.write(text, file, Charset.defaultCharset());
    }
}