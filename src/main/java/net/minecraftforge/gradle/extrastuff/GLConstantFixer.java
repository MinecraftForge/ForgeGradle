package net.minecraftforge.gradle.extrastuff;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.json.GLConstantGroup;
import net.minecraftforge.gradle.json.JsonFactory;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;
import com.google.gson.reflect.TypeToken;

public class GLConstantFixer
{
    private static final String[] PACKAGES = {
            "GL11",
            "GL12",
            "GL13",
            "GL14",
            "GL15",
            "GL20",
            "GL21",
            "ARBMultitexture",
            "ARBOcclusionQuery",
            "ARBVertexBufferObject",
            "ARBShaderObjects"
    };

    private final List<GLConstantGroup> json;
    public static final Pattern CALL_REGEX = Pattern.compile("(" + Joiner.on("|").join(PACKAGES) + ")\\.([\\w]+)\\(.+\\)");
    public static final Pattern CONSTANT_REGEX = Pattern.compile("(?<![-.\\w])\\d+(?![.\\w])");
    private static final String ADD_AFTER = "org.lwjgl.opengl.GL11";
    private static final String CHECK = "org.lwjgl.opengl.";
    private static final String IMPORT_CHECK = "import " + CHECK;
    private static final String IMPORT_REPLACE = "import " + ADD_AFTER + ";";

    public GLConstantFixer() throws IOException
    {
        String text = Resources.toString(Resources.getResource(GLConstantFixer.class, "gl.json"), Charset.defaultCharset());
        json = JsonFactory.GSON.fromJson(text, new TypeToken<List<GLConstantGroup>>(){}.getType());
    }

    public String fixOGL(String text)
    {
        // if it never uses openGL, ignore it.
        if (!text.contains(IMPORT_CHECK))
        {
            return text;
        }

        text = annotateConstants(text);

        for (String pack : PACKAGES)
        {
            if (text.contains(pack + "."))
            {
                text = updateImports(text, CHECK + pack);
            }
        }

        return text;
    }

    private String annotateConstants(String text)
    {
        Matcher rootMatch = CALL_REGEX.matcher(text);
        String pack, method, fullCall;
        StringBuffer out = new StringBuffer(text.length());
        StringBuffer innerOut;

        // search with regex.
        while (rootMatch.find())
        {
            // helper variables
            fullCall = rootMatch.group();
            pack = rootMatch.group(1);
            method = rootMatch.group(2);

            Matcher constantMatcher = CONSTANT_REGEX.matcher(fullCall);
            innerOut = new StringBuffer(fullCall.length());

            // search for hardcoded numbers
            while (constantMatcher.find())
            {
                // helper variables and return variable.
                String constant = constantMatcher.group();
                String answer = null;

                // iterrate over the JSON
                for (GLConstantGroup group : json)
                {

                    // ensure that the package and method are defined
                    if (group.functions.containsKey(pack) && group.functions.get(pack).contains(method))
                    {
                        // itterrate through the map.
                        for (Map.Entry<String, Map<String, String>> entry : group.constants.entrySet())
                        {
                            // find the actual constant for the number from the regex
                            if (entry.getValue().containsKey(constant))
                            {
                                // construct the final line
                                answer = entry.getKey() + "." + entry.getValue().get(constant);
                            }
                        }
                    }

                }

                // replace the final line.
                if (answer != null)
                {
                    constantMatcher.appendReplacement(innerOut, Matcher.quoteReplacement(answer));
                }
            }
            constantMatcher.appendTail(innerOut);

            // replace the final line.
            if (fullCall != null)
            {
                rootMatch.appendReplacement(out, Matcher.quoteReplacement(innerOut.toString()));
            }
        }
        rootMatch.appendTail(out);

        return out.toString();
    }

    private String updateImports(String text, String imp)
    {
        if (!text.contains("import " + imp + ";"))
        {
            text = text.replace(IMPORT_REPLACE, IMPORT_REPLACE + Constants.NEWLINE + "import " + imp + ";");
        }

        return text;
    }

}
