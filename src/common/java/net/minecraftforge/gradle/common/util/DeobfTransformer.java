package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.HasConfigurableAttributes;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public class DeobfTransformer {
    static Attribute<String> FG_DEOBF = Attribute.of("fg_deobf_stage",   String.class);

    public static void apply(MinecraftExtension extension) {
        final Project project = extension.getProject();
        @SuppressWarnings("unused")
        final Logger log = project.getLogger();

        project.getDependencies().getAttributesSchema().attribute(FG_DEOBF);
        project.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(FG_DEOBF, "raw");

        Set<String> whitelist = new HashSet<>();
        Action<? super Configuration> enhance = cfg -> {
            String name = cfg.getName();
            if (!name.startsWith("deobf")) {
                String newName = "deobf" + name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1);
                if ("implementation".equals(name))
                    newName = "deobf";

                Configuration deobf = project.getConfigurations().maybeCreate(newName);
                cfg.extendsFrom(deobf);
                deobf.getDependencies().whenObjectAdded(dep -> {
                    if (dep instanceof HasConfigurableAttributes)
                        ((HasConfigurableAttributes<?>)dep).attributes(atr -> atr.attribute(FG_DEOBF, "bin"));
                    else
                        throw new IllegalArgumentException("Could not add attribute to deobf dependency, this most likely means you tried to use a direct file dependecy, this is not supported right now:\n" + dep.toString());
                    whitelist.add(dep.getGroup() + ':' + dep.getName() + ':' + dep.getVersion());
                });
            }
        };
        project.getConfigurations().stream().collect(Collectors.toList()).forEach(cfg -> enhance.execute(cfg)); // We copy to a intermediate to prevent CMEs
        project.getConfigurations().whenObjectAdded(enhance);

        project.afterEvaluate(prj -> {
            project.getDependencies().registerTransform(Transformer.class, it -> {
                it.getFrom().attribute(FG_DEOBF, "raw");
                it.getTo().attribute(FG_DEOBF, "bin");
                it.parameters(p -> {
                    p.setChannel(extension.getMappingChannel());
                    p.setVersion(extension.getMappingVersion());
                });
            });

            project.getDependencies().registerTransform(Transformer.class, it -> {
                it.getFrom().attribute(FG_DEOBF, "raw");
                it.getTo().attribute(FG_DEOBF, "sources");
                it.parameters(p -> {
                    p.setChannel(extension.getMappingChannel());
                    p.setVersion(extension.getMappingVersion() + " source");
                });
            });
        });

        DeobfTransformerHacks.apply(project, whitelist);
    }

    @CacheableTransform
    public abstract static class Transformer implements TransformAction<Transformer.Parameters> {
        public interface Parameters extends TransformParameters {
            @Input
            String getChannel();
            void setChannel(String value);

            @Input
            String getVersion();
            void setVersion(String value);
        }

        @PathSensitive(PathSensitivity.NONE)
        @InputArtifact
        protected abstract Provider<FileSystemLocation> getInputArtifact();

        @Override
        public void transform(TransformOutputs outputs) {
            Parameters params = getParameters();
            File input = getInputArtifact().get().getAsFile();
            System.out.println("Transforming: " + input.getAbsolutePath());
            System.out.println("  Channel: " + params.getChannel());
            System.out.println("  Version: " + params.getVersion());

            String baseName = input.getName().substring(0, input.getName().length() - 4);
            File output = outputs.file(baseName + ".deobf.jar"); //TODO: Process
            try {
                Files.copy(input.toPath(), output.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
