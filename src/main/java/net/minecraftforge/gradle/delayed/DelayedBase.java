package net.minecraftforge.gradle.delayed;

import static net.minecraftforge.gradle.common.Constants.EXT_NAME_JENKINS;
import static net.minecraftforge.gradle.common.Constants.EXT_NAME_MC;
import groovy.lang.Closure;
import net.minecraftforge.gradle.common.BaseExtension;
import net.minecraftforge.gradle.common.JenkinsExtension;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public abstract class DelayedBase<V> extends Closure<V>
{
    protected Project project;
    private V resolved;
    protected String pattern;
    public boolean resolveOnce = true;
    @SuppressWarnings("rawtypes")
    protected IDelayedResolver[] resolvers;
    public static final IDelayedResolver<BaseExtension> RESOLVER = new IDelayedResolver<BaseExtension>()
    {
        @Override
        public String resolve(String pattern, Project project, BaseExtension extension)
        {
            return pattern;
        }
    };

    @SuppressWarnings("unchecked")
    public DelayedBase(Project owner, String pattern)
    {
        this(owner, pattern, RESOLVER);
    }

    public DelayedBase(Project owner, String pattern, IDelayedResolver<? extends BaseExtension>... resolvers)
    {
        super(owner);
        this.project = owner;
        this.pattern = pattern;
        this.resolvers = resolvers;
    }

    public abstract V resolveDelayed();
    
    @Override
    public final V call()
    {
        if (resolved == null || !resolveOnce)
        {
            resolved = resolveDelayed();
        }
        
        return resolved;
    }

    @Override
    public String toString()
    {
        return call().toString();
    }

    // interface
    public static interface IDelayedResolver<K extends BaseExtension>
    {
        public String resolve(String pattern, Project project, K extension);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static String resolve(String patern, Project project, IDelayedResolver... resolvers)
    {
        project.getLogger().debug("Resolving: " + patern);
        BaseExtension exten = (BaseExtension)project.getExtensions().getByName(EXT_NAME_MC);
        JenkinsExtension jenk = (JenkinsExtension)project.getExtensions().getByName(EXT_NAME_JENKINS);

        String build = "0";
        if (System.getenv().containsKey("BUILD_NUMBER"))
        {
            build = System.getenv("BUILD_NUMBER");
        }
        
        // resolvers first
        for (IDelayedResolver r : resolvers)
        {
            patern = r.resolve(patern, project, exten);
        }

        patern = patern.replace("{MC_VERSION}", exten.getVersion());
        patern = patern.replace("{MC_VERSION_SAFE}", exten.getVersion().replace('-', '_'));
        patern = patern.replace("{MCP_VERSION}", exten.getMcpVersion());
        patern = patern.replace("{CACHE_DIR}", project.getGradle().getGradleUserHomeDir().getAbsolutePath().replace('\\', '/') + "/caches");
        patern = patern.replace("{BUILD_DIR}", project.getBuildDir().getAbsolutePath().replace('\\', '/'));
        patern = patern.replace("{BUILD_NUM}", build);
        patern = patern.replace("{PROJECT}", project.getName());
        patern = patern.replace("{RUN_DIR}", exten.getRunDir().replace('\\', '/'));
        
        if (exten.mappingsSet())
        {
            patern = patern.replace("{MAPPING_CHANNEL}", exten.getMappingsChannel());
            patern = patern.replace("{MAPPING_CHANNEL_DOC}", exten.getMappingsChannelNoSubtype());
            patern = patern.replace("{MAPPING_VERSION}", exten.getMappingsVersion());
        }

        patern = patern.replace("{JENKINS_SERVER}",        jenk.getServer());
        patern = patern.replace("{JENKINS_JOB}",           jenk.getJob());
        patern = patern.replace("{JENKINS_AUTH_NAME}",     jenk.getAuthName());
        patern = patern.replace("{JENKINS_AUTH_PASSWORD}", jenk.getAuthPassword());

        project.getLogger().debug("Resolved:  " + patern);
        return patern;
    }
}
