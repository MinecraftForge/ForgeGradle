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

import java.io.File;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.bundling.Jar;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import groovy.lang.Closure;
import net.minecraftforge.gradle.common.Constants;
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

        task.dependsOn(Constants.TASK_GENERATE_SRGS, jarName);
        task.mustRunAfter("test");

        task.setJar(new Closure<File>(ReobfTaskFactory.class) {
            public File call()
            {
                return ((Jar) plugin.project.getTasks().getByName(jarName)).getArchivePath();
            }
        });

        plugin.project.getTasks().getByName("assemble").dependsOn(task);

        ReobfTaskWrapper wrapper = new ReobfTaskWrapper(jarName, task);
        plugin.setupReobf(wrapper);

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

        return wrapper;
    }

    public class ReobfTaskWrapper implements IReobfuscator
    {
        private final String name;
        private final TaskSingleReobf reobf;
        private ReobfMappingType mappingType;

        public ReobfTaskWrapper(String name, TaskSingleReobf reobf)
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
        public TaskSingleReobf getTask()
        {
            return reobf;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj instanceof ReobfTaskWrapper)
            {
                return name.equals(((ReobfTaskWrapper) obj).name);
            }
            return false;
        }

        @Override
        public File getMappings()
        {
            return reobf.getPrimarySrg();
        }

        @Override
        public void setMappings(Object srg)
        {
            mappingType = ReobfMappingType.CUSTOM;
            reobf.setPrimarySrg(srg);
        }

        @Override
        public void setMappingType(ReobfMappingType type)
        {
            Preconditions.checkNotNull(type, "Mapping type cannot be null. Use setMappings() to use custom mappings.");
            Preconditions.checkArgument(type != ReobfMappingType.CUSTOM,
                    "Cannot set a Custom mapping type. Use setMappings() instead.");
            mappingType = type;
            reobf.setPrimarySrg(plugin.delayedFile(type.getPath()));
        }

        @Override
        public ReobfMappingType getMappingType()
        {
            return this.mappingType;
        }

        @Override
        public void setClasspath(FileCollection classpath)
        {
            reobf.setClasspath(classpath);
        }

        @Override
        public FileCollection getClasspath()
        {
            return reobf.getClasspath();
        }

        @Override
        public void setExtraLines(List<Object> extra)
        {
            reobf.getExtraSrgLines().clear();
            extraLines(extra);
        }

        @Override
        public List<Object> getExtraLines()
        {
            List<Object> list = Lists.newArrayList();
            list.addAll(reobf.getExtraSrgLines());
            return list;
        }

        @Override
        public void extraLines(Iterable<Object> o)
        {
            for (Object obj : o)
            {
                reobf.addExtraSrgLine(Constants.resolveString(obj));
            }
        }

        @Override
        public void extraLines(Object... o)
        {
            for (Object obj : o)
            {
                reobf.addExtraSrgLine(Constants.resolveString(obj));
            }
        }

        @Override
        public List<Object> getExtraFiles()
        {
            List<Object> list = Lists.newArrayList();
            list.addAll(reobf.getSecondarySrgFiles().getFiles());
            return list;
        }

        @Override
        public void extraFiles(Iterable<Object> o)
        {
            for (Object obj : o)
            {
                reobf.addSecondarySrgFile(obj);
            }
        }

        @Override
        public void extraFiles(Object... o)
        {
            for (Object obj : o)
            {
                reobf.addSecondarySrgFile(obj);
            }
        }

        @Deprecated
        @Override
        public void useSrgSrg()
        {
            setMappingType(ReobfMappingType.SEARGE);
            warnDeprecation("useSrgSrg()", "mappingType");
        }

        @Deprecated
        @Override
        public void useNotchSrg()
        {
            setMappingType(ReobfMappingType.NOTCH);
            warnDeprecation("useNotchSrg()", "mappingType");
        }

        private void warnDeprecation(String old, String new_)
        {
            plugin.project.getLogger().warn("Warning, {} is deprecated! You should use {} instead.", old, new_);
        }
    }
}
