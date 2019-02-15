/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.util.mcp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.minecraftforge.gradle.common.Constants;

import com.google.code.regexp.Matcher;
import com.google.code.regexp.Pattern;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

public class FmlCleanup
{
    //private static final Pattern METHOD_REG = Pattern.compile("^ {4}(\\w+\\s+\\S.*\\(.*|static)$");
    private static final Pattern METHOD_REG = Pattern.compile("^(?<indent>\\s+)(?<modifiers>(?:(?:" + FFPatcher.MODIFIERS + ") )*)(?:(?<return>[\\w\\[\\]\\.$]+) )?(?<name>[\\w$]+)\\((?<parameters>.*?)\\)(?<end>(?: throws (?<throws>[\\w$.]+(?:, [\\w$.]+)*))?)");
    private static final Pattern CATCH_REG = Pattern.compile("catch \\((.*)\\)$");
    private static final Pattern METHOD_DEC_END = Pattern.compile("(}|\\);|throws .+?;)$");
    private static final Pattern CAPS_START = Pattern.compile("^[A-Z]");
    private static final Pattern ARRAY = Pattern.compile("(\\[|\\.\\.\\.)");
    private static final Pattern VAR_CALL = Pattern.compile("(?i)[a-z_$][a-z0-9_\\[\\]]+ var\\d+(?:x)*");
    private static final Pattern VAR = Pattern.compile("var\\d+(?:x)*");

    private static final Comparator<String> COMPARATOR = new Comparator<String>()
    {
        @Override
        public int compare(String str1, String str2)
        {
            return str2.length() - str1.length();
        }
    };

    public static String renameClass(String text)
    {
        String[] lines = text.split("(\r\n|\r|\n)");
        List<String> output = new ArrayList<String>(lines.length);
        MethodInfo method = null;

        for (String line : lines)
        {
            Matcher matcher = METHOD_REG.matcher(line);
            boolean found = matcher.find();
            if (!line.endsWith(";") && !line.endsWith(",") && found)// && !line.contains("=") && !NESTED_PERINTH.matcher(line).find())
            {
                method = new MethodInfo(method, matcher.group("indent"));
                method.lines.add(line);

                boolean invalid = false; // Can't think of a better way to filter out enum declarations, so make sure that all the parameters have types
                String args = matcher.group("parameters");
                if (args != null)
                {
                    for (String str : Splitter.on(',').trimResults().omitEmptyStrings().split(args))
                    {
                        if (str.indexOf(' ') == -1)
                        {
                            invalid = true;
                            break;
                        }
                        method.addVar(str);
                    }
                }

                if (invalid || METHOD_DEC_END.matcher(line).find())
                {
                    if (method.parent != null)
                    {
                        method.parent.children.remove(method);
                    }
                    method = method.parent;

                    if (method == null) // dont output if there is a parent method.
                        output.add(line);
                }
            }
            else if (method != null && method.ENDING.equals(line))
            {
                method.lines.add(line);

                if (method.parent == null)
                {
                    for (String l : Splitter.on(Constants.NEWLINE).split(method.rename(null)))
                    {
                        output.add(l);
                    }
                }

                method = method.parent;
            }
            else if (method != null)
            {
                method.lines.add(line);
                matcher = CATCH_REG.matcher(line);
                if (matcher.find())
                {
                    method.addVar(matcher.group(1));
                }
                else
                {
                    matcher = VAR_CALL.matcher(line);
                    while (matcher.find())
                    {
                        String match = matcher.group();
                        if (!match.startsWith("return") && !match.startsWith("throw"))
                        {
                            method.addVar(match);
                        }
                    }
                }
            }
            else // If we get to here, then we are outside of all methods
            {
                output.add(line);
            }
        }

        return Joiner.on(Constants.NEWLINE).join(output);
    }

    private static class MethodInfo
    {
        private MethodInfo parent = null;
        private List<Object> lines = Lists.newArrayList();
        private List<String> vars = Lists.newArrayList();
        private List<MethodInfo> children = Lists.newArrayList();
        private final String ENDING;

        private MethodInfo(MethodInfo parent, String indent)
        {
            this.parent = parent;
            ENDING = indent + "}";
            if (parent != null)
            {
                parent.children.add(this);
                parent.lines.add(this);
            }
        }

