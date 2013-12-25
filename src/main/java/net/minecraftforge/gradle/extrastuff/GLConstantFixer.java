package net.minecraftforge.gradle.extrastuff;

import argo.jdom.JdomParser;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;
import argo.jdom.JsonStringNode;
import argo.saj.InvalidSyntaxException;

import com.google.common.base.Joiner;
import com.google.common.io.Resources;

import net.minecraftforge.gradle.common.Constants;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final JdomParser JDOM_PARSER = new JdomParser();
    private final JsonRootNode json;
    public static final Pattern CALL_REGEX = Pattern.compile("(" + Joiner.on("|").join(PACKAGES) + ")\\.([\\w]+)\\(.+\\)");
    public static final Pattern CONSTANT_REGEX = Pattern.compile("(?<![-.\\w])\\d+(?![.\\w])");
    private static final String ADD_AFTER = "org.lwjgl.opengl.GL11";
    private static final String CHECK = "org.lwjgl.opengl.";
    private static final String IMPORT_CHECK = "import " + CHECK;
    private static final String IMPORT_REPLACE = "import " + ADD_AFTER + ";";

    public GLConstantFixer() throws IOException, InvalidSyntaxException
    {
        String text = Resources.toString(Resources.getResource("gl.json"), Charset.defaultCharset());
        json = JDOM_PARSER.parse(text);
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
        JsonNode listNode;
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
                for (JsonNode group : json.getElements())
                {
                    // the list part object
                    listNode = group.getElements().get(0);

                    // ensure that the package and method are defined
                    if (listNode.isNode(pack) && jsonArrayContains(listNode.getArrayNode(pack), method))
                    {
                        // now the map part object
                        listNode = group.getElements().get(1);

                        // itterrate through the map.
                        for (Map.Entry<JsonStringNode, JsonNode> entry : listNode.getFields().entrySet())
                        {
                            // find the actual constant for the number from the regex
                            if (entry.getValue().isNode(constant))
                            {
                                // construct the final line
                                answer = entry.getKey().getText() + "." + entry.getValue().getStringValue(constant);
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

    private boolean jsonArrayContains(List<JsonNode> nodes, String str)
    {
        boolean hasMethod = false;
        for (JsonNode testMethod : nodes)
        {
            hasMethod = testMethod.getText().equals(str);
            if (hasMethod)
            {
                return hasMethod;
            }
        }

        return false;
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
