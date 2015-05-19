
package edu.sc.seis.launch4j;

import java.io.File;
import java.io.Serializable;
import java.util.regex.Pattern;

import org.gradle.api.Project;
import org.gradle.api.plugins.JavaPluginConvention;


public class Launch4jPluginExtension implements Serializable
{
    private static final long serialVersionUID = 1001523559902066994L;
    
    private String  outputDir      = "launch4j";
    private String  xmlFileName    = "launch4j.xml";
    private String  mainClassName;
    private boolean dontWrapJar    = false;
    private String  headerType     = "gui";
    private String  jar;
    private String  outfile;
    private String  errTitle       = "";
    private String  cmdLine        = "";
    private String  chdir          = ".";
    private String  priority       = "normal";
    private String  downloadUrl    = "";
    private String  supportUrl     = "";
    private boolean customProcName = false;
    private boolean stayAlive      = false;
    private String  manifest       = "";
    private String  icon           = "";
    private String  version        = "";
    private String  copyright      = "unknown";
    private String  opt            = "";
	
	private String bundledJrePath;
	private String jreMinVersion = "1.6.0";
	private String jreMaxVersion;
	
	private String mutexName;
	private String windowTitle;
	
	private String messagesStartupError;
	private String messagesBundledJreError;
	private String messagesJreVersionError;
    private String messagesLauncherError;
	
	private int initialHeapSize;
	private int initialHeapPercent;
	private int maxHeapSize;
	private int maxHeapPercent;
	
	private static final Pattern JAVA_VERSION_REGEX = Pattern.compile("\\d+(\\.\\d+){0,1}");
	
    public File getXmlOutFileForProject(Project project)
    {
        return project.file(project.getBuildDir() + "/" + outputDir + "/" + xmlFileName);
    }

    void initExtensionDefaults(Project project)
    {
        outfile = project.getName()+".exe";
        version = (String)project.getVersion();
        
        JavaPluginConvention javaConv = (JavaPluginConvention)project.getConvention().getPlugins().get("java");
        if (javaConv != null)
        {
            jreMinVersion = javaConv.getTargetCompatibility().toString();
            if (JAVA_VERSION_REGEX.matcher(jreMinVersion).matches())
            {
                jreMinVersion = jreMinVersion + ".0";
            }
        }
    }

    public String getOutputDir()
    {
        return outputDir;
    }

    public void setOutputDir(String outputDir)
    {
        this.outputDir = outputDir;
    }

    public String getXmlFileName()
    {
        return xmlFileName;
    }

    public void setXmlFileName(String xmlFileName)
    {
        this.xmlFileName = xmlFileName;
    }

    public String getMainClassName()
    {
        return mainClassName;
    }

    public void setMainClassName(String mainClassName)
    {
        this.mainClassName = mainClassName;
    }

    public boolean getDontWrapJar()
    {
        return dontWrapJar;
    }

    public void setDontWrapJar(boolean dontWrapJar)
    {
        this.dontWrapJar = dontWrapJar;
    }

    public String getHeaderType()
    {
        return headerType;
    }

    public void setHeaderType(String headerType)
    {
        this.headerType = headerType;
    }

    public String getJar()
    {
        return jar;
    }

    public void setJar(String jar)
    {
        this.jar = jar;
    }

    public String getOutfile()
    {
        return outfile;
    }

    public void setOutfile(String outfile)
    {
        this.outfile = outfile;
    }

    public String getErrTitle()
    {
        return errTitle;
    }

    public void setErrTitle(String errTitle)
    {
        this.errTitle = errTitle;
    }

    public String getCmdLine()
    {
        return cmdLine;
    }

    public void setCmdLine(String cmdLine)
    {
        this.cmdLine = cmdLine;
    }

    public String getChdir()
    {
        return chdir;
    }

    public void setChdir(String chdir)
    {
        this.chdir = chdir;
    }

    public String getPriority()
    {
        return priority;
    }

    public void setPriority(String priority)
    {
        this.priority = priority;
    }

    public String getDownloadUrl()
    {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl)
    {
        this.downloadUrl = downloadUrl;
    }

    public String getSupportUrl()
    {
        return supportUrl;
    }

    public void setSupportUrl(String supportUrl)
    {
        this.supportUrl = supportUrl;
    }

    public boolean getCustomProcName()
    {
        return customProcName;
    }

    public void setCustomProcName(boolean customProcName)
    {
        this.customProcName = customProcName;
    }

    public boolean getStayAlive()
    {
        return stayAlive;
    }

    public void setStayAlive(boolean stayAlive)
    {
        this.stayAlive = stayAlive;
    }

    public String getManifest()
    {
        return manifest;
    }

    public void setManifest(String manifest)
    {
        this.manifest = manifest;
    }

    public String getIcon()
    {
        return icon;
    }

    public void setIcon(String icon)
    {
        this.icon = icon;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public String getCopyright()
    {
        return copyright;
    }

    public void setCopyright(String copyright)
    {
        this.copyright = copyright;
    }

    public String getOpt()
    {
        return opt;
    }

    public void setOpt(String opt)
    {
        this.opt = opt;
    }

    public String getBundledJrePath()
    {
        return bundledJrePath;
    }

    public void setBundledJrePath(String bundledJrePath)
    {
        this.bundledJrePath = bundledJrePath;
    }

    public String getJreMinVersion()
    {
        return jreMinVersion;
    }

    public void setJreMinVersion(String jreMinVersion)
    {
        this.jreMinVersion = jreMinVersion;
    }

    public String getJreMaxVersion()
    {
        return jreMaxVersion;
    }

    public void setJreMaxVersion(String jreMaxVersion)
    {
        this.jreMaxVersion = jreMaxVersion;
    }

    public String getMutexName()
    {
        return mutexName;
    }

    public void setMutexName(String mutexName)
    {
        this.mutexName = mutexName;
    }

    public String getWindowTitle()
    {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle)
    {
        this.windowTitle = windowTitle;
    }

    public String getMessagesStartupError()
    {
        return messagesStartupError;
    }

    public void setMessagesStartupError(String messagesStartupError)
    {
        this.messagesStartupError = messagesStartupError;
    }

    public String getMessagesBundledJreError()
    {
        return messagesBundledJreError;
    }

    public void setMessagesBundledJreError(String messagesBundledJreError)
    {
        this.messagesBundledJreError = messagesBundledJreError;
    }

    public String getMessagesJreVersionError()
    {
        return messagesJreVersionError;
    }

    public void setMessagesJreVersionError(String messagesJreVersionError)
    {
        this.messagesJreVersionError = messagesJreVersionError;
    }

    public String getMessagesLauncherError()
    {
        return messagesLauncherError;
    }

    public void setMessagesLauncherError(String messagesLauncherError)
    {
        this.messagesLauncherError = messagesLauncherError;
    }

    public int getInitialHeapSize()
    {
        return initialHeapSize;
    }

    public void setInitialHeapSize(int initialHeapSize)
    {
        this.initialHeapSize = initialHeapSize;
    }

    public int getInitialHeapPercent()
    {
        return initialHeapPercent;
    }

    public void setInitialHeapPercent(int initialHeapPercent)
    {
        this.initialHeapPercent = initialHeapPercent;
    }

    public int getMaxHeapSize()
    {
        return maxHeapSize;
    }

    public void setMaxHeapSize(int maxHeapSize)
    {
        this.maxHeapSize = maxHeapSize;
    }

    public int getMaxHeapPercent()
    {
        return maxHeapPercent;
    }

    public void setMaxHeapPercent(int maxHeapPercent)
    {
        this.maxHeapPercent = maxHeapPercent;
    }
}
