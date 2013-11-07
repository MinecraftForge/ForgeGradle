package net.minecraftforge.gradle.user;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public class UserJson
{
    private final File file;
    String             mainClass;
    ArrayList<String>  libs;
    ArrayList<String>  natives;
    private boolean    hasApplied = false;

    public UserJson(File file)
    {
        this.file = file;
        libs = new ArrayList<String>();
        natives = new ArrayList<String>();
    }

    void apply(Project project, String depConfig, String nativeConfig)
    {
        if (hasApplied)
            return;

        try
        {
            JsonRootNode root = Constants.JDOM_PARSER.parse(Files.newReader(file, Charset.defaultCharset()));

            mainClass = root.getStringValue("mainClass");

            for (JsonNode node : root.getArrayNode("libraries"))
            {
                String dep = node.getStringValue("name");

                // its  maven central one
                if (dep.contains("_fixed"))
                {
                    // nope. we dont like fixed things.
                    continue;
                }
                else if (node.isNode("extract"))
                {
                    natives.add(dep);
                }
                else
                {
                    libs.add(dep);
                }
            }
        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }

        // apply the dep info.
        DependencyHandler handler = project.getDependencies();

        for (String dep : libs)
            handler.add(depConfig, dep);

        for (String dep : natives)
            handler.add(nativeConfig, dep);
    }

}
