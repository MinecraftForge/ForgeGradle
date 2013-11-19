package edu.sc.seis.launch4j;

import java.io.File;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CreateLaunch4jXMLTask extends DefaultTask
{
    @Input
    Launch4jPluginExtension configuration;

    @OutputFile
    public File getXmlOutFile()
    {
        return ((Launch4jPluginExtension) getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME)).getXmlOutFileForProject(getProject());
    }

    @TaskAction
    public void writeXmlConfig() throws ParserConfigurationException, TransformerException
    {
        if (configuration == null)
            configuration = ((Launch4jPluginExtension) getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME));

        File file = getXmlOutFile();
        file.getParentFile().mkdirs();

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element root = doc.createElement("launch4jConfig");
        doc.appendChild(root);

        Element child;

        makeTextElement(doc, root, "dontWrapJar", "" + configuration.getDontWrapJar());
        makeTextElement(doc, root, "headerType", configuration.getHeaderType());
        makeTextElement(doc, root, "jar", configuration.getJar());
        makeTextElement(doc, root, "outfile", configuration.getOutfile());
        makeTextElement(doc, root, "errTitle", configuration.getErrTitle());
        makeTextElement(doc, root, "cmdLine", configuration.getCmdLine());
        makeTextElement(doc, root, "chdir", configuration.getChdir());
        makeTextElement(doc, root, "priority", configuration.getPriority());
        makeTextElement(doc, root, "downloadUrl", configuration.getDownloadUrl());
        makeTextElement(doc, root, "supportUrl", configuration.getSupportUrl());
        makeTextElement(doc, root, "customProcName", "" + configuration.getCustomProcName());
        makeTextElement(doc, root, "stayAlive", "" + configuration.getStayAlive());
        makeTextElement(doc, root, "manifest", configuration.getManifest());
        makeTextElement(doc, root, "icon", configuration.getIcon());

        child = doc.createElement("classPath");
        {
            root.appendChild(child);

            makeTextElement(doc, child, "icon", "" + configuration.getIcon());
        }

        child = doc.createElement("versionInfo");
        {
            root.appendChild(child);

            makeTextElement(doc, child, "fileVersion", parseDotVersion(configuration.getVersion()));
            makeTextElement(doc, child, "txtFileVersion", "" + configuration.getVersion());
            makeTextElement(doc, child, "fileDescription", getProject().getName());
            makeTextElement(doc, child, "copyright", configuration.getCopyright());
            makeTextElement(doc, child, "productVersion", parseDotVersion(configuration.getVersion()));
            makeTextElement(doc, child, "txtProductVersion", configuration.getVersion());
            makeTextElement(doc, child, "productName", getProject().getName());
            makeTextElement(doc, child, "internalName", getProject().getName());
            makeTextElement(doc, child, "originalFilename", "" + configuration.getOutfile());
        }

        child = doc.createElement("jre");
        {
            root.appendChild(child);

            if (configuration.getBundledJrePath() != null)
                makeTextElement(doc, child, "path", configuration.getBundledJrePath());

            if (configuration.getJreMinVersion() != null)
                makeTextElement(doc, child, "minVersion", configuration.getJreMinVersion());

            if (configuration.getJreMaxVersion() != null)
                makeTextElement(doc, child, "maxVersion", configuration.getJreMaxVersion());

            if (configuration.getOpt().length() != 0)
                makeTextElement(doc, child, "opt", configuration.getOpt());

            if (configuration.getInitialHeapSize() != null)
                makeTextElement(doc, child, "initialHeapSize", "" + configuration.getInitialHeapSize());

            if (configuration.getInitialHeapPercent() != null)
                makeTextElement(doc, child, "initialHeapPercent", "" + configuration.getInitialHeapPercent());

            if (configuration.getMaxHeapSize() != null)
                makeTextElement(doc, child, "maxHeapSize", "" + configuration.getMaxHeapSize());

            if (configuration.getMaxHeapPercent() != null)
                makeTextElement(doc, child, "maxHeapPercent", "" + configuration.getMaxHeapPercent());
        }

        if (configuration.getMessagesStartupError() != null || configuration.getMessagesBundledJreError() != null ||
                configuration.getMessagesJreVersionError() != null || configuration.getMessagesLauncherError() != null)
        {
            child = doc.createElement("messages");
            {
                root.appendChild(child);

                if (configuration.getMessagesStartupError() != null)
                    makeTextElement(doc, child, "startupErr", "" + configuration.getMessagesStartupError());

                if (configuration.getMessagesBundledJreError() != null)
                    makeTextElement(doc, child, "bundledJreErr", "" + configuration.getMessagesBundledJreError());

                if (configuration.getMessagesJreVersionError() != null)
                    makeTextElement(doc, child, "jreVersionErr", "" + configuration.getMessagesJreVersionError());

                if (configuration.getMessagesLauncherError() != null)
                    makeTextElement(doc, child, "launcherErr", "" + configuration.getMessagesLauncherError());
            }
        }

        if (configuration.getMutexName() != null || configuration.getWindowTitle() != null)
        {
            child = doc.createElement("singleInstance");
            {
                root.appendChild(child);

                if (configuration.getMutexName() != null)
                    makeTextElement(doc, child, "mutexName", "" + configuration.getMutexName());

                if (configuration.getWindowTitle() != null)
                    makeTextElement(doc, child, "windowTitle", "" + configuration.getWindowTitle());
            }
        }

        // now for writing it...

        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        transformer.transform(source, result);
    }

    private void makeTextElement(Document doc, Element parent, String name, String val)
    {
        Element node = doc.createElement(name);

        node.appendChild(doc.createTextNode(val));

        if (parent != null)
            parent.appendChild(node);
    }

    private final Pattern VERSION1 = Pattern.compile("\\d+(\\.\\d+){3}");
    private final Pattern VERSION2 = Pattern.compile("\\d+(\\.\\d+){0,2}");

    /**
     * launch4j fileVersion and productVersion are required to be x.y.z.w format, no text like beta or
     * SNAPSHOT. I think this is a windows thing. So we check the version, and if it is only dots and
     * numbers, we use it. If not we use 0.0.0.1
     * @param version
     * @return
     */
    private String parseDotVersion(String version)
    {
        if (VERSION1.matcher(version).matches())
        {
            return version;
        }
        else if (VERSION2.matcher(version).matches())
        {
            String s = version + ".0";
            while (VERSION2.matcher(s).matches())
            {
                s += ".0";
            }
            return s;
        }
        else
        {
            return "0.0.0.1";
        }
    }

}
