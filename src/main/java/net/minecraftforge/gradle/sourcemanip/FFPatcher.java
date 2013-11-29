package net.minecraftforge.gradle.sourcemanip;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.code.regexp.Pattern;
import com.google.code.regexp.Matcher;

import net.minecraftforge.gradle.common.Constants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FFPatcher
{
    static final String MODIFIERS = "public|protected|private|static|abstract|final|native|synchronized|transient|volatile|strictfp";

    public static final Pattern SYNTHETICS = Pattern.compile("(?m)(\\s*// \\$FF: (synthetic|bridge) method(\\r\\n|\\n|\\r)){1,2}\\s*(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<return>.+?) (?<method>.+?)\\((?<arguments>.*)\\)\\s*\\{(\\r\\n|\\n|\\r)\\s*return this\\.(?<method2>.+?)\\((?<arguments2>.*)\\);(\\r\\n|\\n|\\r)\\s*\\}");
    public static final Pattern TYPECAST = Pattern.compile("\\([\\w\\.]+\\)");

    // Remove TRAILING whitespace
    public static final String TRAILING = "(?m)[ \\t]+$";

    //Remove repeated blank lines
    public static final String NEWLINES = "(?m)^(\\r\\n|\\r|\\n){2,}";

    public static final Pattern MODIFIERS_REG = Pattern.compile("(" + MODIFIERS + ")");
    public static final String LIST = ", ";

    // modifiers, type, name, implements, body, end
    public static final Pattern ENUM_CLASS = Pattern.compile("(?m)^(?<modifiers>(?:(?:" + MODIFIERS + ") )*)(?<type>enum) (?<name>[\\w$]+)(?: implements (?<implements>[\\w$.]+(?:, [\\w$.]+)*))? \\{(?:\\r\\n|\\r|\\n)(?<body>(?:.*(?:\\r\\n|\\n|\\r))*?)(?<end>\\})");

    // name, body, end
    public static final Pattern ENUM_ENTRIES = Pattern.compile("(?m)^ +(?<name>[\\w$]+)\\(\"(?:[\\w$]+)\", [0-9]+(?:, (?<body>.*?))?\\)(?<end>(?:;|,)(?:\\r\\n|\\n|\\r)+)");

    public static final String EMPTY_SUPER = "(?m)^ +super\\(\\);(\\r\\n|\\n|\\r)";

    // strip TRAILING 0 from doubles and floats to fix decompile differences on OSX
    // 0.0010D => 0.001D
    // value, type
    public static final String TRAILINGZERO = "([0-9]+\\.[0-9]*[1-9])0+([DdFfEe])";

    // modifiers, params, throws, empty, body, end
    public static final String CONSTRUCTOR = "(?m)^ +(?<modifiers>(?:(?:" + MODIFIERS + ") )*)%s\\((?<parameters>.*?)\\)(?: throws (?<throws>[\\w$.]+(?:, [\\w$.]+)*))? \\{(?:(?<empty>\\}(?:\\r\\n|\\r|\\n)+)|(?:(?<body>(?:\\r\\n|\\r|\\n)(?:.*?(?:\\r\\n|\\r|\\n))*?)(?<end> {3}\\}(?:\\r\\n|\\r|\\n)+)))";

    public static final String ENUM_VALS = "(?m)^ +// \\$FF: synthetic field(\\r\\n|\\n|\\r) +private static final %s\\[\\] [$\\w]+ = new %s\\[\\]\\{.*?\\};(\\r\\n|\\n|\\r)";

    public static String processFile(String fileName, String text) throws IOException
    {
        String classname = fileName.split("\\.")[0];
        
        StringBuffer out = new StringBuffer();
        Matcher m = SYNTHETICS.matcher(text);
        while(m.find())
        {
            m.appendReplacement(out, synthetic_replacement(m).replace("$", "\\$"));
        }
        m.appendTail(out);
        text = out.toString();

        text = text.replaceAll(TRAILING, "");

        Matcher match = ENUM_CLASS.matcher(text);
        while (match.find())
        {
            // defaults.. inc ase the body isnt there
            if (!classname.equals(match.group("name")))
            {
                throw new RuntimeException("ERROR PARSING ENUM !!!!! Class Name != File Name");
            }

            // find all modifiers
            ArrayList<String> mods = new ArrayList<String>();
            Matcher modMatch = MODIFIERS_REG.matcher(match.group("modifiers"));
            while (modMatch.find())
            {
                mods.add(modMatch.group());
            }

            // check modifiers
            if (!Strings.isNullOrEmpty(match.group("modifiers")) && mods.isEmpty())
            {
                throw new RuntimeException("ERROR PARSING ENUM !!!!! no modifiers!");
            }

            List<String> interfaces = new ArrayList<String>();
            if (!Strings.isNullOrEmpty(match.group("implements")))
            {
                interfaces = Arrays.asList(match.group("implements").split(LIST));
            }

            text = text.replace(match.group(), processEnum(classname, match.group("type"), mods, interfaces, match.group("body"), match.group("end")));
        }

        text = text.replaceAll(EMPTY_SUPER, "");
        text = text.replaceAll(TRAILINGZERO, "");
        text = text.replaceAll(NEWLINES, Constants.NEWLINE);

        text = text.replaceAll("(\\r\\n|\\r|\\n)", Constants.NEWLINE);
        text = text.replaceAll("(\r\n|\r|\n)", Constants.NEWLINE);

        return text;
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

    private static String processEnum(String classname, String classtype, List<String> modifiers, List<String> interfaces, String body, String end)
    {
        Matcher match = ENUM_ENTRIES.matcher(body);
        while (match.find())
        {
            // defaults.. in case the body isnt there
            String entryBody = "";

            if (!Strings.isNullOrEmpty(match.group("body")))
            {
                entryBody = "(" + match.group("body") + ")";
            }

            body = body.replace(match.group(), "   " + match.group("name") + entryBody + match.group("end"));
        }

        String valuesRegex = String.format(ENUM_VALS, classname, classname);
        body = body.replaceAll(valuesRegex, "");

        String conRegex = String.format(CONSTRUCTOR, classname);
        match = Pattern.compile(conRegex).matcher(body);

        // process constructors
        while (match.find())
        {
            // find all modifiers
            ArrayList<String> mods = new ArrayList<String>();
            Matcher modMatch = MODIFIERS_REG.matcher(match.group("modifiers"));
            while (modMatch.find())
            {
                mods.add(modMatch.group());
            }

            // check modifiers
            if (!Strings.isNullOrEmpty(match.group("modifiers")) && mods.isEmpty())
            {
                throw new RuntimeException("ERROR PARSING ENUM CONSTRUCTOR! !!!!! no modifiers!");
            }

            List<String> params = new ArrayList<String>();
            if (!Strings.isNullOrEmpty(match.group("parameters")))
            {
                params = Arrays.asList(match.group("parameters").split(LIST));
            }

            List<String> exc = new ArrayList<String>();
            if (!Strings.isNullOrEmpty(match.group("throws")))
            {
                exc = Arrays.asList(match.group("throws").split(LIST));
            }

            String methodBody, methodEnd;
            if (!Strings.isNullOrEmpty(match.group("empty")))
            {
                methodBody = "";
                methodEnd = match.group("empty");
            }
            else
            {
                methodBody = match.group("body");
                methodEnd = match.group("end");
            }

            body = body.replace(match.group(), processConstructor(classname, mods, params, exc, methodBody, methodEnd));
        }

        // rebuild enum
        StringBuilder out = new StringBuilder("");

        if (!modifiers.isEmpty())
        {
            out.append(Joiner.on(" ").join(modifiers)).append(" ");
        }

        out.append(classtype).append(' ').append(classname);

        if (!interfaces.isEmpty())
        {
            out.append(" implements ").append(Joiner.on(", ").join(interfaces));
        }

        out.append(" {").append(Constants.NEWLINE).append(body).append(end);

        return out.toString();
    }

    private static String processConstructor(String classname, List<String> mods, List<String> params, List<String> exc, String methodBody, String methodEnd)
    {
        if (params.size() >= 2)
        {
            // special case?
            if (params.get(0).startsWith("String ") && params.get(1).startsWith("int "))
            {
                params = params.subList(2, params.size());

                // empty CONSTRUCTOR
                if (Strings.isNullOrEmpty(methodBody) && params.isEmpty())
                {
                    return "";
                }
            }
            else
            {
                throw new RuntimeException("invalid initial parameters in enum");
            }
            // ERROR
        }
        else
        {
            throw new RuntimeException("not enough parameters in enum");
        }

        // rebuild CONSTRUCTOR

        StringBuilder out = new StringBuilder("   ");
        if (mods != null && !mods.isEmpty())
        {
            out.append(Joiner.on(" ").join(mods)).append(" ");
        }

        out.append(classname).append("(").append(Joiner.on(", ").join(params)).append(")");

        if (exc != null && !exc.isEmpty())
        {
            out.append(" throws ").append(Joiner.on(", ").join(exc));
        }

        out.append(" {").append(methodBody).append(methodEnd);

        return out.toString();
    }
}
