/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
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
package net.minecraftforge.gradle.testsupport;

import org.junit.Assert;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.*;
import java.util.jar.*;
import java.util.stream.*;

public class JarComparison
{
    public static void compareJarClassMembers(File expected, File actual) throws IOException
    {
        try (JarFile expectedJarFile = new JarFile(expected);
             JarFile actualJarFile = new JarFile(actual))
        {
            compareJarClassMembers(expectedJarFile, actualJarFile);
        }
    }

    public static void compareJarClassMembers(JarFile expected, JarFile actual) throws IOException
    {
        boolean visitedAnyClasses = false;
        for (JarEntry entry : (Iterable<JarEntry>) expected.stream()::iterator)
        {
            if (!entry.getName().endsWith(".class"))
                continue;

            visitedAnyClasses = true;
            ClassNode expectedNode = readClassNodeFromJarEntryInJarFile(expected, entry);
            Assert.assertNotNull("Should load expected node", expectedNode);
            ClassNode changedNode = readClassNodeFromJarEntryInJarFile(actual, actual.getJarEntry(entry.getName()));
            Assert.assertNotNull("Should load changed node", changedNode);

            for (FieldNode outer : expectedNode.fields)
            {
                boolean found = false;
                for (FieldNode inner : changedNode.fields)
                    if (key(inner).equals(key(outer)))
                    {
                        found = true;
                        break;
                    }
                Assert.assertTrue("Should find field " + key(outer) + " in " + changedNode.name, found);
            }

            for (MethodNode outer : expectedNode.methods)
            {
                boolean found = false;
                for (MethodNode inner : changedNode.methods)
                    if (key(inner).equals(key(outer)))
                    {
                        found = true;
                        break;
                    }
                Assert.assertTrue("Should find method " + key(outer) + " in " + changedNode.name + ", actual methods\n" + changedNode.methods.stream().map(JarComparison::key).collect(Collectors.toList()), found);
            }
        }

        Assert.assertTrue("Should visit classes in expected jar " + expected, visitedAnyClasses);
    }

    private static String key(MethodNode node) {
        return node.name + ':' + node.desc;
    }

    private static String key(FieldNode node) {
        return node.name + ':' + node.desc;
    }

    private static ClassNode readClassNodeFromJarEntryInJarFile(JarFile jarFile, JarEntry jarEntry) throws IOException
    {
        if (jarEntry == null)
            return null;
        try (InputStream is = jarFile.getInputStream(jarEntry))
        {
            ClassNode node = new ClassNode();
            ClassReader classReader = new ClassReader(is);
            classReader.accept(node, 0);
            return node;
        }
    }
}
