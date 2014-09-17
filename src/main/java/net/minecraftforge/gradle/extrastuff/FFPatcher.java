package net.minecraftforge.gradle.extrastuff;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

public class FFPatcher
{
    static final String MODIFIERS = "public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp";

    private static final Pattern SYNTHETICS = Pattern.compile("(?m)(\\s*// \\$FF: (synthetic|bridge) method(\\r\\n|\\n|\\r)){1,2}\\s*(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<return>.+?) (?<method>.+?)\\((?<arguments>.*)\\)\\s*\\{(\\r\\n|\\n|\\r)\\s*return this\\.(?<method2>.+?)\\((?<arguments2>.*)\\);(\\r\\n|\\n|\\r)\\s*\\}");
    //private static final Pattern TYPECAST = Pattern.compile("\\([\\w\\.]+\\)");
    private static final Pattern ABSTRACT = Pattern.compile("(?m)^(?<indent>[ \\t\\f\\v]*)(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<return>[^ ]+) (?<method>func_(?<number>\\d+)_[a-zA-Z_]+)\\((?<arguments>([^ ,]+ (\\.\\.\\. )?var\\d+,? ?)*)\\)(?: throws (?:[\\w$.]+,? ?)+)?;$");

    // Remove TRAILING whitespace
    private static final String TRAILING = "(?m)[ \\t]+$";

    //Remove repeated blank lines
    private static final String NEWLINES = "(?m)^(\\r\\n|\\r|\\n){2,}";
    private static final String EMPTY_SUPER = "(?m)^[ \t]+super\\(\\);(\\r\\n|\\n|\\r)";

    // strip TRAILING 0 from doubles and floats to fix decompile differences on OSX
    // 0.0010D => 0.001D
    // value, type
    private static final String TRAILINGZERO = "([0-9]+\\.[0-9]*[1-9])0+([DdFfEe])";

    // new regexes
    private static final String CLASS_REGEX = "(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<type>enum|class|interface) (?<name>[\\w$]+)(?: (extends|implements) (?:[\\w$.]+(?:, [\\w$.]+)*))* \\{";
    private static final String ENUM_ENTRY_REGEX = "(?<name>[\\w$]+)\\(\"(?:[\\w$]+)\", [0-9]+(?:, (?<body>.*?))?\\)(?<end> *(?:;|,|\\{)$)";
    private static final String CONSTRUCTOR_REGEX = "(?<modifiers>(?:(?:" + MODIFIERS + ") )*)%s\\((?<parameters>.*?)\\)(?<end>(?: throws (?<throws>[\\w$.]+(?:, [\\w$.]+)*))? *(?:\\{\\}| \\{))";
    private static final String CONSTRUCTOR_CALL_REGEX = "(?<name>this|super)\\((?<body>.*?)\\)(?<end>;)";
    private static final String VALUE_FIELD_REGEX = "private static final %s\\[\\] [$\\w\\d]+ = new %s\\[\\]\\{.*?\\};";

    public static String processFile(String fileName, String text, boolean fixInterfaces) throws IOException
    {
        StringBuffer out = new StringBuffer();
        Matcher m = SYNTHETICS.matcher(text);
        while(m.find())
        {
            m.appendReplacement(out, synthetic_replacement(m).replace("$", "\\$"));
        }
        m.appendTail(out);
        text = out.toString();

        text = text.replaceAll(TRAILING, "");

        text = text.replaceAll(TRAILINGZERO, "$1$2");

        List<String> lines = new ArrayList<String>();
        lines.addAll(StringUtils.lines(text));

        processClass(lines, "", 0, "", ""); // mutates the list
        text = Joiner.on(Constants.NEWLINE).join(lines);

        text = text.replaceAll(NEWLINES, Constants.NEWLINE);
        text = text.replaceAll(EMPTY_SUPER, "");

        if (fixInterfaces)
        {
            out = new StringBuffer();
            m = ABSTRACT.matcher(text);
            while (m.find())
            {
                m.appendReplacement(out, abstract_replacement(m).replace("$", "\\$"));
            }
            m.appendTail(out);
            text = out.toString();
        }

        return text;
    }

    private static int processClass(List<String> lines, String indent, int startIndex, String qualifiedName, String simpleName)
    {
        Pattern classPattern = Pattern.compile(indent + CLASS_REGEX);

        for (int i = startIndex; i < lines.size(); i++)
        {
            String line = lines.get(i);

            // who knows.....
            if (Strings.isNullOrEmpty(line))
                continue;
            // ignore packages and imports
            else if (line.startsWith("package") || line.startsWith("import"))
                continue;

            Matcher matcher = classPattern.matcher(line);

            // found a class!
            if (matcher.find())
            {
                String newIndent;
                String classPath;
                if (Strings.isNullOrEmpty(qualifiedName))
                {
                    classPath = matcher.group("name");
                    newIndent = indent;
                }
                else
                {
                    classPath = qualifiedName + "." + matcher.group("name");
                    newIndent = indent+ "   ";
                }

                // fund an enum class, parse it seperately
                if (matcher.group("type").equals("enum"))
                    processEnum(lines, newIndent, i+1, classPath, matcher.group("name"));

                // nested class searching
                i = processClass(lines, newIndent, i+1, classPath, matcher.group("name"));
            }

            // class has finished
            if (line.startsWith(indent + "}"))
                return i;
        }

        return 0;
    }