        private void addVar(String info)
        {
            vars.add(info);
        }

        private String rename(FmlCleanup namer)
        {
            namer = namer == null ? new FmlCleanup() : new FmlCleanup(namer);

            Map<String, String> renames = Maps.newHashMap();
            Map<String, String> unnamed = Maps.newHashMap();

            for (String var : vars)
            {
                String[] split = var.split(" ");

                if (!split[1].startsWith("var"))
                    renames.put(split[1], namer.getName(split[0], split[1]));
                else
                    unnamed.put(split[1], split[0]);
            }

            if (unnamed.size() > 0)
            {
                // We sort the var## names because FF is non-deterministic and sometimes decompiles the declarations in different orders.
                List<String> sorted = new ArrayList<String>(unnamed.keySet());
                Collections.sort(sorted, new Comparator<String>()
                {
                    @Override
                    public int compare(String o1, String o2)
                    {
                        if (o1.length() < o2.length()) return -1;
                        if (o1.length() > o2.length()) return  1;
                        return o1.compareTo(o2);
                    }
                });
                for (String s : sorted)
                {
                    renames.put(s, namer.getName(unnamed.get(s), s));
                }
            }

            StringBuilder buf = new StringBuilder();
            for (Object line : lines)
            {
                if (line instanceof MethodInfo)
                    buf.append(((MethodInfo)line).rename(namer)).append(Constants.NEWLINE);
                else
                    buf.append((String)line).append(Constants.NEWLINE);
            }

            String body = buf.toString();

            if (renames.size() > 0)
            {
                List<String> sortedKeys = new ArrayList<String>(renames.keySet());
                Collections.sort(sortedKeys, COMPARATOR);

                // closure changes the sort, to sort by the return value of the closure.
                for (String key : sortedKeys)
                {
                    if (VAR.matcher(key).matches())
                    {
                        body = body.replace(key, renames.get(key));
                    }
                }
            }

            return body.substring(0, body.length() - Constants.NEWLINE.length());
        }
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
        last.put("Enum", new Holder(0, true, "oenum"));

        remap = new HashMap<String, String>();
        remap.put("long", "int");
    }

    private FmlCleanup(FmlCleanup parent)
    {
        last = Maps.newHashMap();
        for (Entry<String, Holder> e : parent.last.entrySet())
        {
            Holder v = e.getValue();
            last.put(e.getKey(), new Holder(v.id, v.skip_zero, v.names));
        }

        remap = Maps.newHashMap();
        for (Entry<String, String> e : parent.remap.entrySet())
        {
            remap.put(e.getKey(), e.getValue());
        }
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
        else if (last.containsKey(Constants.lower(findtype)))
        {
            index = Constants.lower(findtype);
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

            String name = Constants.lower(type);
            // Strip single dots that might happen because of inner class references
            name = name.replace(".", "");
            boolean skip_zero = true;

            if (Pattern.compile("\\[").matcher(type).find())
            {
                skip_zero = true;
                name = "a" + name;
                name = name.replace("[]", "").replace("...", "");
            }

            last.put(Constants.lower(type), new Holder(0, skip_zero, name));
            index = Constants.lower(type);
        }

        if (Strings.isNullOrEmpty(index))
        {
            return Constants.lower(type);
        }

        Holder holder = last.get(index);
        int id = holder.id;
        List<String> names = holder.names;

        int ammount = names.size();

        String name;
        if (ammount == 1)
        {
            name = names.get(0) + (id == 0 && holder.skip_zero ? "" : id);
        }
        else
        {
            int num = id / ammount;
            name = names.get(id % ammount) + (id < ammount && holder.skip_zero ? "" : num);
        }

        holder.id++;
        return name;
    }

    private class Holder
    {
        public int id;
        public boolean skip_zero;
        public final List<String> names = Lists.newArrayList();

        public Holder(int t1, boolean skip_zero, String... names)
        {
            this.id = t1;
            this.skip_zero = skip_zero;
            Collections.addAll(this.names, names);
        }

        public Holder(int t1, boolean skip_zero, List<String> names)
        {
            this.id = t1;
            this.skip_zero = skip_zero;
            this.names.addAll(names);
        }
    }
}
