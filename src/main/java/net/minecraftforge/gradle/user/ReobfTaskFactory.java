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
package net.minecraftforge.gradle.user;

import static net.minecraftforge.gradle.common.Constants.TASK_GENERATE_SRGS;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Jar;

import groovy.lang.Closure;
import net.minecraftforge.gradle.util.GradleConfigurationException;

public class ReobfTaskFactory implements NamedDomainObjectFactory<IReobfuscator>
{
    private final UserBasePlugin<?> plugin;

    public ReobfTaskFactory(UserBasePlugin<?> plugin)
    {
        this.plugin = plugin;
    }

    @SuppressWarnings("serial")
    @Override
    public IReobfuscator create(final String jarName)
    {
        String name = "reobf" + Character.toUpperCase(jarName.charAt(0)) + jarName.substring(1);
        final TaskSingleReobf task = plugin.maybeMakeTask(name, TaskSingleReobf.class);

        task.dependsOn(TASK_GENERATE_SRGS, jarName);
        task.mustRunAfter("test");
        
        task.setJar(new Closure<File>(null) {
            public File call()
            {
                return ((Jar) plugin.project.getTasks().getByName(jarName)).getArchivePath();
            }
        });

        plugin.project.getTasks().getByName("assemble").dependsOn(task);

        plugin.setupReobf(task);
        
        // do after-Evaluate resolution, for the same of good error reporting
        plugin.project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project arg0)
            {
                Task jar = plugin.project.getTasks().getByName(jarName);
                if (!(jar instanceof Jar))
                {
                    throw new GradleConfigurationException(jarName + "  is not a jar task. Can only reobf jars!");
                }
            }
        });

        return new TaskWrapper(jarName, task);
    }

    class TaskWrapper implements IReobfuscator
    {
        private final String name;
        private final IReobfuscator reobf;

        public TaskWrapper(String name, IReobfuscator reobf)
        {
            this.name = name;
            this.reobf = reobf;
        }

        public String getName()
        {
            return name;
        }

        /**
         * Returns the instance of {@link TaskSingleReobf} that this object
         * wraps.
         *
         * @return The task
         */
        public IReobfuscator getTask()
        {
            return reobf;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof TaskWrapper)
            {
                return name.equals(((TaskWrapper) obj).name);
            }
            return false;
        }

        public Object getMappings()
        {
            return reobf.getMappings();
        }

        public void setMappings(Object srg)
        {
            reobf.setMappings(srg);
        }

        public void setClasspath(FileCollection classpath)
        {
            reobf.setClasspath(classpath);
        }

        public FileCollection getClasspath()
        {
            return reobf.getClasspath();
        }

        public List<Object> getExtra()
        {
            return reobf.getExtra();
        }

        public void setExtra(List<Object> extra)
        {
            reobf.setExtra(extra);
        }

        public void extra(Object... o)
        {
            reobf.extra(o);
        }

        public void extra(Collection<Object> o)
        {
            reobf.extra(o);
        }

        public void useSrgSrg()
        {
            reobf.useSrgSrg();
        }

        public void useNotchSrg()
        {
            reobf.useNotchSrg();
        }
    }
}
