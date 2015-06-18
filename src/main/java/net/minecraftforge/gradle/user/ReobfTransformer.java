package net.minecraftforge.gradle.user;

import java.io.Serializable;

public interface ReobfTransformer extends Serializable
{
    
    /**
     * You should use a classNode, but DONT use EXPAND_FRAMES
     */
    public abstract byte[] transform(byte[] data);
}
