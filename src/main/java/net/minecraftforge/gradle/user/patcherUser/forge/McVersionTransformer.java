package net.minecraftforge.gradle.user.patcherUser.forge;

import java.util.List;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.user.ReobfTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class McVersionTransformer implements ReobfTransformer
{
    private static final long serialVersionUID = 1L;

    private Object            mcVersion;

    protected McVersionTransformer(Object mcVersion)
    {
        this.mcVersion = mcVersion;
    }

    @Override
    public byte[] transform(byte[] data)
    {
        String mcVersion = Constants.resolveString(this.mcVersion);

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
                    mod.values.add("[" + mcVersion + "]");
                }

                break; // break out, im done. There cant be 2 @Mods in a file... can there?
            }
        }

        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }
}
