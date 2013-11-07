package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.tasks.DownloadTask;

import org.gradle.api.Project;
import org.gradle.api.tasks.Copy;
import org.gradle.process.ExecSpec;

import argo.jdom.JsonNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class DevBasePlugin extends BasePlugin<DevExtension> implements IDelayedResolver<DevExtension>
{
    @SuppressWarnings("serial")
    protected static String runGit(final Project project, final String... args)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        project.exec(new Closure<ExecSpec>(project, project)
        {
            @Override
            public ExecSpec call()
            {
                ExecSpec exec = (ExecSpec) getDelegate();
                exec.setExecutable("git");
                exec.args((Object[]) args);
                exec.setStandardOutput(out);
                exec.setWorkingDir(project.getProjectDir());
                return exec;
            }
        });

        return out.toString().trim();
    }
    
    @Override
    public void afterEvaluate()
    {
        try
        {
            Copy copyTask = makeTask("extractNatives", Copy.class);
            {
                copyTask.exclude("META-INF", "META-INF/**", "META-INF/*");
                copyTask.into(delayedString(Constants.ECLIPSE_NATIVES).call());
                copyTask.dependsOn("extractWorkspace");
            }

            String devJson = getDevJson();
            if (devJson == null)
            {
                project.getLogger().info("Dev json not set, could not create native downloads tasks");
                return;
            }
            
            JsonNode node = null;
            File jsonFile = delayedFile(devJson).call().getAbsoluteFile(); // ToDo: Support files in zips, for Modder dev workspace.
            node = Constants.PARSER.parse(Files.newReader(jsonFile, Charset.defaultCharset()));

            for (JsonNode lib : node.getArrayNode("libraries"))
            {
                if (lib.isNode("natives") && lib.isNode("extract"))
                {
                    String notation = lib.getStringValue("name");
                    String[] s = notation.split(":");
                    String path = String.format("%s/%s/%s/%s-%s-natives-%s.jar",
                            s[0].replace('.', '/'), s[1], s[2], s[1], s[2], Constants.OPERATING_SYSTEM
                    );

                    
                    DownloadTask task = makeTask("downloadNatives-" + s[1], DownloadTask.class);
                    {
                        task.setOutput(delayedFile("{CACHE_DIR}/" + path));
                        task.setUrl(delayedString("http://repo1.maven.org/maven2/" + path));
                    }

                    copyTask.from(delayedZipTree("{CACHE_DIR}/" + path));
                    copyTask.dependsOn("downloadNatives-" + s[1]);
                }
            }

        }
        catch (Exception e)
        {
            Throwables.propagate(e);
        }
    }
    
    protected Class<DevExtension> getExtensionClass(){ return DevExtension.class; }
    
    @Override
    public String resolve(String pattern, Project project, DevExtension exten)
    {
        pattern = pattern.replace("{MAIN_CLASS}", exten.getMainClass());
        pattern = pattern.replace("{INSTALLER_VERSION}", exten.getInstallerVersion());
        pattern = pattern.replace("{FML_DIR}", exten.getFmlDir());
        pattern = pattern.replace("{MAPPINGS_DIR}", exten.getFmlDir() + "/conf");
        return pattern;
    }
}
