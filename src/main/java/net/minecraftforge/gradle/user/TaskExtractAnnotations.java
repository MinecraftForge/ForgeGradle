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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.minecraftforge.gradle.common.Constants;

public class TaskExtractAnnotations extends DefaultTask
{
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Object jar;

    public TaskExtractAnnotations()
    {
        this.getOutputs().upToDateWhen(Constants.CALL_FALSE);
    }

    @TaskAction
    public void doTask() /*throws IOException*/
    {
        try { //Temporary for now, so we dont break people's builds... at least... we shouldn't.
        File out = getJar();
        File tempIn = File.createTempFile("input", ".jar", getTemporaryDir());
        File tempOut = File.createTempFile("output", ".jar", getTemporaryDir());
        tempIn.deleteOnExit();
        tempOut.deleteOnExit();

        Constants.copyFile(out, tempIn); // copy the to-be-output jar to the temporary input location. because output == input

        processJar(tempIn, tempOut);

        Constants.copyFile(tempOut, out);// This is the only 'destructive' line, IF we do error on here. then something is screwy... If we error above then it'd be just like this never run.
        tempOut.delete();
        } catch (IOException e) {
            this.getProject().getLogger().error("Error while building FML annotations cache: " + e.getMessage(), e);
        }
    }

    private void processJar(File input, File output) throws IOException
    {
        Map<String, ASMInfo> asm_info = Maps.newTreeMap(); //Tree map because I like sorted outputs.
        Map<String, Integer> class_versions = Maps.newTreeMap();

        try (ZipFile in = new ZipFile(input);
             ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output))))
        {
            for (ZipEntry e : Collections.list(in.entries()))
            {
                if (e.isDirectory())
                {
                    out.putNextEntry(e);
                    continue;
                }

                ZipEntry n = new ZipEntry(e.getName());
                n.setTime(e.getTime());
                out.putNextEntry(n);

                byte[] data = ByteStreams.toByteArray(in.getInputStream(e));
                out.write(data);

                // correct source name
                if (e.getName().endsWith(".class"))
                {
                    ASMInfo info = processClass(data);
                    if (info != null)
                    {
                        String name = e.getName().substring(0, e.getName().length() - 6);
                        class_versions.put(name, info.version);
                        info.version = null;
                        if (info.annotations != null)
                        {
                            for (Annotation anno : info.annotations)
                            {
                                if (anno.values != null && anno.values.size() == 1 && anno.values.containsKey("value"))
                                {
                                    anno.value = anno.values.get("value");
                                    anno.values = null;
                                }
                            }
                        }
                        if (info.annotations != null || info.interfaces != null)
                            asm_info.put(name, info);
                    }
                }
            }

            if (!asm_info.isEmpty())
            {
                String data = GSON.toJson(asm_info);
                ZipEntry cache = new ZipEntry("META-INF/fml_cache_annotation.json");
                cache.setTime(new Date().getTime());
                out.putNextEntry(cache);
                out.write(data.getBytes(Charsets.UTF_8));

                data = GSON.toJson(class_versions);
                cache = new ZipEntry("META-INF/fml_cache_class_versions.json");
                cache.setTime(new Date().getTime());
                out.putNextEntry(cache);
                out.write(data.getBytes(Charsets.UTF_8));
            }
        }
    }

    private ASMInfo processClass(byte[] data)
    {
        final ASMInfo info = new ASMInfo();
        ClassReader reader = new ClassReader(data);
        reader.accept(new ClassVisitor(Opcodes.ASM6)
        {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces)
            {
                info.name = name;
                info.version = version;
                //info.super_type = Strings.isNullOrEmpty(superName) ? null : superName;
                info.interfaces = interfaces == null || interfaces.length == 0 ? null : interfaces;
            }

            @Override
            public AnnotationVisitor visitAnnotation(String annotationName, boolean runtimeVisible)
            {
                return new ModAnnotationVisitor(info, new Annotation(TargetType.CLASS, annotationName, info.name));
            }

            @Override
            public FieldVisitor visitField(int access, final String name, String desc, String signature, Object value)
            {
                return new FieldVisitor(Opcodes.ASM6)
                {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationName, boolean runtimeVisible)
                    {
                        return new ModAnnotationVisitor(info, new Annotation(TargetType.FIELD, annotationName, name));
                    }
                };
            }

            @Override
            public MethodVisitor visitMethod(int access, final String name, final String desc, String signature, String[] exceptions)
            {
                return new MethodVisitor(Opcodes.ASM6)
                {
                    @Override
                    public AnnotationVisitor visitAnnotation(String annotationName, boolean runtimeVisible)
                    {
                        return new ModAnnotationVisitor(info, new Annotation(TargetType.METHOD, annotationName, name + desc));
                    }
                };
            }
        }, 0);

        return info;
    }

    public File getJar()
    {
        return getProject().file(jar);
    }

    public void setJar(Object jar)
    {
        this.jar = jar;
    }


    public static class ASMInfo
    {
        public String name;
        public Integer version = -1; //Used this to pass up, will be nulled out so it doesn't actually make it to the json
        //public String super_type; // Was used for looking for ModLoader mods, but not used anymore.
        public String[] interfaces;
        public List<Annotation> annotations;

        public void add(Annotation anno)
        {
            if (annotations == null)
                this.annotations = Lists.newArrayList();
            this.annotations.add(anno);
        }
    }

    public enum TargetType { CLASS, FIELD, METHOD, SUBTYPE };

    public static class ValueHolder
    {
        public enum ValueType { BOOL, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE, STRING, CLASS, ENUM, ANNOTATION, NULL};
        private static final Map<Class<?>, ValueType> byClass = ImmutableMap.<Class<?>, ValueType>builder()
            .put(Boolean.class,   ValueType.BOOL  )
            .put(boolean[].class, ValueType.BOOL  )
            .put(Byte.class,      ValueType.BYTE  )
            .put(byte[].class,    ValueType.BYTE  )
            .put(Character.class, ValueType.CHAR  )
            .put(char[].class,    ValueType.CHAR  )
            .put(Short.class,     ValueType.SHORT )
            .put(short[].class,   ValueType.SHORT )
            .put(Integer.class,   ValueType.INT   )
            .put(int[].class,     ValueType.INT   )
            .put(Long.class,      ValueType.LONG  )
            .put(long[].class,    ValueType.LONG  )
            .put(Float.class,     ValueType.FLOAT )
            .put(float[].class,   ValueType.FLOAT )
            .put(Double.class,    ValueType.DOUBLE)
            .put(double[].class,  ValueType.DOUBLE)
            //.put(String.class,    ValueType.STRING)
            //.put(String[].class,  ValueType.STRING)
            .put(Type.class,      ValueType.CLASS)
            .put(Type[].class,    ValueType.CLASS)
            // We deal with enums and annotations elseware
            .build();

        private static final ValueHolder NULL = new ValueHolder();
        private static final ValueHolder EMPTY_LIST = new ValueHolder(null, null, Lists.newArrayList());

        public final ValueType type;
        public final String value;
        public final List<String> values;

        private ValueHolder(Object value)
        {
            Class<?> cls = value.getClass();
            if (cls.isArray())
            {
                this.value = null;
                this.values = Lists.newArrayList();
                for (int x = 0; x < Array.getLength(value); x++)
                    this.values.add(Array.get(value, x).toString());
            }
            else
            {
                this.value = value.toString();
                this.values = null;
            }
            this.type = ValueHolder.byClass.get(value.getClass());
        }

        private ValueHolder(String type, String value)
        {
            this(ValueType.ENUM, type + "/" + value, null);
        }

        private ValueHolder()
        {
            this(ValueType.NULL, null, null);
        }

        private ValueHolder(ValueType type, String value, List<String> values)
        {
            this.type = type;
            this.value = value;
            this.values = values;
        }

        public static ValueHolder of(Object value)
        {
            if (value == null)
                return NULL;

            if (value instanceof Annotation)
                return new ValueHolder(ValueType.ANNOTATION, ((Annotation)value).id.toString(), null);

            return new ValueHolder(value);
        }

        public static ValueHolder of(String type, String value)
        {
            return new ValueHolder(type, value);
        }

        public static ValueHolder of(List<ValueHolder> values)
        {
            if (values.isEmpty())
                return EMPTY_LIST;

            return new ValueHolder(values.get(0).type, null, values.stream().map(a -> a.value).collect(Collectors.toList()));
        }
    }

    private static class Annotation
    {
        private static int SUB_COUNT = 1;
        public final TargetType type;
        public final String name;
        public final String target;
        public final Integer id;
        public ValueHolder value;
        public Map<String, ValueHolder> values;

        public Annotation(TargetType type, String name, String target)
        {
            this.type = type;
            this.name = name;
            this.target = target;
            this.id = this.type == TargetType.SUBTYPE ? SUB_COUNT++ : null;
        }

        // Possible Types: boolean, byte, char, short, int, long, float, double, String, Class, Enum, and annotation.
        // It's also possible to be an array of any of those types.
        // Writing to JSON is lossy... so.. we need to write a custom parser?
        public void addProperty(String name, ValueHolder value)
        {
            if (values == null)
                values = Maps.newTreeMap();
            values.put(name, value);
        }
    }

    private static class ModAnnotationVisitor extends AnnotationVisitor
    {
        protected final Annotation anno;
        private final ASMInfo info;

        public ModAnnotationVisitor(ASMInfo info, Annotation annon)
        {
            super(Opcodes.ASM6);
            this.info = info;
            this.anno = annon;
        }

        @Override
        public void visit(String name, Object value)
        {
            anno.addProperty(name, ValueHolder.of(value));
        }

        @Override
        public void visitEnum(String name, String desc, String value)
        {
            anno.addProperty(name, ValueHolder.of(desc, value));
        }

        @Override
        public AnnotationVisitor visitArray(String name)
        {
            final List<ValueHolder> array = Lists.newArrayList();
            Annotation holder = new Annotation(null, null, null)
            {
                @Override
                public void addProperty(String name, ValueHolder value)
                {
                    array.add(value);
                }
            };

            return new ModAnnotationVisitor(info, holder)
            {
                @Override
                public void visitEnd()
                {
                    ModAnnotationVisitor.this.anno.addProperty(name, ValueHolder.of(array));
                }
            };
        }

        @Override
        public AnnotationVisitor visitAnnotation(String name, String desc)
        {
            Annotation child = new Annotation(TargetType.SUBTYPE, desc, null);
            anno.addProperty(name, ValueHolder.of(child));
            return new ModAnnotationVisitor(info, child);
        }

        @Override
        public void visitEnd()
        {
            if (!filterAnnotation(this.anno))
                info.add(this.anno);
        }

        private boolean filterAnnotation(Annotation anno)
        {
            //TODO: Actually load the annotation and filter anything with a special annotation?
            //For now we just filter 'java.*' annotations. And a couple of Forge ones.
            if (
                 anno.name.startsWith("Ljava/lang/") ||
                 anno.name.startsWith("Ljavax/annotation/") ||
                 anno.name.contains("/fml/relauncher/SideOnly;")
               )
                return true;
            return false;
        }
    }
}
