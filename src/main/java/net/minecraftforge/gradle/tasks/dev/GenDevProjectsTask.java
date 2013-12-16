package net.minecraftforge.gradle.tasks.dev;

import argo.jdom.JsonNode;
import argo.saj.InvalidSyntaxException;

import com.google.common.io.Files;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedFile;
import static net.minecraftforge.gradle.common.Constants.NEWLINE;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class GenDevProjectsTask extends DefaultTask
{
    protected DelayedFile targetDir;

    @Input
    protected DelayedFile json;

    private List<DelayedFile> sources = new ArrayList<DelayedFile>();
    private List<DelayedFile> resources = new ArrayList<DelayedFile>();

    private final ArrayList<String> deps = new ArrayList<String>();

    public GenDevProjectsTask()
    {
        this.getOutputs().file(getTargetFile());
    }

    @TaskAction
    public void doTask() throws IOException, InvalidSyntaxException
    {
        parseJson();
        writeFile();
    }

    private void parseJson() throws IOException, InvalidSyntaxException
    {
        JsonNode node = Constants.PARSER.parse(Files.newReader(getJson(), Charset.defaultCharset()));

        for (JsonNode lib : node.getArrayNode("libraries"))
        {
            if (lib.getStringValue("name").contains("fixed") || lib.isNode("natives") || lib.isNode("extract"))
            {
                continue;
            }
            else
            {
                deps.add(lib.getStringValue("name"));
            }
        }
    }

    private void writeFile() throws IOException
    {
        File file = getProject().file(getTargetFile().call());
        file.getParentFile().mkdirs();
        Files.touch(file);

        // prepare file string for writing.
        StringBuilder o = new StringBuilder();
        
        a(o, 
            "apply plugin: 'java' ",
            "apply plugin: 'eclipse'",
            "",
            "sourceCompatibility = '1.6'",
            "targetCompatibility = '1.6'",
            "",
            "repositories",
            "{",
            "    maven",
            "    {",
            "        name 'forge'",
            "        url 'http://files.minecraftforge.net/maven'",
            "    }",
            "    mavenCentral()",
            "    maven",
            "    {",
            "        name 'minecraft'",
            "        url '" + Constants.LIBRARY_URL + "'",
            "    }",
            "}",
            "",
            "dependencies",
            "{"
        );
        
        // read json, output json in gradle freindly format...
        for (String dep : deps)
        {
            o.append("    compile '").append(dep).append('\'').append(NEWLINE);
        }

        a(o, 
            "",
            "    testCompile 'junit:junit:4.5'", 
            "}",
            ""
        );

        URI base = targetDir.call().toURI();

        if (resources.size() > 0 || sources.size() > 0)
        {
            a(o, "sourceSets");
            a(o, "{");
            a(o, "    main");
            a(o, "    {");
            if (sources.size() > 0)
            {
                a(o, "        java");
                a(o, "        {");
                for (DelayedFile src : sources)
                {
                    String relative = base.relativize(src.call().toURI()).getPath();
                    o.append("            srcDir '").append(relative).append('\'').append(NEWLINE);
                }
                a(o, "        }");
            }
            if (resources.size() > 0)
            {
                a(o, "        resources");
                a(o, "        {");
                for (DelayedFile src : resources)
                {
                    String relative = base.relativize(src.call().toURI()).getPath();
                    o.append("            srcDir '").append(relative).append('\'').append(NEWLINE);
                }
                a(o, "        }");
            }
            a(o, "    }");
            a(o, "}");
        }

        Files.write(o.toString(), file, Charset.defaultCharset());
    }

    private void a(StringBuilder out, String... lines)
    {
        for (String line : lines)
        {
            out.append(line).append(NEWLINE);
        }
    }

    private Closure<File> getTargetFile()
    {
        return new Closure<File>(this)
        {
            private static final long serialVersionUID = -6333350974905684295L;

            @Override
            public File call()
            {
                return new File(getTargetDir(), "build.gradle");
            }

            @Override
            public File call(Object obj)
            {
                return new File(getTargetDir(), "build.gradle");
            }
        };
    }

    public File getTargetDir()
    {
        return targetDir.call();
    }

    public void setTargetDir(DelayedFile targetDir)
    {
        this.targetDir = targetDir;
    }

    public GenDevProjectsTask addSource(DelayedFile source)
    {
        sources.add(source);
        return this;
    }

    public GenDevProjectsTask addResource(DelayedFile resource)
    {
        resources.add(resource);
        return this;
    }

    public File getJson()
    {
        return json.call();
    }

    public void setJson(DelayedFile json)
    {
        this.json = json;
    }
}