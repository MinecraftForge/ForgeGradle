package net.minecraftforge.gradle.dev;

import groovy.lang.Closure;

import java.io.ByteArrayOutputStream;

import net.minecraftforge.gradle.common.BasePlugin;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;

import org.gradle.api.Project;
import org.gradle.process.ExecSpec;

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
