package net.minecraftforge.gradle.delayed;

import static net.minecraftforge.gradle.common.Constants.EXT_NAME_JENKINS;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.JenkinsExtension;
import net.minecraftforge.gradle.dev.DevExtension;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public abstract class DelayedBase<V> extends Closure<V>
{
    protected Project project;
    protected V resolved;
    protected String pattern;
    protected IDelayedResolver[] resolvers;
    public static final IDelayedResolver RESOLVER = new IDelayedResolver()
    {
        @Override
        public String resolve(String pattern, Project project, DevExtension extension)
        {
            return pattern;
        }
    };
    
    public DelayedBase(Project owner, String pattern)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = new IDelayedResolver[] {RESOLVER};
    }

    public DelayedBase(Project owner, String pattern, IDelayedResolver... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }
    
    @Override
    public abstract V call();
    
    // interface
    public static interface IDelayedResolver
    {
        public String resolve(String pattern, Project project, DevExtension extension);
    }
    
    public static String resolve(String patern, Project project, IDelayedResolver resolver)
    {
        return resolve(patern, project, new IDelayedResolver[] {resolver});
    }
    
    public static String resolve(String patern, Project project, IDelayedResolver[] resolvers)
    {
        project.getLogger().info("Resolving: " + patern);
        DevExtension exten = (DevExtension)project.getExtensions().getByName(EXT_NAME_MC);
        JenkinsExtension jenk = (JenkinsExtension)project.getExtensions().getByName(EXT_NAME_JENKINS);
        
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
}
