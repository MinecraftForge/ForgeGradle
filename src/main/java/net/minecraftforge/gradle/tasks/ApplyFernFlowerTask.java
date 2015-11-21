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
package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.common.Constants;
import net.minecraftforge.gradle.util.caching.Cached;
import net.minecraftforge.gradle.util.caching.CachedTask;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.main.DecompilerContext;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.main.extern.IVariableNameProvider;
import org.jetbrains.java.decompiler.main.extern.IVariableNamingFactory;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.InterpreterUtil;
import org.jetbrains.java.decompiler.util.JADNameProvider;

public class ApplyFernFlowerTask extends CachedTask {
    @InputFile
    Object inJar;

    @Cached
    @OutputFile
    Object outJar;

    private FileCollection         classpath;

    @TaskAction
    public void applyFernFlower() throws IOException {
        final File in = getInJar();
        final File out = getOutJar();

        final File tempDir = this.getTemporaryDir();
        final File tempJar = new File(this.getTemporaryDir(), in.getName());

        Map<String, Object> mapOptions = new HashMap<String, Object>();
        mapOptions.put(IFernflowerPreferences.DECOMPILE_INNER, "1");
        mapOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        mapOptions.put(IFernflowerPreferences.ASCII_STRING_CHARACTERS, "1");
        mapOptions.put(IFernflowerPreferences.INCLUDE_ENTIRE_CLASSPATH, "1");
        mapOptions.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        mapOptions.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1");
        mapOptions.put(IFernflowerPreferences.REMOVE_BRIDGE, "1");
        mapOptions.put(IFernflowerPreferences.LITERALS_AS_IS, "0");
        mapOptions.put(IFernflowerPreferences.UNIT_TEST_MODE, "0");
        mapOptions.put(IFernflowerPreferences.MAX_PROCESSING_METHOD, "0");
        mapOptions.put(DecompilerContext.RENAMER_FACTORY, AdvancedJadRenamerFactory.class.getName());

        PrintStreamLogger logger = new PrintStreamLogger(Constants.getTaskLogStream(getProject(), getName() + ".log"));
        BaseDecompiler decompiler = new BaseDecompiler(new ByteCodeProvider(), new ArtifactSaver(tempDir), mapOptions, logger);

        decompiler.addSpace(in, true);
        for (File library : classpath) {
            decompiler.addSpace(library, false);
        }

        decompiler.decompileContext();
        Constants.copyFile(tempJar, out);
    }

    public static class AdvancedJadRenamerFactory implements IVariableNamingFactory {
        @Override
        public IVariableNameProvider createFactory(StructMethod arg0)
        {
            // TODO Auto-generated method stub
            return new AdvancedJadRenamer(arg0);
        }
    }
    public static class AdvancedJadRenamer extends JADNameProvider {
        private StructMethod wrapper;
        private static final Pattern p = Pattern.compile("func_(\\d+)_.*");
        public AdvancedJadRenamer(StructMethod wrapper)
        {
            super(wrapper);
            this.wrapper = wrapper;
        }
        @Override
        public String renameAbstractParameter(String abstractParam, int index)
        {
            String result = abstractParam;
            if ((wrapper.getAccessFlags() & CodeConstants.ACC_ABSTRACT) != 0) {
                String methName = wrapper.getName();
                Matcher m = p.matcher(methName);
                if (m.matches()) {
                    result = String.format("p_%s_%d_", m.group(1), index);
                }
            }
            return result;

        }
    }
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

    class ArtifactSaver implements IResultSaver {
        private final Map<String, ZipOutputStream> mapArchiveStreams = new HashMap<String, ZipOutputStream>();
        private final Map<String, Set<String>> mapArchiveEntries = new HashMap<String, Set<String>>();
        private final File root;
        public ArtifactSaver(File tempDir) {
            this.root = tempDir;
        }

        private String getAbsolutePath(String path) {
            return new File(root, path).getAbsolutePath();
          }


