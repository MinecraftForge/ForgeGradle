/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.user.patcherUser.forge;

import static net.minecraftforge.gradle.common.Constants.REPLACE_MC_VERSION;
import static net.minecraftforge.gradle.user.UserConstants.TASK_REOBF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Strings;
import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.tasks.CreateStartTask;
import net.minecraftforge.gradle.user.ReobfMappingType;
import net.minecraftforge.gradle.user.ReobfTaskFactory.ReobfTaskWrapper;
import net.minecraftforge.gradle.user.TaskSingleReobf;
import net.minecraftforge.gradle.user.UserConstants;
import net.minecraftforge.gradle.user.patcherUser.PatcherUserBasePlugin;
import net.minecraftforge.gradle.util.GradleConfigurationException;
import net.minecraftforge.gradle.util.json.fgversion.FGVersion;
import net.minecraftforge.gradle.util.json.fgversion.FGVersionWrapper;

public class ForgePlugin extends PatcherUserBasePlugin<ForgeExtension>
{
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void applyUserPlugin()
    {
        super.applyUserPlugin();

        // setup reobf
        {
            TaskSingleReobf reobf = (TaskSingleReobf) project.getTasks().getByName(TASK_REOBF);
            reobf.addPreTransformer(new McVersionTransformer(delayedString(REPLACE_MC_VERSION)));
        }

        // add coremod loading hack to gradle start
        {
            CreateStartTask makeStart = ((CreateStartTask)project.getTasks().getByName(UserConstants.TASK_MAKE_START));
            for (String res : Constants.GRADLE_START_FML_RES)
            {
                makeStart.addResource(res);
            }
            makeStart.addExtraLine("net.minecraftforge.gradle.GradleForgeHacks.searchCoremods(this);");
        }

        // configure eclipse task to do extra stuff.
        project.getTasks().getByName("eclipse").doLast(new Action() {

            @Override
            public void execute(Object arg0)
            {
                // find the file
                File f = new File("eclipse/.metadata/.plugins/org.eclipse.core.resources/.projects");

                if (!f.exists()) // folder doesnt exist
                {
                    return;
                }

                File[] files = f.listFiles();
                if (files.length < 1) // empty folder
                    return;

                f = new File(files[0], ".location");

                if (f.exists()) // if .location exists
                {
                    String projectDir = "URI//" + project.getProjectDir().toURI().toString();
                    try
                    {
                        byte[] LOCATION_BEFORE = new byte[] { 0x40, (byte) 0xB1, (byte) 0x8B, (byte) 0x81, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x25, (byte) 0x96, (byte) 0xE7, (byte) 0xA3, (byte) 0x93, (byte) 0xBE, 0x1E };
                        byte[] LOCATION_AFTER = new byte[] { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0xC0, 0x58, (byte) 0xFB, (byte) 0xF3, 0x23, (byte) 0xBC, 0x00, 0x14, 0x1A, 0x51, (byte) 0xF3, (byte) 0x8C, 0x7B, (byte) 0xBB, 0x77, (byte) 0xC6 };

                        FileOutputStream fos = new FileOutputStream(f);
                        fos.write(LOCATION_BEFORE);//Unknown but w/e
                        fos.write((byte) ((projectDir.length() & 0xFF) >> 8));
                        fos.write((byte) ((projectDir.length() & 0xFF) >> 0));
                        fos.write(projectDir.getBytes());
                        fos.write(LOCATION_AFTER);//Unknown but w/e
                        fos.close();
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    @Override
    protected void setupReobf(ReobfTaskWrapper reobf)
    {
        super.setupReobf(reobf);
        reobf.setMappingType(ReobfMappingType.SEARGE);
    }

    @Override
    protected void afterEvaluate()
    {
        ForgeExtension ext = getExtension();
        if (Strings.isNullOrEmpty(ext.getForgeVersion()))
        {
            throw new GradleConfigurationException("You must set the Forge version!");
        }

        super.afterEvaluate();

        // add manifest things
        {
            Jar jarTask = (Jar) project.getTasks().getByName("jar");

            if (!Strings.isNullOrEmpty(ext.getCoreMod()))
            {
                jarTask.getManifest().getAttributes().put("FMLCorePlugin", ext.getCoreMod());
            }
        }
    }

    @Override
    protected void onVersionCheck(FGVersion version, FGVersionWrapper wrapper)
    {
        String forgeVersion = getExtension().getForgeVersion();

        // isolate build number
        int index = forgeVersion.indexOf('-');
        if (index >= 0)
            forgeVersion = forgeVersion.substring(0, index);
        index = forgeVersion.lastIndexOf('.');
        forgeVersion.substring(index + 1);

        int buildNum = Integer.parseInt(forgeVersion);

        int minBuild = version.ext.get("forgeMinBuild").getAsInt();
        int maxBuild = version.ext.get("forgeMaxBuild").getAsInt();

        if (buildNum < minBuild)
            throw new GradleConfigurationException("This version of ForgeGradle ("+getExtension().forgeGradleVersion+") does not support forge builds less than #"+minBuild);
        else if (buildNum > maxBuild)
            throw new GradleConfigurationException("This version of ForgeGradle ("+getExtension().forgeGradleVersion+") does not support forge builds greater than #"+maxBuild);
    }

    @Override
    public String getApiGroup(ForgeExtension ext)
    {
        return "net.minecraftforge";
    }

    @Override
    public String getApiName(ForgeExtension ext)
    {
        return "forge";
    }

    @Override
    public String getApiVersion(ForgeExtension ext)
    {
        return ext.getVersion() + "-" + ext.getForgeVersion();
    }

    @Override
    public String getUserdevClassifier(ForgeExtension ext)
    {
        return "userdev";
    }

    @Override
    public String getUserdevExtension(ForgeExtension ext)
    {
        return "jar";
    }

    @Override
    protected String getClientTweaker(ForgeExtension ext)
    {
        return getApiGroup(ext) + ".fml.common.launcher.FMLTweaker";
    }

    @Override
    protected String getServerTweaker(ForgeExtension ext)
    {
        return getApiGroup(ext) + ".fml.common.launcher.FMLServerTweaker";
    }

    @Override
    protected String getClientRunClass(ForgeExtension ext)
    {
        return "net.minecraft.launchwrapper.Launch";
    }

    @Override
    protected List<String> getClientRunArgs(ForgeExtension ext)
    {
        return ext.getResolvedClientRunArgs();
    }

    @Override
    protected String getServerRunClass(ForgeExtension ext)
    {
        return getClientRunClass(ext);
    }

    @Override
    protected List<String> getServerRunArgs(ForgeExtension ext)
    {
        return ext.getResolvedServerRunArgs();
    }

    @Override
    protected List<String> getClientJvmArgs(ForgeExtension ext)
    {
        List<String> out = ext.getResolvedClientJvmArgs();
        if (!Strings.isNullOrEmpty(ext.getCoreMod()))
        {
            out.add("-Dfml.coreMods.load=" + ext.getCoreMod());
        }
        return out;
    }

    @Override
    protected List<String> getServerJvmArgs(ForgeExtension ext)
    {
        List<String> out = ext.getResolvedServerJvmArgs();
        if (!Strings.isNullOrEmpty(ext.getCoreMod()))
        {
            out.add("-Dfml.coreMods.load=" + ext.getCoreMod());
        }
        return out;
    }
}
