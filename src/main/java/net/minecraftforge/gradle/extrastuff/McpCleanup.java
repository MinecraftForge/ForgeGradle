package net.minecraftforge.gradle.extrastuff;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.Constants;

public class McpCleanup
{
    public static final Pattern COMMENTS_TRAILING = Pattern.compile("(?m)[ \\t]+$");
    public static final Pattern COMMENTS_NEWLINES = Pattern.compile("(?m)^(?:\\r\\n|\\r|\\n){2,}");

    public static String stripComments(String text)
    {
        StringReader in = new StringReader(text);
        StringWriter out = new StringWriter(text.length());
        boolean inComment = false;
        boolean inString = false;
        char c;
        int ci;
        try
        {
            while ((ci = in.read()) != -1)
            {
                c = (char) ci;
                switch (c)
                    {
                        case '\\':
                            {
                                out.write(c);
                                out.write(in.read());//Skip escaped chars
                                break;
                            }
                        case '\"':
                            {
                                if (!inComment)
                                {
                                    out.write(c);
                                    inString = !inString;
                                }
                                break;
                            }
                        case '\'':
                            {
                                if (!inComment)
                                {
                                    out.write(c);
                                    out.write(in.read());
                                    out.write(in.read());
                                }
                                break;
                            }
                        case '*':
                            {
                                char c2 = (char) in.read();
                                if (inComment && c2 == '/')
                                {
                                    inComment = false;
                                    out.write(' ');//Allows int x = 3; int y = -/**/-x; to work
                                }
                                else
                                {
                                    out.write(c);
                                    out.write(c2);
                                }
                                break;
                            }
                        case '/':
                            {
                                if (!inString)
                                {
                                    char c2 = (char) in.read();
                                    switch (c2)
                                        {
                                            case '/':
                                                char c3 = 0;
                                                while (c3 != '\n' && c3 != '\r')
                                                {
                                                    c3 = (char) in.read();
                                                }
                                                out.write(c3);//write newline
                                                break;
                                            case '*':
                                                inComment = true;
                                                break;
                                            default:
                                                out.write(c);
                                                out.write(c2);
                                                break;
                                        }
                                }
                                else
                                {
                                    out.write(c);
                                }
                                break;
                            }
                        default:
                            {
                                if (!inComment)
                                {
                                    out.write(c);
                                }
                                break;
                            }
                    }
            }
            out.close();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
        
        text = out.toString();

        text = COMMENTS_TRAILING.matcher(text).replaceAll("");
        text = COMMENTS_NEWLINES.matcher(text).replaceAll(Constants.NEWLINE);

        return text;
    }

    //---------------------------------------------
    //  CLEANUP REGEXES.
    //----------------------------------------------

    public static final Pattern CLEANUP_header = Pattern.compile("^\\s+"); // Remove extra whitespace at start of file

    public static final Pattern CLEANUP_footer = Pattern.compile("\\s+$"); // Remove extra whitespace at end of file

    public static final Pattern CLEANUP_trailing = Pattern.compile("(?m)[ \\t]+$"); // Remove TRAILING whitespace

    public static final Pattern CLEANUP_package = Pattern.compile("(?m)^package ([\\w.]+);$"); // find package --- in quots since its a special word

    public static final Pattern CLEANUP_import = Pattern.compile("(?m)^import (?:([\\w.]*?)\\.)?(?:[\\w]+);(?:\\r\\n|\\r|\\n)"); // package and class.

    public static final Pattern CLEANUP_newlines = Pattern.compile("(?m)^\\s*(?:\\r\\n|\\r|\\n){2,}"); // remove repeated blank lines   ?? JDT?

    public static final Pattern CLEANUP_ifstarts = Pattern.compile("(?m)(^(?![\\s{}]*$).+(?:\\r\\n|\\r|\\n))((?:[ \\t]+)if.*)"); // add new line before IF statements

    // close up blanks in code like:
    // {
    //
    //     private
    public static final Pattern CLEANUP_blockstarts = Pattern.compile("(?m)(?<=\\{)\\s+(?=(?:\\r\\n|\\r|\\n)[ \\t]*\\S)");

    // close up blanks in code like:
    //     }
    //
    // }
    public static final Pattern CLEANUP_blockends = Pattern.compile("(?m)(?<=[;}])\\s+(?=(?:\\r\\n|\\r|\\n)\\s*})");

    // Remove GL comments and surrounding whitespace
    public static final Pattern CLEANUP_gl = Pattern.compile("\\s*\\/\\*\\s*GL_[^*]+\\*\\/\\s*");

    // convert unicode character constants back to integers
    public static final Pattern CLEANUP_unicode = Pattern.compile("'\\\\u([0-9a-fA-F]{4})'");

    // strip out Character.valueof
    public static final Pattern CLEANUP_charval = Pattern.compile("Character\\.valueOf\\(('.')\\)");

    // 1.7976...E+308D to Double.MAX_VALUE
    public static final Pattern CLEANUP_maxD = Pattern.compile("1\\.7976[0-9]*[Ee]\\+308[Dd]");

    // 3.1415...D to Math.PI
    public static final Pattern CLEANUP_piD = Pattern.compile("3\\.1415[0-9]*[Dd]");

    // 3.1415...F to (float)Math.PI
    public static final Pattern CLEANUP_piF = Pattern.compile("3\\.1415[0-9]*[Ff]");

    // 6.2831...D to (Math.PI * 2D)
    public static final Pattern CLEANUP_2piD = Pattern.compile("6\\.2831[0-9]*[Dd]");

    // 6.2831...F to ((float)Math.PI * 2F)
    public static final Pattern CLEANUP_2piF = Pattern.compile("6\\.2831[0-9]*[Ff]");

    // 1.5707...D to (Math.PI / 2D)
    public static final Pattern CLEANUP_pi2D = Pattern.compile("1\\.5707[0-9]*[Dd]");

    // 1.5707...F to ((float)Math.PI / 2F)
    public static final Pattern CLEANUP_pi2F = Pattern.compile("1\\.5707[0-9]*[Ff]");

    // 4.7123...D to (Math.PI * 3D / 2D)
    public static final Pattern CLEANUP_3pi2D = Pattern.compile("4\\.7123[0-9]*[Dd]");

    // 4.7123...F to ((float)Math.PI * 3F / 2F)
    public static final Pattern CLEANUP_3pi2F = Pattern.compile("4\\.7123[0-9]*[Ff]");

    // 0.7853...D to (Math.PI / 4D)
    public static final Pattern CLEANUP_pi4D = Pattern.compile("0\\.7853[0-9]*[Dd]");

    // 0.7853...F to ((float)Math.PI / 4F)
    public static final Pattern CLEANUP_pi4F = Pattern.compile("0\\.7853[0-9]*[Ff]");

    // 0.6283...D to (Math.PI / 5D)
    public static final Pattern CLEANUP_pi5D = Pattern.compile("0\\.6283[0-9]*[Dd]");

    // 0.6283...F to ((float)Math.PI / 5F)
    public static final Pattern CLEANUP_pi5F = Pattern.compile("0\\.6283[0-9]*[Ff]");

    // 57.295...D to (180D / Math.PI)
    public static final Pattern CLEANUP_180piD = Pattern.compile("57\\.295[0-9]*[Dd]");

    // 57.295...F to (180F / (float)Math.PI)
    public static final Pattern CLEANUP_180piF = Pattern.compile("57\\.295[0-9]*[Ff]");

    // 0.6981...D to (Math.PI * 2D / 9D)
    public static final Pattern CLEANUP_2pi9D = Pattern.compile("0\\.6981[0-9]*[Dd]");

    // 0.6981...F to ((float)Math.PI * 2F / 9F)
    public static final Pattern CLEANUP_2pi9F = Pattern.compile("0\\.6981[0-9]*[Ff]");

    // 0.3141...D to (Math.PI / 10D)
    public static final Pattern CLEANUP_pi10D = Pattern.compile("0\\.3141[0-9]*[Dd]");

    // 0.3141...F to ((float)Math.PI / 10F)
    public static final Pattern CLEANUP_pi10F = Pattern.compile("0\\.3141[0-9]*[Ff]");

    // 1.2566...D to (Math.PI * 2D / 5D)
    public static final Pattern CLEANUP_2pi5D = Pattern.compile("1\\.2566[0-9]*[Dd]");

    // 1.2566...F to ((float)Math.PI 2F / 5F)
    public static final Pattern CLEANUP_2pi5F = Pattern.compile("1\\.2566[0-9]*[Ff]");

    // 0.21991...D to (Math.PI * 7D / 100D)
    public static final Pattern CLEANUP_7pi100D = Pattern.compile("0\\.21991[0-9]*[Dd]");

    // 0.21991...F to ((float)Math.PI * 7F / 100F)
    public static final Pattern CLEANUP_7pi100F = Pattern.compile("0\\.21991[0-9]*[Ff]");

    // 5.8119...D to (Math.PI * 185D / 100D)
    public static final Pattern CLEANUP_185pi100D = Pattern.compile("5\\.8119[0-9]*[Dd]");

    // 5.8119...F to ((float)Math.PI * 185F / 100F)
    public static final Pattern CLEANUP_185pi100F = Pattern.compile("0\\.8119[0-9]*[Ff]");

    public static String cleanup(String text)
    {
        // simple replacements
        text = CLEANUP_header.matcher(text).replaceAll("");
        text = CLEANUP_footer.matcher(text).replaceAll("");
        text = CLEANUP_trailing.matcher(text).replaceAll("");
        text = CLEANUP_newlines.matcher(text).replaceAll(Constants.NEWLINE);
        text = CLEANUP_ifstarts.matcher(text).replaceAll("$1" + Constants.NEWLINE + "$2");
        text = CLEANUP_blockstarts.matcher(text).replaceAll("");
        text = CLEANUP_blockends.matcher(text).replaceAll("");
        text = CLEANUP_gl.matcher(text).replaceAll("");
        text = CLEANUP_maxD.matcher(text).replaceAll("Double.MAX_VALUE");
    
        // unicode chars
        {
            Matcher matcher = CLEANUP_unicode.matcher(text);
            int val;
            StringBuffer buffer = new StringBuffer(text.length());
    
            while (matcher.find())
            {
                val = Integer.parseInt(matcher.group(1), 16);
                // work around the replace('\u00a7', '$') call in MinecraftServer and a couple of '\u0000'
                if (val > 255)
                {
                    matcher.appendReplacement(buffer, Matcher.quoteReplacement("" + val));
                }
            }
            matcher.appendTail(buffer);
            text = buffer.toString();
        }
    
        // charval.. its stupid.
        text = CLEANUP_charval.matcher(text).replaceAll("$1"); // TESTING NEEDED
    
        //		 pi?   true
        text = CLEANUP_piD.matcher(text).replaceAll("Math.PI");
        text = CLEANUP_piF.matcher(text).replaceAll("(float)Math.PI");
        text = CLEANUP_2piD.matcher(text).replaceAll("(Math.PI * 2D)");
        text = CLEANUP_2piF.matcher(text).replaceAll("((float)Math.PI * 2F)");
        text = CLEANUP_pi2D.matcher(text).replaceAll("(Math.PI / 2D)");
        text = CLEANUP_pi2F.matcher(text).replaceAll("((float)Math.PI / 2F)");
        text = CLEANUP_3pi2D.matcher(text).replaceAll("(Math.PI * 3D / 2D)");
        text = CLEANUP_3pi2F.matcher(text).replaceAll("((float)Math.PI * 3F / 2F)");
        text = CLEANUP_pi4D.matcher(text).replaceAll("(Math.PI / 4D)");
        text = CLEANUP_pi4F.matcher(text).replaceAll("((float)Math.PI / 4F)");
        text = CLEANUP_pi5D.matcher(text).replaceAll("(Math.PI / 5D)");
        text = CLEANUP_pi5F.matcher(text).replaceAll("((float)Math.PI / 5F)");
        text = CLEANUP_180piD.matcher(text).replaceAll("(180D / Math.PI)");
        text = CLEANUP_180piF.matcher(text).replaceAll("(180F / (float)Math.PI)");
        text = CLEANUP_2pi9D.matcher(text).replaceAll("(Math.PI * 2D / 9D)");
        text = CLEANUP_2pi9F.matcher(text).replaceAll("((float)Math.PI * 2F / 9F)");
        text = CLEANUP_pi10D.matcher(text).replaceAll("(Math.PI / 10D)");
        text = CLEANUP_pi10F.matcher(text).replaceAll("((float)Math.PI / 10F)");
        text = CLEANUP_2pi5D.matcher(text).replaceAll("(Math.PI * 2D / 5D)");
        text = CLEANUP_2pi5F.matcher(text).replaceAll("((float)Math.PI * 2F / 5F)");
        text = CLEANUP_7pi100D.matcher(text).replaceAll("(Math.PI * 7D / 100D)");
        text = CLEANUP_7pi100F.matcher(text).replaceAll("((float)Math.PI * 7F / 100F)");
        text = CLEANUP_185pi100D.matcher(text).replaceAll("(Math.PI * 185D / 100D)");
        text = CLEANUP_185pi100F.matcher(text).replaceAll("((float)Math.PI * 185F / 100F)");
    
        return text;
    }

    /**
     * Ensures that no class imports stuff from the package its in.
     *
     * @param text Full file as a string
     * @return Full file as a string with imports fixed.
     */
    public static String fixImports(String text)
    {
        Matcher match = CLEANUP_package.matcher(text);
        if (match.find())
        {
            String pack = match.group(1);

            Matcher match2 = CLEANUP_import.matcher(text);
            while (match2.find())
            {
                if (match2.group(1).equals(pack))
                {
                    text = text.replace(match2.group(), "");
                }
            }
        }

        return text;
    }
}
