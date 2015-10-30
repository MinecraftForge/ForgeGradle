package edu.sc.seis.launch4j;

import java.io.File;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class CreateLaunch4jXMLTask extends DefaultTask
{
    @OutputFile
    public File getXmlOutFile()
    {
        return ((Launch4jPluginExtension) getProject().getExtensions().getByName(Launch4jPlugin.LAUNCH4J_CONFIGURATION_NAME)).getXmlOutFileForProject(getProject());
    }

    @TaskAction
    public void writeXmlConfig() throws ParserConfigurationException, TransformerException
    {
        Launch4jPluginExtension cfg = (Launch4jPluginExtension) getProject().getExtensions().getByName("launch4j");

        File file = getXmlOutFile();
        file.getParentFile().mkdirs();

        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.newDocument();

        Element root = doc.createElement("launch4jConfig");
        doc.appendChild(root);

        Element child;

        textElement(doc, root, "dontWrapJar",    cfg.getDontWrapJar());
        textElement(doc, root, "headerType",     cfg.getHeaderType());
        textElement(doc, root, "jar",            new File(cfg.getJar()).getAbsolutePath());
        textElement(doc, root, "outfile",        new File(cfg.getOutfile()).getAbsolutePath());
        textElement(doc, root, "errTitle",       cfg.getErrTitle());
        textElement(doc, root, "cmdLine",        cfg.getCmdLine());
        textElement(doc, root, "chdir",          cfg.getChdir());
        textElement(doc, root, "priority",       cfg.getPriority());
        textElement(doc, root, "downloadUrl",    cfg.getDownloadUrl());
        textElement(doc, root, "supportUrl",     cfg.getSupportUrl());
        textElement(doc, root, "customProcName", cfg.getCustomProcName());
        textElement(doc, root, "stayAlive",      cfg.getStayAlive());
        textElement(doc, root, "manifest",       cfg.getManifest());
        textElement(doc, root, "icon",           cfg.getIcon());

        /*
        child = doc.createElement("classPath");
        {
            root.appendChild(child);
            textElement(doc, child, "icon", cfg.getIcon());
        }
        */

        child = doc.createElement("versionInfo");
        {
            String originalFilename = ensureLength(46, new File(cfg.getOutfile()).getName());
            if (!originalFilename.endsWith(".exe")) originalFilename += ".exe";

            textElement(doc, child, "fileVersion",       ensureLength(20,  parseDotVersion(cfg.getVersion())));    //Max 20
            textElement(doc, child, "txtFileVersion",    ensureLength(50,  cfg.getVersion()));                     //Max 50
            textElement(doc, child, "fileDescription",   ensureLength(150, getProject().getName()));               //Max 150
            textElement(doc, child, "copyright",         ensureLength(20,  cfg.getCopyright()));                   //Max 150
            textElement(doc, child, "productVersion",    ensureLength(20,  parseDotVersion(cfg.getVersion())));    //Max 20
            textElement(doc, child, "txtProductVersion", ensureLength(50,  cfg.getVersion()));                     //Max 50
            textElement(doc, child, "productName",       ensureLength(150, getProject().getName()));               //Max 150
            textElement(doc, child, "internalName",      ensureLength(150, getProject().getName()));               //Max 50, Must NOT end in .exe
            textElement(doc, child, "originalFilename",  originalFilename);                                         //Max 50, Must end in .exe
        }

        child = doc.createElement("jre");
        {
            root.appendChild(child);
            textElement(doc, child, "path",               cfg.getBundledJrePath());
            textElement(doc, child, "minVersion",         cfg.getJreMinVersion());
            textElement(doc, child, "maxVersion",         cfg.getJreMaxVersion());
            textElement(doc, child, "opt",                cfg.getOpt());
            textElement(doc, child, "initialHeapSize",    cfg.getInitialHeapSize());
            textElement(doc, child, "initialHeapPercent", cfg.getInitialHeapPercent());
            textElement(doc, child, "maxHeapSize",        cfg.getMaxHeapSize());
            textElement(doc, child, "maxHeapPercent",     cfg.getMaxHeapPercent());
        }

        if (cfg.getMessagesStartupError() != null ||
            cfg.getMessagesBundledJreError() != null ||
            cfg.getMessagesJreVersionError() != null ||
            cfg.getMessagesLauncherError() != null)
        {
            child = doc.createElement("messages");
            {
                root.appendChild(child);
                textElement(doc, child, "startupErr",    cfg.getMessagesStartupError());
                textElement(doc, child, "bundledJreErr", cfg.getMessagesBundledJreError());
                textElement(doc, child, "jreVersionErr", cfg.getMessagesJreVersionError());
                textElement(doc, child, "launcherErr",   cfg.getMessagesLauncherError());
            }
        }

        if (cfg.getMutexName() != null || cfg.getWindowTitle() != null)
        {
            child = doc.createElement("singleInstance");
            {
                root.appendChild(child);
                textElement(doc, child, "mutexName", cfg.getMutexName());
                textElement(doc, child, "windowTitle", cfg.getWindowTitle());
            }
        }

        // now for writing it...

        // write the content into xml file
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.METHOD, "xml");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(file);
        //StreamResult result = new StreamResult(System.out);

        transformer.transform(source, result);
    }
    private String ensureLength(int maxLen, String value)
    {
        if (value.length() > maxLen) return value.substring(0, maxLen - 1);
        return value;
    }

    private void textElement(Document doc, Element parent, String name, Integer val)
    {
        if (val == null) return;
        textElement(doc, parent, name, val.toString());
    }
    private void textElement(Document doc, Element parent, String name, boolean val)
    {
        textElement(doc, parent, name, val ? "true" : "false");
    }
    private void textElement(Document doc, Element parent, String name, String val)
    {
        if (val == null || name == null || val.isEmpty())
            return;

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