        @Override
        public void saveFolder(String path) {
            File dir = new File(getAbsolutePath(path));
            if (!(dir.mkdirs() || dir.isDirectory())) {
                throw new RuntimeException("Cannot create directory " + dir);
            }
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
            try {
                InterpreterUtil.copyFile(new File(source), new File(getAbsolutePath(path), entryName));
            } catch (IOException ex) {
                DecompilerContext.getLogger().writeMessage("Cannot copy " + source + " to " + entryName, ex);
            }
        }

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
            File file = new File(getAbsolutePath(path), entryName);
            try {
                Writer out = new OutputStreamWriter(new FileOutputStream(file), "UTF8");
                try {
                    out.write(content);
                } finally {
                    out.close();
                }
            } catch (IOException ex) {
                DecompilerContext.getLogger().writeMessage("Cannot write class file " + file, ex);
            }
        }

        @Override
        public void createArchive(String path, String archiveName, Manifest manifest) {
            File file = new File(getAbsolutePath(path), archiveName);
            try {
                if (!(file.createNewFile() || file.isFile())) {
                    throw new IOException("Cannot create file " + file);
                }

                FileOutputStream fileStream = new FileOutputStream(file);
                ZipOutputStream zipStream = manifest != null ? new JarOutputStream(fileStream, manifest) : new ZipOutputStream(fileStream);
                mapArchiveStreams.put(file.getPath(), zipStream);
            } catch (IOException ex) {
                DecompilerContext.getLogger().writeMessage("Cannot create archive " + file, ex);
            }
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
            saveClassEntry(path, archiveName, null, entryName, null);
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entryName) {
            String file = new File(getAbsolutePath(path), archiveName).getPath();

            if (!checkEntry(entryName, file)) {
                return;
            }

            try {
                ZipFile srcArchive = new ZipFile(new File(source));
                try {
                    ZipEntry entry = srcArchive.getEntry(entryName);
                    if (entry != null) {
                        InputStream in = srcArchive.getInputStream(entry);
                        ZipOutputStream out = mapArchiveStreams.get(file);
                        out.putNextEntry(new ZipEntry(entryName));
                        InterpreterUtil.copyStream(in, out);
                        in.close();
                    }
                } finally {
                    srcArchive.close();
                }
            } catch (IOException ex) {
                String message = "Cannot copy entry " + entryName + " from " + source + " to " + file;
                DecompilerContext.getLogger().writeMessage(message, ex);
            }
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
            String file = new File(getAbsolutePath(path), archiveName).getPath();

            if (!checkEntry(entryName, file)) {
                return;
            }

            try {
                ZipOutputStream out = mapArchiveStreams.get(file);
                out.putNextEntry(new ZipEntry(entryName));
                if (content != null) {
                    out.write(content.getBytes("UTF-8"));
                }
            } catch (IOException ex) {
                String message = "Cannot write entry " + entryName + " to " + file;
                DecompilerContext.getLogger().writeMessage(message, ex);
            }
        }

        private boolean checkEntry(String entryName, String file) {
            Set<String> set = mapArchiveEntries.get(file);
            if (set == null) {
                mapArchiveEntries.put(file, set = new HashSet<String>());
            }

            boolean added = set.add(entryName);
            if (!added) {
                String message = "Zip entry " + entryName + " already exists in " + file;
                DecompilerContext.getLogger().writeMessage(message, IFernflowerLogger.Severity.WARN);
            }
            return added;
        }

        @Override
        public void closeArchive(String path, String archiveName) {
            String file = new File(getAbsolutePath(path), archiveName).getPath();
            try {
                mapArchiveEntries.remove(file);
                mapArchiveStreams.remove(file).close();
            } catch (IOException ex) {
                DecompilerContext.getLogger().writeMessage("Cannot close " + file, IFernflowerLogger.Severity.WARN);
            }
        }

    }

    public File getInJar() {
        return getProject().file(inJar);
    }

    public void setInJar(Object inJar) {
        this.inJar = inJar;
    }

    public File getOutJar() {
        return getProject().file(outJar);
    }

    public void setOutJar(Object outJar) {
        this.outJar = outJar;
    }

    public FileCollection getClasspath()
    {
        return classpath;
    }

    public void setClasspath(FileCollection classpath)
    {
        this.classpath = classpath;
    }


}
