package net.minecraftforge.gradle.delayed;

import static net.minecraftforge.gradle.Constants.*;
import net.minecraftforge.gradle.ExtensionObject;
import net.minecraftforge.gradle.JenkinsExtensionObject;

import org.gradle.api.Project;

import groovy.lang.Closure;

@SuppressWarnings("serial")
public class DelayedString extends Closure<String>
{
    private Project project;
    private String resolved;
    private String pattern;
    private IDelayedResolver[] resolvers;

    public DelayedString(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }

    @Override
    public String call()
    {
        if (resolved == null)
        {
            resolved = resolve(pattern, project, resolvers);
        }
        return resolved;
    }

    public static String resolve(String patern, Project project, IDelayedResolver[] resolvers)
    {
        project.getLogger().info("Resolving: " + patern);
        ExtensionObject exten = (ExtensionObject)project.getExtensions().getByName(EXT_NAME_MC);
        JenkinsExtensionObject jenk = (JenkinsExtensionObject)project.getExtensions().getByName(EXT_NAME_JENKINS);
        
        String build = "0";
        if (System.getenv().containsKey("BUILD_NUMBER"))
        {
            build = System.getenv("BUILD_NUMBER");
        }

        // For simplicities sake, if the version is in the standard format of {MC_VERSION}-{realVersion}
        // lets trim the MC version from the replacement string.
        String version = project.getVersion().toString();
        if (version.startsWith(exten.getVersion() + "-"))
        {
            version = version.substring(exten.getVersion().length() + 1);
        }
        
        patern = patern.replace("{MC_VERSION}", exten.getVersion());
        patern = patern.replace("{MAIN_CLASS}", exten.getMainClass());
        patern = patern.replace("{INSTALLER_VERSION}", exten.getInstallerVersion());
        patern = patern.replace("{CACHE_DIR}", project.getGradle().getGradleUserHomeDir().getAbsolutePath() + "/caches");
        patern = patern.replace("{BUILD_DIR}", project.getBuildDir().getAbsolutePath());
        patern = patern.replace("{VERSION}", version);
        patern = patern.replace("{BUILD_NUM}", build);
        patern = patern.replace("{PROJECT}", project.getName());

        patern = patern.replace("{JENKINS_SERVER}",        jenk.getServer());
        patern = patern.replace("{JENKINS_JOB}",           jenk.getJob());
        patern = patern.replace("{JENKINS_AUTH_NAME}",     jenk.getAuthName());
        patern = patern.replace("{JENKINS_AUTH_PASSWORD}", jenk.getAuthPassword());
        
        for (IDelayedResolver r : resolvers)
        {
            patern = r.resolve(patern, project, exten);
        }
        
        project.getLogger().info("Resolved:  " + patern);
        return patern;
    }

    public static interface IDelayedResolver
    {
        public String resolve(String patern, Project project, ExtensionObject extension);
    }
}
