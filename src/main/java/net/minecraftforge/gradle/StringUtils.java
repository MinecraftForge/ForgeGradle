package net.minecraftforge.gradle;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Locale;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.InputSupplier;

public final class StringUtils {
    private StringUtils() {}

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
            return ImmutableList.copyOf(CharStreams.readLines(new InputSupplier<StringReader>() {
                @Override
                public StringReader getInput() throws IOException
                {
                    return new StringReader(text);
                }
            }));
        } catch (IOException e)
        {
            // HERP
            return ImmutableList.of();
        }
    }
}
