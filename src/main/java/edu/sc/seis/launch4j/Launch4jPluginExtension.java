
package edu.sc.seis.launch4j

import java.io.File;

import org.codehaus.groovy.util.HashCodeHelper;
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin;


class Launch4jPluginExtension implements Serializable {

    String launch4jCmd = "launch4j"
    String outputDir = "launch4j"
    String xmlFileName = "launch4j.xml"
    String mainClassName
    boolean dontWrapJar = false
    String headerType = "gui"
    String jar
    String outfile
    String errTitle = ""
    String cmdLine = ""
    String chdir = '.'
    String priority = 'normal'
    String downloadUrl = ""
    String supportUrl = ""
    boolean customProcName = false
    boolean stayAlive = false
    String manifest = ""
    String icon = ""
    String version = ""
    String copyright = "unknown"
    String opt = ""
	
	String bundledJrePath
	String jreMinVersion
	String jreMaxVersion
	
	String mutexName
	String windowTitle
	
	String messagesStartupError
	String messagesBundledJreError
	String messagesJreVersionError
    String messagesLauncherError
	
	Integer initialHeapSize
	Integer initialHeapPercent
	Integer maxHeapSize
	Integer maxHeapPercent
	
    public File getXmlOutFileForProject(Project project) {
        return project.file("${project.buildDir}/${outputDir}/${xmlFileName}")
    }
 
