package net.minecraftforge.gradle.tasks.patcher;

import static net.minecraftforge.gradle.common.Constants.NEWLINE;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;

public class GenDevProjectsTask extends DefaultTask
{
    //@formatter:off
    @Input private String                javaLevel;
    private Object                       workspaceDir;
    private static final String          INDENT       = "    "; // 4 spaces
    @Input private final List<Repo>      repositories = Lists.newArrayList();
    @Input private final List<String>    dependencies = Lists.newArrayList();
    private final Map<String, DevProject> projects     = Maps.newHashMap();
    //@formatter:on

    @TaskAction
    public void executeTask() throws IOException
    {
        File workspace = getWorkspaceDir();
        workspace.mkdirs();

        generateRootBuild(new File(workspace, "build.gradle"));
        generateRootSettings(new File(workspace, "settings.gradle"), projects.keySet());

        URI workspaceUri = workspace.toURI();
        for (DevProject project : projects.values())
        {
            File projectDir = project.getProjectDir(workspace);
            projectDir.mkdirs();
            generateProjectBuild(workspaceUri, new File(projectDir, "build.gradle"), project);
        }
    }

    private void generateRootBuild(File output) throws IOException
    {
        StringBuilder builder = new StringBuilder();

        //@formatter:off
        
        // create util tasks
        lines(builder,
                "",
                "task eclipse",
                "task cleanEclipse",
                "task idea",
                "task cleanIdea",
                ""
        );
        
        
        // start subprojects
        append(builder, "subprojects { ", NEWLINE);
        
        // plugins
        lines(builder, 1,
                "apply plugin: 'java'",
                "apply plugin: 'eclipse'",
                "apply plugin: 'idea'",
                "",
                // set task dependencies
                "rootProject.tasks.eclipse.dependsOn tasks.eclipse",
                "rootProject.tasks.cleanEclipse.dependsOn tasks.cleanEclipse",
                "rootProject.tasks.idea.dependsOn tasks.idea",
                "rootProject.tasks.cleanIdea.dependsOn tasks.cleanIdea",
                ""
        );
        
        
        // repositories
        append(builder, INDENT, "repositories {", NEWLINE);
        append(builder, INDENT, INDENT, "mavenCentral()", NEWLINE);
        for (Repo repo : repositories)
        {
            lines(builder, 2,
                    "maven {",
                    "    name '"+repo.name + "'",
                    "    url '" +repo.url + "'",
                    "}"
            );
        }
        append(builder, INDENT, "}", NEWLINE, NEWLINE);
        
        // dependencies
        append(builder, INDENT, "dependencies {", NEWLINE);
        for (String dep : dependencies)
        {
            append(builder, INDENT, INDENT, dep, NEWLINE);
        }
        append(builder, INDENT, "}", NEWLINE, NEWLINE);
        
        // set java level
        append(builder, INDENT, "compileJava { sourceCompatibility = targetCompatibility = ", javaLevel, " }", NEWLINE);
        
        // ignore gradleStart just in case
        lines(builder, 1,
              "",
              "jar { exclude \'GradleStart*\', \'net/minecraftforge/gradle/**\' }",
              ""
        );
        
        // eclipse hacks.
        // why? because eclipse doesnt like 2 srcDirs with the same name "java" (src/main/java)
        lines(builder, 1,
                "def links = []",
                "def dupes = []",
                "eclipse.project.file.withXml { provider ->",
                "    def node = provider.asNode()",
                "    links = []",
                "    dupes = []",
                "    node.linkedResources.link.each { child ->",
                "        def path = child.location.text()",
                "        if (path in dupes) {",
                "            child.replaceNode {}",
                "        } else {",
                "            dupes.add(path)",
                "            def newName = path.split('/')[-2..-1].join('/')",
                "            links += newName",
                "            child.replaceNode {",
                "                link{",
                "                    name(newName)",
                "                    type('2')",
                "                    location(path)",
                "                }",
                "            }",
                "        }",
                "    }",
                "}",
                "",
                "eclipse.classpath.file.withXml {",
                "    def node = it.asNode()",
                "    node.classpathentry.each { child -> ",
                "        if (child.@kind == 'src' && !child.@path.contains('/')) child.replaceNode {}",
                "        if (child.@path in links) links.remove(child.@path)",
                "    }",
                "    links.each { link -> node.appendNode('classpathentry', [kind:'src', path:link]) }",
                "}",
                "tasks.eclipseClasspath.dependsOn 'eclipseProject' //Make them run in correct order"
        );
        
        // TODO: idea hacks.. if any... maybe add a contentRoot?
        // ideafix.. just in case
        lines(builder, 1,
                "",
                "idea { module { inheritOutputDirs = true } }",
                ""
        );
        
        //@formatter:on

        // end subprojects
        append(builder, "}", NEWLINE);

        Files.write(builder.toString(), output, Constants.CHARSET);
    }

    private static void generateRootSettings(File output, Collection<String> projects) throws IOException
    {
        StringBuilder builder = new StringBuilder();

        builder.append("include '");
        Joiner.on("', '").appendTo(builder, projects);
        builder.append("'");

        Files.write(builder.toString(), output, Constants.CHARSET);
    }

