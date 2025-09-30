/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package local.build;

import net.minecraftforge.gradleutils.shared.Closures;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.gradle.api.Project;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Dedicated transformer for Eclipse JDT to redirect a method call to Srg2Source's {@code RangeExtractor}.
 *
 * @see #register(Project)
 * @see #transform(HasConfigurableAttributes)
 */
public abstract class JDTClassTransformer implements TransformAction<JDTClassTransformer.Parameters> {
    private static final Logger LOGGER = Logging.getLogger(JDTClassTransformer.class);

    private static final Attribute<Boolean> ATTRIBUTE = Attribute.of("net.minecraftforge.gradle.build.jdt", Boolean.class);

    public static final String COMPILATION_UNIT_RESOLVER = "org/eclipse/jdt/core/dom/CompilationUnitResolver";
    public static final String RANGE_EXTRACTOR = "net/minecraftforge/srg2source/ast/RangeExtractor";
    private static final String RESOLVE_METHOD = "resolve([Ljava/lang/String;[Ljava/lang/String;[Ljava/lang/String;Lorg/eclipse/jdt/core/dom/FileASTRequestor;ILjava/util/Map;I)V";
    private static final String GET_CONTENTS = "org/eclipse/jdt/internal/compiler/util/Util.getFileCharContent(Ljava/io/File;Ljava/lang/String;)[C";
    private static final String HOOK_DESC_RESOLVE = "(Ljava/lang/String;Ljava/lang/String;)[C";

    public interface Parameters extends TransformParameters {
        @Input SetProperty<String> getTargets();
    }

    /**
     * Registers the JDT class transformer with the given project.
     *
     * @param project
     */
    public static void register(Project project) {
        var dependencies = project.getDependencies();
        dependencies.getAttributesSchema().attribute(ATTRIBUTE);
        dependencies.getArtifactTypes().named(ArtifactTypeDefinition.JAR_TYPE, jar -> jar.getAttributes().attribute(ATTRIBUTE, false));

        dependencies.registerTransform(JDTClassTransformer.class, spec -> {
            spec.parameters(p -> p.getTargets().set(Set.of(JDTClassTransformer.COMPILATION_UNIT_RESOLVER, JDTClassTransformer.RANGE_EXTRACTOR)));

            spec.getFrom()
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY))
                .attribute(ATTRIBUTE, false);
            spec.getTo()
                .attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                .attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.LIBRARY))
                .attribute(ATTRIBUTE, true);
        });
    }

    public static void transform(HasConfigurableAttributes<?> dependency) {
        dependency.attributes(a -> a.attribute(ATTRIBUTE, true));
    }

    protected abstract @InputArtifact Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        var lib = this.getInputArtifact().get().getAsFile();
        var targets = getParameters().getTargets().get();
        var output = outputs.file(this.getInputArtifact().get().getAsFile().getName().replace(".jar", "-jdt-patched.jar"));

        var toProcess = new HashSet<>(targets);
        var toRemove = new ArrayList<String>();
        try (var zout = new ZipOutputStream(new FileOutputStream(output))) {
            try (var zin = new ZipFile(lib)) {
                for (var entry : Collections.list(zin.entries())) {
                    String target = null;
                    for (var t : toProcess) {
                        if (entry.getName().equals(t + ".class")) {
                            target = t;
                            break;
                        }
                    }

                    if (target == null) {
                        var nentry = new ZipEntry(entry.getName());
                        nentry.setTime(0L);
                        zout.putNextEntry(nentry);
                        zout.write(zin.getInputStream(entry).readAllBytes());
                        zout.closeEntry();
                        continue;
                    }

                    var node = new ClassNode();
                    var reader = new ClassReader(zin.getInputStream(entry));
                    reader.accept(node, 0);

                    //CompilationUnitResolver allows batch compiling, the problem is it is hardcoded to read the contents from a File.
                    //So we patch this call to redirect to us, so we can get the contents from our InputSupplier
                    if (COMPILATION_UNIT_RESOLVER.equals(target)) {
                        LOGGER.info("Transforming: {} From: {}", target, lib);
                        var resolve = DefaultGroovyMethods.find(node.methods, Closures.<MethodNode, Boolean>function(
                            m -> RESOLVE_METHOD.equals(m.name + m.desc)
                        ));
                        if (resolve == null)
                            throw new RuntimeException("Failed to patch %s: Could not find method %s".formatted(target, RESOLVE_METHOD));
                        for (int x = 0; x < resolve.instructions.size(); x++) {
                            var it = resolve.instructions.get(x);
                            if (!(it instanceof MethodInsnNode insn)) continue;
                            if (!GET_CONTENTS.equals(insn.owner + "." + insn.name + insn.desc)) continue;

                            if (
                                resolve.instructions.get(x - 5).getOpcode() == Opcodes.NEW &&
                                    resolve.instructions.get(x - 4).getOpcode() == Opcodes.DUP &&
                                    resolve.instructions.get(x - 3).getOpcode() == Opcodes.ALOAD &&
                                    resolve.instructions.get(x - 2).getOpcode() == Opcodes.INVOKESPECIAL &&
                                    resolve.instructions.get(x - 1).getOpcode() == Opcodes.ALOAD
                            ) {
                                resolve.instructions.set(resolve.instructions.get(x - 5), new InsnNode(Opcodes.NOP)); // NEW File
                                resolve.instructions.set(resolve.instructions.get(x - 4), new InsnNode(Opcodes.NOP)); // DUP
                                resolve.instructions.set(resolve.instructions.get(x - 2), new InsnNode(Opcodes.NOP)); // INVOKESTATIC <init>
                                insn.owner = RANGE_EXTRACTOR;
                                insn.desc = HOOK_DESC_RESOLVE;
                                LOGGER.info("Patched {}", node.name);
                            } else {
                                throw new IllegalStateException("Found Util#getFileCharContents call with unexpected context");
                            }
                        }
                    } else if (RANGE_EXTRACTOR.equals(target)) {
                        LOGGER.info("Transforming: {} From: {}", target, lib);
                        var marker = DefaultGroovyMethods.find(node.methods, Closures.<MethodNode, Boolean>function(
                            m -> "hasBeenASMPatched()Z".equals(m.name + m.desc)
                        ));
                        if (marker == null)
                            throw new RuntimeException("Failed to patch " + target + ": Could not find method hasBeenASMPatched()Z");

                        marker.instructions.clear();
                        marker.instructions.add(new InsnNode(Opcodes.ICONST_1));
                        marker.instructions.add(new InsnNode(Opcodes.IRETURN));
                        LOGGER.info("Patched: {}", node.name);
                    }

                    var writer = new ClassWriter(0);
                    node.accept(writer);

                    toRemove.add(target);
                    var nentry = new ZipEntry(entry.getName());
                    nentry.setTime(0L);
                    zout.putNextEntry(nentry);
                    zout.write(writer.toByteArray());
                    zout.closeEntry();
                }

                toRemove.forEach(toProcess::remove);
            }

            if (toProcess.size() < targets.size()) {
                LOGGER.lifecycle("JDTClassTransformer successfully patched library: {}", lib.getName());
            }
        } catch (IOException e) {
            throw new RuntimeException("JDTClassTransformer encountered an unrecoverable error trying to patch " + lib.getName(), e);
        }
    }
}
