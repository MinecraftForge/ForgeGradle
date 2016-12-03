package net.minecraftforge.gradle.tasks.fernflower;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.util.InterpreterUtil;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

class ByteCodeProvider implements IBytecodeProvider {
    @Override
    public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
        File file = new File(externalPath);
        if (internalPath == null) {
            return InterpreterUtil.getBytes(file);
        } else {
            ZipFile archive = new ZipFile(file);
            try {
                ZipEntry entry = archive.getEntry(internalPath);
                if (entry == null) {
                    throw new IOException("Entry not found: " + internalPath);
                }
                return InterpreterUtil.getBytes(archive, entry);
            } finally {
                archive.close();
            }
        }
    }
}