    private static void generateProjectBuild(URI workspace, File output, DevProject project) throws IOException
    {
        StringBuilder builder = new StringBuilder();

        File src = project.getExternalSrcDir();
        File res = project.getExternalResDir();
        File testSrc = project.getExternalTestSrcDir();
        File testRes = project.getExternalTestResDir();

        // @formatter:off
        
        // why use relatvie paths? so the eclipse hack below can work correctly.
        // add extra sourceDirs
        append(builder, "sourceSets {", NEWLINE);
        if (src != null )     append(builder, INDENT, "main.java.srcDir '",      relative(workspace, src),     "'", NEWLINE);
        if (res != null )     append(builder, INDENT, "main.resources.srcDir '", relative(workspace, res),     "'", NEWLINE);
        if (testSrc != null ) append(builder, INDENT, "test.java.srcDir '",      relative(workspace, testSrc), "'", NEWLINE);
        if (testRes != null ) append(builder, INDENT, "test.resources.srcDir '", relative(workspace, testRes), "'", NEWLINE);
        append(builder, "}");

        // @formatter:on

        // write
        Files.write(builder.toString(), output, Constants.CHARSET);
    }

    private static void lines(StringBuilder out, CharSequence... lines)
    {
        for (CharSequence line : lines)
        {
            out.append(line).append(NEWLINE);
        }
    }

    private static void lines(StringBuilder out, int indentLevel, CharSequence... lines)
    {
        String indent = Strings.repeat(INDENT, indentLevel);

        for (CharSequence line : lines)
        {
            out.append(indent).append(line).append(NEWLINE);
        }
    }

    private static void append(StringBuilder out, CharSequence... things)
    {
        for (CharSequence str : things)
        {
            out.append(str);
        }
    }

    private static String relative(URI base, File src)
    {
        String relative = base.relativize(src.toURI()).getPath().replace('\\', '/');
        if (!relative.endsWith("/"))
            relative += "/";
        return relative;
    }

    @SuppressWarnings("serial")
    private static class Repo implements Serializable
    {
        public final String name, url;

        public Repo(String name, String url)
        {
            super();
            this.name = name;
            this.url = url;
        }
    }

    @SuppressWarnings("serial")
    private static class DevProject implements Serializable
    {
        //@formatter:off
        private final transient Project project;
        private final String name;
        private final Object externalSrcDir, externalResDir;
        private final Object externalTestSrcDir, externalTestResDir;
        //@formatter:on

        public DevProject(Project project, String name, Object externalSrcDir, Object externalResDir, Object externalTestSrcDir, Object externalTestResDir)
        {
            super();
            this.project = project;
            this.name = name;
            this.externalSrcDir = externalSrcDir;
            this.externalResDir = externalResDir;
            this.externalTestSrcDir = externalTestSrcDir;
            this.externalTestResDir = externalTestResDir;
        }

        public File getProjectDir(File root)
        {
            return new File(root, name);
        }

        public File getExternalSrcDir()
        {
            return externalSrcDir == null ? null : project.file(externalSrcDir);
        }

        public File getExternalResDir()
        {
            return externalResDir == null ? null : project.file(externalResDir);
        }

        public File getExternalTestSrcDir()
        {
            return externalTestSrcDir == null ? null : project.file(externalTestSrcDir);
        }

        public File getExternalTestResDir()
        {
            return externalTestResDir == null ? null : project.file(externalTestResDir);
        }
    }

    public String getJavaLevel()
    {
        return javaLevel;
    }

    public void setJavaLevel(String javaLevel)
    {
        this.javaLevel = javaLevel;
    }

    public void addCompileDep(String depString)
    {
        dependencies.add("compile '" + depString + "'");
    }

    public void addTestCompileDep(String depString)
    {
        dependencies.add("testCompile '" + depString + "'");
    }

    public void addRepo(String name, String url)
    {
        repositories.add(new Repo(name, url));
    }

    @OutputFiles
    public List<File> getGeneratedFiles()
    {
        List<File> files = new ArrayList<File>(2 + projects.size());
        File workspace = getWorkspaceDir();
        files.add(new File(workspace, "build.gradle"));
        files.add(new File(workspace, "settings.gradle"));

        for (DevProject p : projects.values())
        {
            files.add(new File(p.getProjectDir(workspace) + "/build.gradle"));
        }
        return files;
    }

    public void putProject(String name, Object externalSrcDir, Object externalResDir, Object externalTestSrcDir, Object externalTestResDir)
    {
        projects.put(name, new DevProject(getProject(), name, externalSrcDir, externalResDir, externalTestSrcDir, externalTestResDir));
    }
    
    public void removeProject(String name)
    {
        projects.remove(name);
    }

    public File getWorkspaceDir()
    {
        return getProject().file(workspaceDir);
    }

    public void setWorkspaceDir(Object workspaceDir)
    {
        this.workspaceDir = workspaceDir;
    }
}
