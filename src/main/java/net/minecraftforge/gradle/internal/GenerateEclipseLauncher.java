package net.minecraftforge.gradle.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.inject.Inject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.util.Map;

// This is mostly taken from ForgeGradle 6 but slimmed down to what we need
abstract class GenerateEclipseLauncher extends DefaultTask implements ForgeGradleTask {
    protected abstract @OutputFile RegularFileProperty getOutputFile();
    protected abstract @Input Property<String> getProjectName();
    protected abstract @Input @Optional Property<String> getEclipseProjectName();
    protected abstract @Input @Optional ListProperty<String> getArgs();
    protected abstract @Input @Optional ListProperty<String> getJvmArgs();
    protected abstract @Internal DirectoryProperty getWorkingDir();
    protected abstract @Input @Optional MapProperty<String, String> getEnvironment();

    protected abstract @Inject WorkerExecutor getWorkerExecutor();

    @Inject
    public GenerateEclipseLauncher() {

    }

    static abstract class Action implements WorkAction<Action.Parameters> {
        interface Parameters extends WorkParameters {
            RegularFileProperty getOutputFile();

            Property<String> getEclipseProjectName();

            Property<String> getMainClass();

            ListProperty<String> getArgs();

            ListProperty<String> getJvmArgs();

            DirectoryProperty getWorkingDir();

            MapProperty<String, String> getEnvironment();
        }

        @Override
        public void execute() {
            var parameters = getParameters();

            DocumentBuilder documentBuilder;
            Transformer transformer;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                transformer = TransformerFactory.newInstance().newTransformer();
            } catch (ParserConfigurationException | TransformerConfigurationException e) {
                throw new RuntimeException(e);
            }

            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            var launch = documentBuilder.newDocument();
            var rootElement = launch.createElement("launchConfiguration");

            rootElement.setAttribute("type", "org.eclipse.jdt.launching.localJavaApplication");
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROJECT_ATTR", parameters.getEclipseProjectName().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.MAIN_TYPE", parameters.getMainClass().get());
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.VM_ARGUMENTS", String.join(" ", parameters.getJvmArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.PROGRAM_ARGUMENTS", String.join(" ", parameters.getArgs().get()));
            stringAttribute(launch, rootElement, "org.eclipse.jdt.launching.WORKING_DIRECTORY", parameters.getWorkingDir().getAsFile().get().getAbsolutePath());
            mapAttribute(launch, rootElement, "org.eclipse.debug.core.environmentVariables", parameters.getEnvironment().get());

            var source = new DOMSource(launch);
            var result = new StreamResult(parameters.getOutputFile().getAsFile().get());

            try {
                transformer.transform(source, result);
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }

        private static void stringAttribute(Document document, Element parent, String key, String value) {
            var attribute = document.createElement("stringAttribute");

            attribute.setAttribute("key", key);
            attribute.setAttribute("value", value);
            parent.appendChild(attribute);
        }

        private static void mapAttribute(Document document, Element parent, String key, Map<String, String> map) {
            var attribute = document.createElement("mapAttribute");
            attribute.setAttribute("key", key);

            for (var entry : map.entrySet()) {
                var k = entry.getKey();
                var v = entry.getValue();

                var mapEntry = document.createElement("mapEntry");
                mapEntry.setAttribute("key", k);
                mapEntry.setAttribute("value", v);
                attribute.appendChild(mapEntry);
            }
            parent.appendChild(attribute);
        }
    }
}
