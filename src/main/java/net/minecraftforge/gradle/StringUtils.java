package net.minecraftforge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;

public final class StringUtils
{
    private StringUtils()
    {
    }

    public static String lower(String string)
    {
        return string.toLowerCase(Locale.ENGLISH);
    }

    public static String fromUTF8Stream(InputStream stream) throws IOException
    {
        return new String(ByteStreams.toByteArray(stream), Charsets.UTF_8);
    }

    public static ImmutableList<String> lines(final String text)
    {
        try
        {
            return ImmutableList.copyOf(CharStreams.readLines(new StringReader(text)));
        }
        catch (IOException e)
        {
            // HERP
            return ImmutableList.of();
        }
    }

    //
    //   THE FOLLOWING HAS BEEN STOLEN FROM THE APACHE SHIRO LIBRARY
    //

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * Trims tokens and omits empty tokens.
     * <p>
     * The given delimiters string is supposed to consist of any number of delimiter characters. Each of those characters can be used to separate tokens. A delimiter is always a single character; for multi-character delimiters, consider using <code>delimitedListToStringArray</code>
     * <p>
     * Copied from the Spring Framework while retaining all license, copyright and author information.
     * @param str the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     *            (each of those characters is individually considered as delimiter).
     * @return an array of the tokens
     * @see java.util.StringTokenizer
     * @see java.lang.String#trim()
     */
    public static String[] tokenizeToStringArray(String str, String delimiters)
    {
        return tokenizeToStringArray(str, delimiters, true, true);
    }

    /**
     * Tokenize the given String into a String array via a StringTokenizer.
     * <p>
     * The given delimiters string is supposed to consist of any number of delimiter characters. Each of those characters can be used to separate tokens. A delimiter is always a single character; for multi-character delimiters, consider using <code>delimitedListToStringArray</code>
     * <p>
     * Copied from the Spring Framework while retaining all license, copyright and author information.
     * @param str the String to tokenize
     * @param delimiters the delimiter characters, assembled as String
     *            (each of those characters is individually considered as delimiter)
     * @param trimTokens trim the tokens via String's <code>trim</code>
     * @param ignoreEmptyTokens omit empty tokens from the result array
     *            (only applies to tokens that are empty after trimming; StringTokenizer
     *            will not consider subsequent delimiters as token in the first place).
     * @return an array of the tokens (<code>null</code> if the input String
     *         was <code>null</code>)
     * @see java.util.StringTokenizer
     * @see java.lang.String#trim()
     */
    public static String[] tokenizeToStringArray(
            String str, String delimiters, boolean trimTokens, boolean ignoreEmptyTokens)
    {

        if (str == null)
        {
            return null;
        }
        StringTokenizer st = new StringTokenizer(str, delimiters);
        List<String> tokens = new ArrayList<String>();
        while (st.hasMoreTokens())
        {
            String token = st.nextToken();
            if (trimTokens)
            {
                token = token.trim();
            }
            if (!ignoreEmptyTokens || token.length() > 0)
            {
                tokens.add(token);
            }
        }
        return toStringArray(tokens);
    }

    /**
     * Copy the given Collection into a String array.
     * The Collection must contain String elements only.
     * <p>
     * Copied from the Spring Framework while retaining all license, copyright and author information.
     * @param collection the Collection to copy
     * @return the String array (<code>null</code> if the passed-in
     *         Collection was <code>null</code>)
     */
    public static String[] toStringArray(Collection<String> collection)
    {
        if (collection == null)
        {
            return null;
        }
        return (String[]) collection.toArray(new String[collection.size()]);
    }
}
