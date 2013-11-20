package net.minecraftforge.gradle.dev;

import edu.sc.seis.launch4j.Launch4jPluginExtension;
import groovy.lang.Closure;
import groovy.util.MapEntry;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.delayed.DelayedBase;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.tasks.DownloadTask;
import net.minecraftforge.gradle.tasks.GenSrgTask;
import net.minecraftforge.gradle.tasks.MergeJarsTask;
import net.minecraftforge.gradle.tasks.abstractutil.ExtractTask;
import net.minecraftforge.gradle.tasks.dev.CompressLZMA;
import net.minecraftforge.gradle.tasks.dev.MergeMappingsTask;

import org.apache.shiro.util.AntPathMatcher;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.bundling.Zip;
import org.gradle.process.ExecSpec;

import argo.jdom.JsonNode;

import com.google.common.base.Throwables;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

public abstract class DevBasePlugin extends BasePlugin<DevExtension> implements IDelayedResolver<DevExtension>
{
    private AntPathMatcher antMatcher = new AntPathMatcher();
    protected static final String[] JAVA_FILES = new String[] { "**.java", "*.java", "**/*.java" };
    
    @SuppressWarnings("serial")
    @Override
    public void applyPlugin()
    {
        // apply L4J
        this.applyExternalPlugin("launch4j");
        
        project.getTasks().getByName("uploadArchives").dependsOn("launch4j");
        
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
        
        task1 = makeTask("downloadL4J", DownloadTask.class);
        {
            task1.setOutput(delayedFile(DevConstants.LAUNCH4J));
            task1.setUrl(delayedString(DevConstants.LAUNCH4J_URL));
        }
        
        task = makeTask("extractL4J", ExtractTask.class);
        {
            task.dependsOn("downloadL4J");
            task.from(delayedFile(DevConstants.LAUNCH4J));
            task.into(delayedFile(DevConstants.LAUNCH4J_DIR));
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
        
        Sync task5 = makeTask("copyAssets", Sync.class);
        {
            task5.from(delayedFile(Constants.ASSETS));
            task5.setDestinationDir(new File(DevConstants.ECLIPSE_ASSETS));
            task5.dependsOn("getAssets", "extractWorkspace");
        }
        
        GenSrgTask task6 = makeTask("genSrgs", GenSrgTask.class);
        {
            task6.setInSrg(delayedFile(DevConstants.PACKAGED_SRG));
            task6.setNotchToMcpSrg(delayedFile(DevConstants.NOTCH_2_MCP_SRG));
            task6.setMcpToSrgSrg(delayedFile(DevConstants.MCP_2_SRG_SRG));
            task6.setMcpToNotchSrg(delayedFile(DevConstants.MCP_2_NOTCH_SRG));
            task6.setMethodsCsv(delayedFile(DevConstants.METHODS_CSV));
            task6.setFieldsCsv(delayedFile(DevConstants.FIELDS_CSV));
            task6.dependsOn("fixMappings");
        }
    }
    
    private void configureLaunch4J()
    {
        final File installer = ((Zip) project.getTasks().getByName("packageInstaller")).getArchivePath();
        
        File output = new File(installer.getParentFile(), installer.getName().replace(".jar", "-win.exe"));
        project.getArtifacts().add("archives", output);
       
        Launch4jPluginExtension ext = (Launch4jPluginExtension) project.getExtensions().getByName("launch4j");
        ext.setOutfile(output.getAbsolutePath());
        ext.setJar(installer.getAbsolutePath());
        
        String command = delayedFile(DevConstants.LAUNCH4J_DIR).call().getAbsolutePath();
        command += "/launch4j/launch4jc";
        
        if (Constants.OPERATING_SYSTEM == Constants.OperatingSystem.WINDOWS)
            command += ".exe";
        
        ext.setLaunch4jCmd(command);
        
        Task task = project.getTasks().getByName("generateXmlConfig");
        task.dependsOn("packageInstaller", "extractL4J");
        task.getInputs().file(installer);

        String icon = ext.getIcon();
        if (icon == null || icon.isEmpty())
        {
            icon = delayedFile(DevConstants.LAUNCH4J_DIR + "/launch4j/demo/SimpleApp/l4j/SimpleApp.ico").call().getAbsolutePath();   
        }
        icon = new File(icon).getAbsolutePath();
        ext.setIcon(icon);
        ext.setMainClassName(delayedString("{MAIN_CLASS}").call());
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
        
        configureLaunch4J();
        
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
    protected static String runGit(final Project project, final File workDir, final String... args)
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
                exec.setWorkingDir(workDir);
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

    private boolean shouldSign(String path, List<String> includes, List<String> excludes)
    {
        for (String exclude : excludes)
        {
            if (antMatcher.matches(exclude, path))
            {
                return false;
            }
        }

        for (String include : includes)
        {
            if (antMatcher.matches(include, path))
            {
                return true;
            }
        }

        return includes.size() == 0; //If it gets to here, then it matches nothing. default to true, if no includes were specified
    }

    @SuppressWarnings("unchecked")
    protected void signJar(File archive, String keyName,  String... filters) throws IOException
    {
        if (!project.hasProperty("jarsigner")) return;

        List<String> excludes = new ArrayList<String>();
        List<String> includes = new ArrayList<String>();

        for (String s : filters)
        {
            if (s.startsWith("!")) excludes.add(s.substring(1));
            else includes.add(s);
        }

        Map<String, Map.Entry<byte[], Long>> unsigned = Maps.newHashMap();
        File temp = new File(archive.getAbsoluteFile() + ".tmp");
        File signed = new File(archive.getAbsoluteFile() + ".signed");

        if (temp.exists()) temp.delete();
        if (signed.exists()) signed.delete();

        // Create a temporary jar with only the things we want to sign
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(temp)));
        ZipFile base = new ZipFile(archive);
        for (ZipEntry e: Collections.list(base.entries()))
        {
            if (shouldSign(e.getName(), includes, excludes))
            {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);
                ByteStreams.copy(base.getInputStream(e), out);
            }
            else
            {
                unsigned.put(e.getName(), new MapEntry(ByteStreams.toByteArray(base.getInputStream(e)), e.getTime()));
            }
        }
        base.close();
        out.close();

        // Sign the temporary jar
        Map<String, String> jarsigner = (Map<String, String>)project.property("jarsigner");
        
        Map<String, String> args = Maps.newHashMap();
        args.put("alias", keyName);
        args.put("storepass", jarsigner.get("storepass"));
        args.put("keypass", jarsigner.get("keypass"));
        args.put("keystore", jarsigner.get("keystore"));
        args.put("jar", temp.getAbsolutePath());
        args.put("signedjar", signed.getAbsolutePath());
        project.getAnt().invokeMethod("signjar", args);

        //Kill temp files to make room
        archive.delete();
        temp.delete();

        //Join the signed jar and our unsigned content
        out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(archive)));
        base = new ZipFile(signed);
        for (ZipEntry e: Collections.list(base.entries()))
        {
            if (e.isDirectory())
            {
                out.putNextEntry(e);
            }
            else
            {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);
                ByteStreams.copy(base.getInputStream(e), out);
            }
        }
        base.close();

        for (Map.Entry<String, Map.Entry<byte[], Long>> e : unsigned.entrySet())
        {
            ZipEntry n = new ZipEntry(e.getKey());
            n.setTime(e.getValue().getValue());
            out.putNextEntry(n);
            out.write(e.getValue().getKey());
        }
        out.close();
        signed.delete();
    }
}