    void initExtensionDefaults(Project project) {
        outfile = new File(project.name+'.exe')
        jar = "lib/"+project.tasks[JavaPlugin.JAR_TASK_NAME].outputs.files.getSingleFile().name
        version = project.version
        jreMinVersion = project.targetCompatibility
        while (jreMinVersion ==~ /\d+(\.\d+){0,1}/) {
                jreMinVersion = jreMinVersion+'.0'
            }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((chdir == null) ? 0 : chdir.hashCode());
        result = prime * result + (cmdLine ? 1231 : 1237);
        result = prime * result + ((copyright == null) ? 0 : copyright.hashCode());
        result = prime * result + (customProcName ? 1231 : 1237);
        result = prime * result + (dontWrapJar ? 1231 : 1237);
        result = prime * result + ((downloadUrl == null) ? 0 : downloadUrl.hashCode());
        result = prime * result + ((errTitle == null) ? 0 : errTitle.hashCode());
        result = prime * result + ((headerType == null) ? 0 : headerType.hashCode());
        result = prime * result + ((icon == null) ? 0 : icon.hashCode());
        result = prime * result + ((jar == null) ? 0 : jar.hashCode());
        result = prime * result + ((launch4jCmd == null) ? 0 : launch4jCmd.hashCode());
        result = prime * result + ((mainClassName == null) ? 0 : mainClassName.hashCode());
        result = prime * result + ((manifest == null) ? 0 : manifest.hashCode());
        result = prime * result + ((opt == null) ? 0 : opt.hashCode());
        result = prime * result + ((outfile == null) ? 0 : outfile.hashCode());
        result = prime * result + ((outputDir == null) ? 0 : outputDir.hashCode());
        result = prime * result + ((priority == null) ? 0 : priority.hashCode());
        result = prime * result + (stayAlive ? 1231 : 1237);
        result = prime * result + ((supportUrl == null) ? 0 : supportUrl.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        result = prime * result + ((xmlFileName == null) ? 0 : xmlFileName.hashCode());
		
		result = HashCodeHelper.updateHash(result, bundledJrePath);
		result = HashCodeHelper.updateHash(result, jreMinVersion);
		result = HashCodeHelper.updateHash(result, jreMaxVersion);
		
		result = HashCodeHelper.updateHash(result, mutexName);
		result = HashCodeHelper.updateHash(result, windowTitle);
		
		result = HashCodeHelper.updateHash(result, messagesStartupError);
		result = HashCodeHelper.updateHash(result, messagesBundledJreError);
		result = HashCodeHelper.updateHash(result, messagesJreVersionError);
		result = HashCodeHelper.updateHash(result, messagesLauncherError);
		
		result = HashCodeHelper.updateHash(result, initialHeapSize);
		result = HashCodeHelper.updateHash(result, initialHeapPercent);
		result = HashCodeHelper.updateHash(result, maxHeapSize);
		result = HashCodeHelper.updateHash(result, maxHeapPercent);
		
		return result;
    }

    @Override
    public boolean equals(Object obj) {
		if (this.is(obj)) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		
        Launch4jPluginExtension other = (Launch4jPluginExtension)obj;
        
		if ((chdir == null && other.chdir != null) || !chdir.equals(other.chdir)) {
			return false;
        } 
			
        if (cmdLine != other.cmdLine)
            return false;
			
        if (copyright == null) {
            if (other.copyright != null)
                return false;
				
        } 
		else if (!copyright.equals(other.copyright))
            return false;
			
        if (customProcName != other.customProcName)
            return false;
        if (dontWrapJar != other.dontWrapJar)
            return false;
        if (downloadUrl == null) {
            if (other.downloadUrl != null)
                return false;
        } else if (!downloadUrl.equals(other.downloadUrl))
            return false;
        if (errTitle == null) {
            if (other.errTitle != null)
                return false;
        } else if (!errTitle.equals(other.errTitle))
            return false;
        if (headerType == null) {
            if (other.headerType != null)
                return false;
        } else if (!headerType.equals(other.headerType))
            return false;
        if (icon == null) {
            if (other.icon != null)
                return false;
        } else if (!icon.equals(other.icon))
            return false;
        if (jar == null) {
            if (other.jar != null)
                return false;
        } else if (!jar.equals(other.jar))
            return false;
        if (launch4jCmd == null) {
            if (other.launch4jCmd != null)
                return false;
        } else if (!launch4jCmd.equals(other.launch4jCmd))
            return false;
        if (mainClassName == null) {
            if (other.mainClassName != null)
                return false;
        } else if (!mainClassName.equals(other.mainClassName))
            return false;
        if (manifest == null) {
            if (other.manifest != null)
                return false;
        } else if (!manifest.equals(other.manifest))
            return false;
        if (opt == null) {
            if (other.opt != null)
                return false;
        } else if (!opt.equals(other.opt))
            return false;
        if (outfile == null) {
            if (other.outfile != null)
                return false;
        } else if (!outfile.equals(other.outfile))
            return false;
        if (outputDir == null) {
            if (other.outputDir != null)
                return false;
        } else if (!outputDir.equals(other.outputDir))
            return false;
        if (priority == null) {
            if (other.priority != null)
                return false;
        } else if (!priority.equals(other.priority))
            return false;
        if (stayAlive != other.stayAlive)
            return false;
        if (supportUrl == null) {
            if (other.supportUrl != null)
                return false;
        } else if (!supportUrl.equals(other.supportUrl))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
			
        if (xmlFileName == null) {
            if (other.xmlFileName != null)
                return false;
        } else if (!xmlFileName.equals(other.xmlFileName))
            return false;
			
	    if ((bundledJrePath == null && other.bundledJrePath != null) || !bundledJrePath.equals(other.bundledJrePath)) {
	        return false;
        }    
		if ((jreMinVersion == null && other.jreMinVersion != null) || !jreMinVersion.equals(other.jreMinVersion)) {
			return false;
		}
		if ((jreMaxVersion == null && other.jreMaxVersion != null) || !jreMaxVersion.equals(other.jreMaxVersion)) {
			return false;
		}
		
		if ((mutexName == null && other.mutexName != null) || !mutexName.equals(other.mutexName)) {
			return false;
		} 
		if ((windowTitle == null && other.windowTitle != null) || !windowTitle.equals(other.windowTitle)) {
			return false;
		}
		
		if ((messagesStartupError == null && other.messagesStartupError != null) || !messagesStartupError.equals(other.messagesStartupError)) {
			return false;
		}
		if ((messagesBundledJreError == null && other.messagesBundledJreError != null) || !messagesBundledJreError.equals(other.messagesBundledJreError)) {
			return false;
		}
		if ((messagesJreVersionError == null && other.messagesJreVersionError != null) || !messagesJreVersionError.equals(other.messagesJreVersionError)) {
			return false;
		}
		if ((messagesLauncherError == null && other.messagesLauncherError != null) || !messagesLauncherError.equals(other.messagesLauncherError)) {
			return false;
		}
		
		if ((initialHeapSize == null && other.initialHeapSize != null) || !initialHeapSize.equals(other.initialHeapSize)) {
			return false;
		}
		if ((initialHeapPercent == null && other.initialHeapPercent != null) || !initialHeapPercent.equals(other.initialHeapPercent)) {
			return false;
		}
		if ((maxHeapSize == null && other.maxHeapSize != null) || !maxHeapSize.equals(other.maxHeapSize)) {
			return false;
		}
		if ((maxHeapPercent == null && other.maxHeapPercent != null) || !maxHeapPercent.equals(other.maxHeapPercent)) {
			return false;
		}
			
		return true;
    }


}