    private static void processEnum(List<String> lines, String indent, int startIndex, String qualifiedName, String simpleName)
    {
        String newIndent = indent + "   ";
        Pattern enumEntry = Pattern.compile("^" + newIndent + ENUM_ENTRY_REGEX);
        Pattern constructor = Pattern.compile("^" + newIndent + String.format(CONSTRUCTOR_REGEX, simpleName));
        Pattern constructorCall = Pattern.compile("^" + newIndent + "   " + CONSTRUCTOR_CALL_REGEX);
        String formatted = newIndent + String.format(VALUE_FIELD_REGEX, qualifiedName, qualifiedName);
        Pattern valueField = Pattern.compile("^" + formatted);
        String newLine;
        boolean prevSynthetic = false;

        for (int i = startIndex; i < lines.size(); i++)
        {
            newLine = null;
            String line = lines.get(i);

            // find and replace enum entries
            Matcher matcher = enumEntry.matcher(line);
            if (matcher.find())
            {
                String body = matcher.group("body");

                newLine = newIndent + matcher.group("name");

                if (!Strings.isNullOrEmpty(body))
                {
                    String[] args = body.split(", ");

                    if (line.endsWith("{"))
                    {
                        if (args[args.length - 1].equals("null"))
                        {
                            args = Arrays.copyOf(args, args.length - 1);
                        }
                    }
                    body = Joiner.on(", ").join(args);
                }

                if (Strings.isNullOrEmpty(body))
                    newLine += matcher.group("end");
                else
                    newLine += "(" + body + ")" + matcher.group("end");
            }

            // find and replace constructor
            matcher = constructor.matcher(line);
            if (matcher.find())
            {
                StringBuilder tmp = new StringBuilder();
                tmp.append(newIndent).append(matcher.group("modifiers")).append(simpleName).append("(");

                String[] args = matcher.group("parameters").split(", ");
                for(int x = 2; x < args.length; x++)
                    tmp.append(args[x]).append(x < args.length - 1 ? ", " : "");
                tmp.append(")");

                tmp.append(matcher.group("end"));
                newLine = tmp.toString();

                if (args.length <= 2 && newLine.endsWith("}"))
                    newLine = "";
            }

            // find constructor calls...
            matcher = constructorCall.matcher(line);
            if (matcher.find())
            {
                String body = matcher.group("body");

                if (!Strings.isNullOrEmpty(body))
                {
                    String[] args = body.split(", ");
                    args = Arrays.copyOfRange(args, 2, args.length);
                    body = Joiner.on(", ").join(args);
                }

                newLine = newIndent + "   " + matcher.group("name") + "(" + body + ")" + matcher.group("end");
            }

            if (prevSynthetic)
            {
                matcher = valueField.matcher(line);
                if (matcher.find())
                    newLine = "";
            }

            if (line.contains("// $FF: synthetic field"))
            {
                newLine = "";
                prevSynthetic = true;
            }
            else
                prevSynthetic = false;

            if (newLine != null)
                lines.set(i, newLine);

            // class has finished.
            if (line.startsWith(indent + "}"))
                break;
        }
    }

    private static String synthetic_replacement(Matcher match)
    {
        //This is designed to remove all the synthetic/bridge methods that the compiler will just generate again
        //First off this only works on methods that bounce to methods that are named exactly alike.
        if (!match.group("method").equals(match.group("method2")))
            return match.group();

        //Next, we normalize the arugment list, if the lists are the same then it's a simple bounce method.
        //MC's code strips generic information so the compiler doesn't know to regen typecast methods
        //Uncomment the two lines below if we ever inject generic info
        String arg1 = match.group("arguments");
        String arg2 = match.group("arguments2");
        //String arg1 = _REGEXP['typecast'].sub(r'', match.group('arguments'))
        //String arg2 = _REGEXP['typecast'].sub(r'', match.group('arguments2'))

        if (arg1.equals(arg2) && arg1.equals(""))
            return "";

        String[] args = match.group("arguments").split(", ");
        for (int x = 0; x < args.length; x++)
            args[x] = args[x].split(" ")[1];

        StringBuilder b = new StringBuilder();
        b.append(args[0]);
        for (int x = 1; x < args.length; x++)
            b.append(", ").append(args[x]);
        arg1 = b.toString();

        if (arg1.equals(arg2))
            return "";

        return match.group();
    }

    private static String abstract_replacement(Matcher match)
    {
        String orig = match.group("arguments");
        String number = match.group("number");

        if (Strings.isNullOrEmpty(orig))
            return match.group();

        String[] args = orig.split(", ");
        StringBuilder fixed = new StringBuilder();
        for (int x = 0; x < args.length; x++)
        {
            String[] p = args[x].split(" ");
            if (p.length == 3) //varargs
            {
                p[0] = p[0] + " " + p[1];
                p[1] = p[2];
            }
            fixed.append(p[0]).append(" p_").append(number).append('_').append(p[1].substring(3)).append('_');
            if (x != args.length - 1)
                fixed.append(", ");
        }

        return match.group().replace(orig, fixed.toString());
    }
}
