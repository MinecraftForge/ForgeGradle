package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.dev.CompressLZMA;
import net.minecraftforge.gradle.tasks.dev.MergeMappingsTask;

import org.gradle.api.Project;
import org.gradle.api.tasks.Copy;
import org.gradle.process.ExecSpec;

import argo.jdom.JsonNode;

import com.google.common.base.Throwables;
import com.google.common.io.Files;

public abstract class DevBasePlugin extends BasePlugin<DevExtension> implements IDelayedResolver<DevExtension>
{
    protected static final String[] JAVA_FILES = new String[] { "**.java", "*.java", "**/*.java" };
    
    @SuppressWarnings("serial")
    @Override
    public void applyPlugin()
    {
        ExtractTask task = makeTask("extractWorkspace", ExtractTask.class);
        {
            task.getOutputs().upToDateWhen(new Closure<Boolean>(null) {
                public Boolean call(Object... obj)
                {
                    File file = new File(project.getProjectDir(), "eclipse");
                    return (file.exists() && file.isDirectory());
                }
            });
            task.from(delayedFile(DevConstants.WORKSPACE_ZIP));
            task.into(delayedFile(DevConstants.WORKSPACE));
        }
        
        DownloadTask task1 = makeTask("downloadBaseInstaller", DownloadTask.class);
        {
            task1.setOutput(delayedFile(DevConstants.INSTALLER_BASE));
            task1.setUrl(delayedString(DevConstants.INSTALLER_URL));
        }
        
        MergeMappingsTask task2 = makeTask("fixMappings", MergeMappingsTask.class);
        {
            task2.setPackageCSV(delayedFile(DevConstants.PACK_CSV));
            task2.setInSRG(delayedFile(DevConstants.JOINED_SRG));
            task2.setInEXC(delayedFile(DevConstants.JOINED_EXC));
            task2.setOutSRG(delayedFile(DevConstants.PACKAGED_SRG));
            task2.setOutEXC(delayedFile(DevConstants.PACKAGED_EXC));
            task2.setInPatch(delayedFile(DevConstants.MCP_PATCH));
            task2.setOutPatch(delayedFile(DevConstants.PACKAGED_PATCH));
        }
        
        CompressLZMA task3 = makeTask("compressDeobfData", CompressLZMA.class);
        {
            task3.setInputFile(delayedFile(DevConstants.PACKAGED_SRG));
            task3.setOutputFile(delayedFile(DevConstants.DEOBF_DATA));
            task3.dependsOn("fixMappings");
        }
        
        MergeJarsTask task4 = makeTask("mergeJars", MergeJarsTask.class);
        {
            task4.setClient(delayedFile(Constants.JAR_CLIENT_FRESH));
            task4.setServer(delayedFile(Constants.JAR_SERVER_FRESH));
            task4.setOutJar(delayedFile(Constants.JAR_MERGED));
            task4.setMergeCfg(delayedFile(DevConstants.MERGE_CFG));
            task4.dependsOn("downloadClient", "downloadServer");
        }
    }
    
    @Override
    protected final String getDevJson()
    {
        return DelayedBase.resolve(DevConstants.JSON_DEV, project, this);
    }

    @Override
    public void afterEvaluate()
    {
        super.afterEvaluate();
        try
        {
            Copy copyTask = makeTask("extractNatives", Copy.class);
            {
                copyTask.exclude("META-INF", "META-INF/**", "META-INF/*");
                copyTask.into(delayedString(DevConstants.ECLIPSE_NATIVES).call());
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

    protected Class<DevExtension> getExtensionClass()
    {
        return DevExtension.class;
    }

    protected String getServerClassPath(File json)
    {
        try
        {
            JsonNode node = Constants.PARSER.parse(Files.newReader(json, Charset.defaultCharset()));

            StringBuilder buf = new StringBuilder();

            for (JsonNode lib : node.getArrayNode("versionInfo", "libraries"))
            {
                if (lib.isNode("serverreq") && lib.getBooleanValue("serverreq"))
                {
                    String[] pts = lib.getStringValue("name").split(":");
                    buf.append(String.format("libraries/%s/%s/%s/%s-%s.jar ", pts[0], pts[1], pts[2], pts[1], pts[2]));
                }
            }
            buf.append(delayedString("minecraft_server.{MC_VERSION}").call());
            return buf.toString();
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public String resolve(String pattern, Project project, DevExtension exten)
    {
        pattern = pattern.replace("{MAIN_CLASS}", exten.getMainClass());
        pattern = pattern.replace("{INSTALLER_VERSION}", exten.getInstallerVersion());
        pattern = pattern.replace("{FML_DIR}", exten.getFmlDir());
        pattern = pattern.replace("{MAPPINGS_DIR}", exten.getFmlDir() + "/conf");
        return pattern;
    }
    
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
    

    protected DelayedString delayedString(String path)
    {
        return new DelayedString(project, path, this);
    }

    protected DelayedFile delayedFile(String path)
    {
        return new DelayedFile(project, path, this);
    }

    protected DelayedFileTree delayedFileTree(String path)
    {
        return new DelayedFileTree(project, path, this);
    }

    protected DelayedFileTree delayedZipTree(String path)
    {
        return new DelayedFileTree(project, path, true, this);
    }
}
