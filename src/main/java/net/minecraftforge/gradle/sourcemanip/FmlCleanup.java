package net.minecraftforge.gradle.sourcemanip;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;

import net.minecraftforge.gradle.StringUtils;
import net.minecraftforge.gradle.common.Constants;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FmlCleanup
{
    private static final Pattern METHOD_REG = Pattern.compile("^ {4}(\\w+\\s+\\S.*\\(.*|static)$");
    private static final Pattern CATCH_REG = Pattern.compile("catch \\((.*)\\)$");
    private static final Pattern NESTED_PERINTH = Pattern.compile("\\(.*\\(");
    private static final Pattern METHOD_PARAMS = Pattern.compile("\\((.+)\\)");
    private static final Pattern METHOD_DEC_END = Pattern.compile("(}|\\);|throws .+?;)$");
    private static final Pattern METHOD_END = Pattern.compile("^ {4}\\}$");
    private static final Pattern CAPS_START = Pattern.compile("^[A-Z]");
    private static final Pattern ARRAY = Pattern.compile("(\\[|\\.\\.\\.)");
    private static final Pattern VAR_CALL = Pattern.compile("(?i)[a-z_$][a-z0-9_\\[\\]]+ var\\d+");
    private static final Pattern VAR = Pattern.compile("var\\d+");

    private static final Comparator<String> COMPARATOR = new Comparator<String>()
    {
        @Override
        public int compare(String str1, String str2)
        {
            return str2.length() - str1.length();
        }
    };
    private static final Pattern CLASS = Pattern.compile("class (\\w+)");
    public static String renameClass(String text)
    {
        String[] lines = text.split("(\r\n|\r|\n)");
        String output = "";

        boolean insideMethod = false;
        String method = "";
        ArrayList<String> methodVars = new ArrayList<String>();
        boolean skip = false;

        for (String line : lines)
        {
            // if re.search(METHOD_REG, line) and not re.search('=', line) and not re.search(r'\(.*\(', line):
            if (METHOD_REG.matcher(line).find() && !line.contains("=") && !NESTED_PERINTH.matcher(line).find())
            {
                // if re.search(r'\(.+\)', line):
                Matcher match = METHOD_PARAMS.matcher(line);
                if (match.find())
                {
                    // method_variables += [s.strip() for s in re.search(r'\((.+)\)', line).group(1).split(',')]
                    for (String str : Splitter.on(',').trimResults().split(match.group(1)))
                    {
                        methodVars.add(str);
                    }
                }

                method += line + Constants.NEWLINE;
                // method += line

                // single line method?
                skip = true;

                // if not re.search(r'(}|\);|throws .+?;)$', line):
                if (!METHOD_DEC_END.matcher(line).find())
                {
                    insideMethod = true;
                }
            }

            //elif re.search(r'^ {%s}}$' % indent, line):
            else if (METHOD_END.matcher(line).find())
            {
                //inside_method = False
                insideMethod = false;
            }

            // inside method actions now.
            if (insideMethod)
            {
                if (skip)
                {
                    skip = false;
                    continue;
                }

                method += line + Constants.NEWLINE;

                Matcher matcher = CATCH_REG.matcher(line);
                if (matcher.find())
                {
                    methodVars.add(matcher.group(1));
                }
                else
                {
                    Matcher match = VAR_CALL.matcher(line);
                    while (match.find())
                    {
                        if (!match.group().startsWith("return") && !match.group().startsWith("throw"))
                        {
                            methodVars.add(match.group());
                        }
                    }
                }
            }
            else
            {
                if (!Strings.isNullOrEmpty(method))
                {
                    FmlCleanup namer = new FmlCleanup();
                    HashMap<String, String> todo = new HashMap<String, String>();

                    for (String var : methodVars)
                    {
                        String[] split = var.split(" ");
                        if (split.length > 1)
                        {
                            todo.put(split[1], namer.getName(split[0], split[1]));
                        }
                        else
                        {
                            System.out.printf("Unknown thing : %s (%s)\n", var, method);
                        }
                    }

                    List<String> sortedKeys = new ArrayList<String>(todo.keySet());
                    Collections.sort(sortedKeys, COMPARATOR);

                    // closure changes the sort, to sort by the return value of the closure.
                    for (String key : sortedKeys)
                    {
                        if (VAR.matcher(key).matches())
                        {
                            method = method.replace(key, todo.get(key));
                        }
                    }

                    output += method;

                    // clear methods.
                    methodVars.clear();
                    method = "";
                }

                if (skip)
                {
                    skip = false;
                    continue;
                }

                output += line + Constants.NEWLINE;
            }
        }

        return output;
    }

    HashMap<String, Holder> last;
    HashMap<String, String> remap;

    private FmlCleanup()
    {
        last = new HashMap<String, Holder>();
        last.put("byte", new Holder(0, false, "b"));
        last.put("char", new Holder(0, false, "c"));
        last.put("short", new Holder(1, false, "short"));
        last.put("int", new Holder(0, true, "i", "j", "k", "l"));
        last.put("boolean", new Holder(0, true, "flag"));
        last.put("double", new Holder(0, false, "d"));
        last.put("float", new Holder(0, true, "f"));
        last.put("File", new Holder(1, true, "file"));
        last.put("String", new Holder(0, true, "s"));
        last.put("Class", new Holder(0, true, "oclass"));
        last.put("Long", new Holder(0, true, "olong"));
        last.put("Byte", new Holder(0, true, "obyte"));
        last.put("Short", new Holder(0, true, "oshort"));
        last.put("Boolean", new Holder(0, true, "obool"));
        last.put("Package", new Holder(0, true, "opackage"));

        remap = new HashMap<String, String>();
        remap.put("long", "int");
    }

    private String getName(String type, String var)
    {
        String index = null;
        String findtype = type;
        while (findtype.contains("[][]"))
        {
            findtype = findtype.replaceAll("\\[\\]\\[\\]", "[]");
        }
        if (last.containsKey(findtype))
        {
            index = findtype;
        }
        else if (remap.containsKey(type))
        {
            index = remap.get(type);
        }

        if (Strings.isNullOrEmpty(index) && (CAPS_START.matcher(type).find() || ARRAY.matcher(type).find()))
        {
            // replace multi things with arrays.
            type = type.replace("...", "[]");

            while (type.contains("[][]"))
            {
                type = type.replaceAll("\\[\\]\\[\\]", "[]");
            }

            String name = StringUtils.lower(type);
            // Strip single dots that might happen because of inner class references
            name = name.replace(".", "");
            boolean skip_zero = true;

            if (Pattern.compile("\\[").matcher(type).find())
            {
                skip_zero = true;
                name = "a" + name;
                name = name.replace("[]", "").replace("...", "");
            }

            last.put(type, new Holder(0, skip_zero, name));
            index = type;
        }

        if (Strings.isNullOrEmpty(index))
        {
            //TODO: Debug: System.out.println("NO DATA FOR TYPE " + type + " " + var);
            return StringUtils.lower(type);
        }

        Holder holder = last.get(index);
        int id = holder.id;
        List<String> data = holder.data;

        int ammount = data.size();

        String name;
        if (ammount == 1)
        {
            name = data.get(0) + (id == 0 && holder.skip_zero ? "" : id);
        }
        else
        {
            int num = id / ammount;
            name = data.get(id % ammount) + (id < ammount && holder.skip_zero ? "" : num);
        }

        holder.id++;
        return name;
    }

    private class Holder
    {
        public int id;
        public boolean skip_zero;
        public final ArrayList<String> data;

        public Holder(int t1, boolean skip_zero, String... stuff)
        {
            this.id = t1;
            this.skip_zero = skip_zero;
            this.data = new ArrayList<String>();

            Collections.addAll(this.data, stuff);
        }
    }
}
