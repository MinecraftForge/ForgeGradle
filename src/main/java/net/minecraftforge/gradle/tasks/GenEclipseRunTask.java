package net.minecraftforge.gradle.tasks;

import static net.minecraftforge.gradle.common.Constants.addXml;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.minecraftforge.gradle.common.Constants;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

@ParallelizableTask
public class GenEclipseRunTask extends DefaultTask
{
    //@formatter:off
    @Input           private Object projectName;
    @Input           private Object mainClass;
    @Input           private Object runDir;
    @Input @Optional private Object runArgs;
    @Input @Optional private Object jvmArgs;
    @OutputFile      private Object outputFile;
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException, ParserConfigurationException, TransformerException
    {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root element
        Document doc = docBuilder.newDocument();
        Element root = addXml(doc, "launchConfiguration", ImmutableMap.of("type", "org.eclipse.jdt.launching.localJavaApplication"));

        addXml(root, "stringAttribute", ImmutableMap.of("key", "org.eclipse.jdt.launching.MAIN_TYPE", "value", getMainClass()));
        addXml(root, "stringAttribute", ImmutableMap.of("key", "org.eclipse.jdt.launching.PROJECT_ATTR", "value", getProjectName()));
        addXml(root, "stringAttribute", ImmutableMap.of("key", "org.eclipse.jdt.launching.WORKING_DIRECTORY", "value", getRunDir()));

        if (!Strings.isNullOrEmpty(getArguments()))
        {
            addXml(root, "stringAttribute", ImmutableMap.of("key", "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", "value", getArguments()));
        }
        
        if (!Strings.isNullOrEmpty(getJvmArguments()))
        {
            addXml(root, "stringAttribute", ImmutableMap.of("key", "org.eclipse.jdt.launching.VM_ARGUMENTS", "value", getJvmArguments()));
        }
        
        File outFile = getOutputFile();
        outFile.getParentFile().mkdirs();

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(getOutputFile());

        transformer.transform(source, result);
        
        // make the rundir
        new File(getRunDir()).mkdirs();
    }

    public String getProjectName()
    {
        return Constants.resolveString(projectName);
    }

    public void setProjectName(Object projectName)
    {
        this.projectName = projectName;
    }

    public String getArguments()
    {
        return Constants.resolveString(runArgs);
    }

    public void setArguments(Object arguments)
    {
        this.runArgs = arguments;
    }
    
    public String getJvmArguments()
    {
        return Constants.resolveString(jvmArgs);
    }

    public void setJvmArguments(Object arguments)
    {
        this.jvmArgs = arguments;
    }

    public String getRunDir()
    {
        return Constants.resolveString(runDir);
    }

    public void setRunDir(Object runDir)
    {
        this.runDir = runDir;
    }

    public File getOutputFile()
    {
        return getProject().file(outputFile);
    }

    public void setOutputFile(Object outputFile)
    {
        this.outputFile = outputFile;
    }

    public String getMainClass()
    {
        return Constants.resolveString(mainClass);
    }

    public void setMainClass(Object mainClass)
    {
        this.mainClass = mainClass;
    }
}
