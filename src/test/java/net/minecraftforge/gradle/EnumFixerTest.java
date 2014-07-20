package net.minecraftforge.gradle;

import java.io.IOException;
import java.io.InputStream;

import net.minecraftforge.gradle.extrastuff.FFPatcher;

import org.junit.Assert;
import org.junit.Test;

import com.google.common.io.ByteStreams;

public class EnumFixerTest
{
    private static final String INPUT    = "TestClass";
    private static final String EXPECTED = "TestClassOut";

    @Test
    public void test() throws IOException
    {
        String input = readResource(INPUT);
        //String expected = readResource(EXPECTED);

        input = FFPatcher.processFile(INPUT + ".java", input, true);

        // check LineByLine...
        String[] expected = readResource(EXPECTED).split("\r\n|\r|\n");
        String[] actual = input.split("\r\n|\r|\n");

        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++)
        {
            System.out.println("EXPECTED >>"+expected[i]);
            System.out.println("ACTUAL   >>"+actual[i]);
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    private String readResource(String name) throws IOException
    {
        InputStream stream = this.getClass().getClassLoader().getResourceAsStream(name);
        return new String(ByteStreams.toByteArray(stream));
    }

}
