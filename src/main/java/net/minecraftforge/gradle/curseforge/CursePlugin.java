package net.minecraftforge.gradle.curseforge;

import groovy.lang.Closure;
import net.minecraftforge.gradle.user.UserBasePlugin;
import net.minecraftforge.gradle.user.UserExtension;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.bundling.Jar;

public class CursePlugin implements Plugin<Project>
{

    @SuppressWarnings({ "rawtypes", "serial", "unchecked" })
    @Override
    public void apply(final Project project)
    {
        // create task
        final CurseUploadTask upload = project.getTasks().create("curse", CurseUploadTask.class);
        upload.setGroup("ForgeGradle");
        upload.setDescription("Uploads an artifact to CurseForge. Configureable in the curse{} block.");
        
        // set artifact
        upload.setArtifact(new Closure(null, null) {
            public Object call()
            {
                if (project.getPlugins().hasPlugin("java"))
                    return ((Jar) project.getTasks().getByName("jar")).getArchivePath();
                return null;
            }
            
        });
        
        // configure task extra.
        project.afterEvaluate(new Action() {

            @Override
            public void execute(Object arg0)
            {
                // dont continue if its already failed!
                if (project.getState().getFailure() != null)
                    return;
                
                UserBasePlugin plugin = userPluginApplied(project);
                upload.addGameVersion(plugin.getExtension().getVersion());
                
                upload.dependsOn("reobf");
            }
            
        });
    }

    @SuppressWarnings("unchecked")
    private UserBasePlugin<UserExtension> userPluginApplied(Project project)
    {
        // search for overlays..
        for (Plugin<Project> p : project.getPlugins())
        {
            if (p instanceof UserBasePlugin)
            {
                return (UserBasePlugin<UserExtension>) p;
            }
        }

        return null;
    }

}
