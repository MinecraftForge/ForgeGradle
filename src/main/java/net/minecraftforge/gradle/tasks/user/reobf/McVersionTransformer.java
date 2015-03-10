package net.minecraftforge.gradle.tasks.user.reobf;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import com.google.common.io.ByteStreams;

public class McVersionTransformer
{
    private final File inJar;
    private final File outJar;
    
    protected McVersionTransformer(File inJar, File outJar)
    {
        this.inJar = inJar;
        this.outJar = outJar;
    }
    
    protected void transform(String mcVersion) throws IOException
    {
        ZipFile in = new ZipFile(inJar);
        final ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outJar)));

        for (ZipEntry e : Collections.list(in.entries()))
        {
            if (e.isDirectory())
            {
                out.putNextEntry(e);
            }
            else
            {
                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);

                byte[] data = ByteStreams.toByteArray(in.getInputStream(e));

                // correct source name
                if (e.getName().endsWith(".class"))
                    data = injectVersion(data, mcVersion);

                out.write(data);
            }
        }

        out.flush();
        out.close();
        in.close();
    }

    public static byte[] injectVersion(byte[] data, String mcVersion)
    {
        ClassReader reader = new ClassReader(data);
        ClassNode node = new ClassNode();

        reader.accept(node, 0);
        List<AnnotationNode> annots = node.visibleAnnotations;
        
        if (annots == null || annots.isEmpty()) // annotations
            return data;
        
        for (AnnotationNode mod : annots)
        {
            if (mod.desc.endsWith("fml/common/Mod;"))
            {
                int index = mod.values.indexOf("acceptedMinecraftVersions");
                if (index == -1)
                {
                    mod.values.add("acceptedMinecraftVersions");
                    mod.values.add("["+mcVersion+"]");
                }
                
                break; // break out, im done. There cant be 2 @Mods in a file... can there?
            }
        }
        

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
