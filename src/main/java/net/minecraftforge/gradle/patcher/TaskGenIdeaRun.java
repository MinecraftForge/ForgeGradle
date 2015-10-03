/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.patcher;

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
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;

class TaskGenIdeaRun extends DefaultTask
{
    //@formatter:off
    @Input           private Object configName;
    @Input           private Object projectName;
    @Input           private Object mainClass;
    @Input           private Object runDir;
    @Input @Optional private Object arguments;
    @OutputFile      private Object outputFile;
    //@formatter:on

    //@formatter:off
    public TaskGenIdeaRun() { super(); }
    //@formatter:on

    @TaskAction
    public void doTask() throws IOException, ParserConfigurationException, TransformerException
    {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();

        // root element
        Document doc = docBuilder.newDocument();
        Element root = addXml(doc, "component", ImmutableMap.of("name", "ProjectRunConfigurationManager"));
        root = addXml(root, "configuration", ImmutableMap.of(
                "default", "false",
                "name", getConfigName(),
                "type", "Application",
                "factoryName", "Application"));

        addXml(root, "module", ImmutableMap.of("name", getProjectName()));
        addXml(root, "option", ImmutableMap.of("name", "MAIN_CLASS_NAME", "value", getMainClass()));
        addXml(root, "option", ImmutableMap.of("name", "WORKING_DIRECTORY", "value", getRunDir()));

        if (!Strings.isNullOrEmpty(getArguments()))
        {
            addXml(root, "option", ImmutableMap.of("name", "PROGRAM_PARAMETERS", "value", getArguments()));
        }

        File outFile = getOutputFile();
        outFile.getParentFile().mkdirs();

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(outFile);

        transformer.transform(source, result);
    }

    public String getProjectName()
    {
        return Constants.resolveString(projectName);
    }

    public void setProjectName(Object projectName)
    {
        this.projectName = projectName;
    }
    
    public String getConfigName()
    {
        return Constants.resolveString(configName);
    }

    public void setConfigName(Object configName)
    {
        this.configName = configName;
    }

    public String getArguments()
    {
        return Constants.resolveString(arguments);
    }

    public void setArguments(Object arguments)
    {
        this.arguments = arguments;
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
