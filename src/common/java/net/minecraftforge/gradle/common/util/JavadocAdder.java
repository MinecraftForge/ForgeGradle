/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.common.util;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

public final class JavadocAdder
{
    public static final String NEWLINE = System.getProperty("line.separator");
    private JavadocAdder() { /* no constructing */ }

    /**
     * Converts a raw javadoc string into a nicely formatted, indented, and wrapped string.
     * @param indent the indent to be inserted before every line.
     * @param javadoc The javadoc string to be processed
     * @param multiline If this javadoc is mutlilined (for a field, it isn't) even if there is only one line in the doc
     * @return A fully formatted javadoc comment string complete with comment characters and newlines.
     */
    public static String buildJavadoc(String indent, String javadoc, boolean multiline)
    {
        StringBuilder builder = new StringBuilder();

        // split and wrap.
        List<String> list = Lists.newLinkedList();
        for (String line : Splitter.on("\\n").splitToList(javadoc))
        {
            list.addAll(wrapText(line, 120 - (indent.length() + 3)));
        }

        if (list.size() > 1 || multiline)
        {
            builder.append(indent);
            builder.append("/**");
            builder.append(NEWLINE);

            for (String line : list)
            {
                builder.append(indent);
                builder.append(" * ");
                builder.append(line);
                builder.append(NEWLINE);
            }

            builder.append(indent);
            builder.append(" */");
            //builder.append(Constants.NEWLINE);

        }
        // one line
        else
        {
            builder.append(indent);
            builder.append("/** ");
            builder.append(javadoc);
            builder.append(" */");
            //builder.append(Constants.NEWLINE);
        }

        return builder.toString().replace(indent, indent);
    }

    private static List<String> wrapText(@Nullable String text, int len)
    {
        // return empty array for null text
        if (text == null)
        {
            return Lists.newArrayList();
        }

        // return text if len is zero or less OR text length is less than len
        if (len <= 0 || text.length() <= len)
        {
            return Lists.newArrayList(text);
        }

        List<String> lines = Lists.newLinkedList();
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

        return lines.stream().map(String::trim).collect(Collectors.toList());
    }
}
