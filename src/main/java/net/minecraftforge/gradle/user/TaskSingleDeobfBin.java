package net.minecraftforge.gradle.user;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.ParallelizableTask;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import au.com.bytecode.opencsv.CSVReader;

import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;

@ParallelizableTask
public class TaskSingleDeobfBin extends CachedTask
{
    @InputFile
    private Object methodCsv;

    @InputFile
    private Object fieldCsv;

    @InputFile
    private Object inJar;

    @Cached
    @OutputFile
    private Object outJar;

    @TaskAction
    public void doTask() throws IOException
    {
        final Map<String, String> methods = Maps.newHashMap();
        final Map<String, String> fields = Maps.newHashMap();

        // read CSV files
        CSVReader reader = Constants.getReader(getMethodCsv());
        for (String[] s : reader.readAll())
        {
            methods.put(s[0], s[1]);
        }

        reader = Constants.getReader(getFieldCsv());
        for (String[] s : reader.readAll())
        {
            fields.put(s[0], s[1]);
        }

        // actually do the jar copy..
        File input = getInJar();
        File output = getOutJar();

        output.getParentFile().mkdirs();

        // begin reading jar
        ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
        JarOutputStream zout = new JarOutputStream(new FileOutputStream(output));
        ZipEntry entry = null;

        while ((entry = zin.getNextEntry()) != null)
        {
            // no META or dirs. wel take care of dirs later.
            if (entry.getName().contains("META-INF"))
            {
                continue;
            }

            // resources or directories.
            if (entry.isDirectory() || !entry.getName().endsWith(".class"))
            {
                zout.putNextEntry(new JarEntry(entry));
                ByteStreams.copy(zin, zout);
                zout.closeEntry();
            }
            else
            {
                // classes
                zout.putNextEntry(new JarEntry(entry.getName()));
                zout.write(deobfClass(ByteStreams.toByteArray(zin), methods, fields));
                zout.closeEntry();
            }
        }

        zout.close();
        zin.close();
    }

    private static byte[] deobfClass(byte[] classData, Map<String, String> methods, Map<String, String> fields)
    {
        ClassReader reader = new ClassReader(classData);
        ClassNode node = new ClassNode();

        reader.accept(node, 0);

        for (FieldNode fieldNode : (List<FieldNode>)node.fields)
        {
            if (fields.containsKey(fieldNode.name))
            {
                fieldNode.name = fields.get(fieldNode.name);
            }
        }

        for (MethodNode methodNode : (List<MethodNode>)node.methods)
        {
            if (methods.containsKey(methodNode.name))
            {
                methodNode.name = methods.get(methodNode.name);
            }

            Iterator<AbstractInsnNode> instructions = methodNode.instructions.iterator();
            while (instructions.hasNext())
            {
                AbstractInsnNode insn = instructions.next();
                switch (insn.getType())
                    {
                        case AbstractInsnNode.FIELD_INSN:
                            FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                            fieldInsn.name = fields.containsKey(fieldInsn.name) ? fields.get(fieldInsn.name) : fieldInsn.name;
                            break;
                        case AbstractInsnNode.METHOD_INSN:
                            MethodInsnNode methodInsn = (MethodInsnNode) insn;
                            methodInsn.name = methods.containsKey(methodInsn.name) ? methods.get(methodInsn.name) : methodInsn.name;
                            break;
                        case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
                            InvokeDynamicInsnNode idInsn = (InvokeDynamicInsnNode) insn;
                            idInsn.name = methods.containsKey(idInsn.name) ? methods.get(idInsn.name) : idInsn.name;
                            break;
                    }
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    public File getMethodCsv()
    {
        return getProject().file(methodCsv);
    }

    public void setMethodCsv(Object methodCsv)
    {
        this.methodCsv = methodCsv;
    }

    public File getFieldCsv()
    {
        return getProject().file(fieldCsv);
    }

    public void setFieldCsv(Object fieldCsv)
    {
        this.fieldCsv = fieldCsv;
    }

    public File getInJar()
    {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar)
    {
        this.inJar = inJar;
    }

    public File getOutJar()
    {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar)
    {
        this.outJar = outJar;
    }
}
